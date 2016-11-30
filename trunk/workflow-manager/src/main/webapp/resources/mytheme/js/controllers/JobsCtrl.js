/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

'use strict';

/**
 * JobsCtrl
 * @constructor
 */
var JobsCtrl = function ($scope, $log, ServerSidePush, JobsService, NotificationSvc) {
    $scope.isSession = true;
    //the latest default job priority is retrieved in init
    $scope.newJobPriority = 4;
    var jobTable = null;
    var resizeTimer;

    var init = function () {
        $(window).on('resize', function (e) {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function () {
                resizeViews();
            }, 750);
        });
        resizeViews();
        buildJobTable();
    };

    var resizeViews = function () {
        var maxheight = $("body").height() - $("#navbarMain").height() - $("#header").height() ;
        $("#main").height(maxheight);
        buildJobTable();
    }

    var buildJobTable = function () {
        if (jobTable) {
            jobTable.clear();
            jobTable.draw();
        } else {
            jobTable = $('#jobTable').DataTable({
                destroy: true,
                data: [],
                stateSave: false,
                serverSide: true,
                processing: false,//hide
                ajax: {
                    url: "jobs-paged",
                    type: "POST",
                    data: function (d) {//extra params
                        d.useSession = $scope.isSession;
                        d.search = d.search.value;//pull out because spring is a pain to pass params
                    }
                },
                drawCallback: function (settings) {
                    bindButtons();
                },
                scrollY: '45vh',
                scrollCollapse: false,
                lengthMenu: [[5, 10, 25, 50, 100], [5, 10, 25, 50, 100]],
                pageLength: 25,
                ordering: false,
                columns: [
                    {data: "jobId", width: "3%"},
                    {data: "pipelineName", width: "37%"},
                    {
                        data: "startDate", width: "10%",
                        render: function (data, type, obj) {
                            return (moment(data).format("MM/DD/YYYY hh:mm A"));
                        }
                    },
                    {
                        data: "endDate", width: "10%",
                        render: function (data, type, job) {
                            //keep the endDate from displaying when not COMPLETE after a job re-submission
                            if (data != null && job.jobStatus.startsWith('COMPLETE'))
                                return (moment(data).format("MM/DD/YYYY hh:mm A"));
                            return "";
                        }
                    },
                    {
                        data: "jobStatus", width: "10%",
                        render: function (data, type, job) {
                            return '<div class="jobStatusCell" id="jobStatusCell' + job.jobId + '">' + job.jobStatus + '</div>';
                        }
                    },
                    {
                        data: "jobPriority", width: "5%",
                        render: function (data, type, job) {
                            return '<div class="jobPriorityCell" id="jobPriorityCell' + job.jobId + '">' + job.jobPriority + '</div>';
                        }
                    },
                    {
                        data: "jobProgress", width: "5%",
                        render: function (data, type, job) {
                            var progressVal = job.jobProgress.toFixed(2);
                            //keep the job progress val at 99% until it is complete or cancelled
                            if (progressVal > 99 && !(job.jobStatus.startsWith('COMPLETE') || job.jobStatus.startsWith('CANCELLED'))) {
                                progressVal = 99;
                            } else if (job.jobStatus.startsWith('COMPLETE') || job.jobStatus.startsWith('CANCELLED')) {
                                progressVal = 100;
                            }
                            return '<div class="jobProgressPctCell" id="jobProgress' + job.jobId + '">' + progressVal + '%</div>';
                        }
                    },
                    {
                        "data": "null", "defaultContent": '', width: "10%", orderable: false,
                        render: function (data, type, job) {
                            return '<button class="btn btn-xs btn-info outputObjectBtn" id="outputObjectBtn' + job.jobId + '" >Output Object</button>';
                        }
                    },
                    {
                        "data": "null", "defaultContent": '', orderable: false, width: "10%",
                        render: function (data, type, job) {
                            var isdisabled = "";
                            if (job.terminal || job.jobStatus == 'CANCELLING') isdisabled = "disabled=true";
                            var isterminal = "";
                            if (!job.terminal) isterminal = "disabled=true";
                            return '<div class="btn-group"><button class="btnDisplayCancelJobModal btn btn-xs btn-danger cancelBtn" id="cancelBtn' + job.jobId + '" ' + isdisabled + '>Cancel</button><button class="btnDisplayResubmitJobModal btn btn-xs btn-success resubmitBtn" id="resubmitBtn' + job.jobId + '"' + isterminal + ' >Resubmit</button></div>'
                        }
                    }
                ],
                dom: '<"top"Blf>rt<"bottom"<"dt_foot1"i><"dt_foot2"p>><"clear">',//'lftip',//'lfrtip',//https://datatables.net/reference/option/dom

                buttons: [
                    {
                        text: 'Session',
                        className:'btn btn-session btn-success active',
                        action: function (e, dt, node, config) {
                            $scope.isSession = true;
                            $scope.fetchJobsList(true);
                        }
                    }, {
                        text: 'All',
                        className:'btn btn-all btn-default',
                        action: function (e, dt, node, config) {
                            $scope.isSession = false;
                            $scope.fetchJobsList(true);

                        }
                    }],
                initComplete: function (settings, json) {
                    $log.debug('DataTables has finished its initialization.');
                }
            });
        }
        //button highlights
        if(!$scope.isSession) {
            $(".btn-session").removeClass("active").removeClass("btn-success").addClass("btn-default");
            $(".btn-all").addClass("active").addClass("btn-success").removeClass("btn-default");
        }else {
            $(".btn-all").removeClass("active").removeClass("btn-success").addClass("btn-default");
            $(".btn-session").addClass("active").addClass("btn-success").removeClass("btn-default");
        }
    };

    var bindButtons = function () {
        //  $(".dataTables_scrollBody thead tr").addClass('hidden');//bug
        $(".outputObjectBtn").click(function () {
            var jobid = $(this).attr("id").replace("outputObjectBtn", "");
            $scope.fetchJob(jobid, false, true, '#outputObjectsModal');
        });
        $(".cancelBtn").click(function () {
            //var jobid = $(this).attr("id").replace("cancelBtn","");
            var idx = jobTable.row($(this).closest('tr')[0]).index();
            var job = jobTable.rows(idx).data()[0];
            $scope.currentJob = job;
            $("#cancelJobModal").modal('toggle');
        });
        $(".resubmitBtn").click(function () {
            //var jobid = $(this).attr("id").replace("resubmitBtn","");
            var idx = jobTable.row($(this).closest('tr')[0]).index();
            var job = jobTable.rows(idx).data()[0];
            $scope.currentJob = job;
            $("#resubmitJobModal").modal('toggle');
        });
    }
    //if modalTargetSelector is not null it should be the selection string for a modal that should be displayed
    //the reason for passing it is to solve the async issue and display the modal after the update has been done
    $scope.fetchJob = function (id, update, setAsCurrentJob, modalTargetSelector) {
        JobsService.getJob(id).then(function (job) {
            //update the map
            if (setAsCurrentJob) {
                $scope.currentJob = job;
            }
            //force refresh to reload the main table
            if (update) {
                buildJobTable();
            }

            if (modalTargetSelector) {
                $(modalTargetSelector).modal('toggle');
            }
            if (!$scope.$$phase) $scope.$apply();
        });
    };

    $scope.fetchJobsList = function (update) {
        buildJobTable();
    }

    //todo need to just update the current
    $scope.$on('SSPC_JOBSTATUS', function (event, msg) {
//		console.log( "SSPC_JOBSTATUS: " + JSON.stringify(msg) );
        var json = msg.content;

        //send -1 and -1 on connect
        if (json.id != -1 && json.progress != -1) {
            var jobId = json.id;
            var newJobProgress = json.progress.toFixed(2);
            var newJobStatus = json.jobStatus;

            //sticking with jquery here until mastering ng-animate
            //stopping the effect first, because these can really stack up with push progress updates
            $("#jobProgress" + jobId).stop(true, true);//stop highhlighting
            $("#jobProgress" + jobId).effect("highlight",
                {color: '#33cc00'}, 700, function () {
                    $("#jobProgress" + jobId).stop(true, true);
                });
            $("#jobProgress" + jobId).html(newJobProgress + "%");

            if (newJobStatus == 'CANCELLED') {
                console.log('job cancellation complete for id: ' + jobId);
                NotificationSvc.info('Job cancellation of job ' + jobId + ' is now complete.');
            }

            buildJobTable();
        }
    });

    $scope.resubmitJob = function () {
        var currentId = $scope.currentJob.jobId;
        $("#jobStatusCell"+currentId).html("RESUBMITTING");
        JobsService.resubmitJob($scope.currentJob.jobId, $scope.newJobPriority.selected.priority).then(function (jobCreationResponse) {
            if (jobCreationResponse && jobCreationResponse.hasOwnProperty("mpfResponse") &&
                jobCreationResponse.hasOwnProperty("jobId")) {
                if (jobCreationResponse.mpfResponse.responseCode != 0) {
                    NotificationSvc.error(
                        'Error with resubmit request with message: ' + jobCreationResponse.mpfResponse.message);
                } else {
                    NotificationSvc.success('Job ' + $scope.currentJob.jobId + ' has been resubmitted!');
                    buildJobTable();
                }
            } else {
                NotificationSvc.error('Failed to send a resubmit request');
            }
        });
        //temporary jquery solution to dismiss modal
        $('#resubmitJobModal').modal('toggle');
    };

    $scope.cancelJob = function () {
        var currentId = $scope.currentJob.jobId;
        $("#jobStatusCell"+currentId).html("CANCELLING");
        JobsService.cancelJob(currentId).then(function (mpfResponse) {
            if (mpfResponse && mpfResponse.hasOwnProperty("responseCode") &&
                mpfResponse.hasOwnProperty("message")) {
                if (mpfResponse.responseCode != 0) {
                    NotificationSvc.error('Error with cancellation request with message: ' + mpfResponse.message);
                } else {
                    NotificationSvc.info('A job cancellation request for job ' + currentId + ' has been sent.');
                    buildJobTable();
                }
            } else {
                NotificationSvc.error('Failed to send a cancellation request');
            }
        });
        //temporary jquery solution to dismiss modal
        $('#cancelJobModal').modal('toggle');
    };

    init();
};
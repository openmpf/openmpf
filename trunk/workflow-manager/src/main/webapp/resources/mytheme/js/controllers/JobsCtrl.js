/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
var JobsCtrl = function ($scope, $log, $compile, ServerSidePush, JobsService, NotificationSvc) {

    $scope.selectedJob = {};
    var jobTable = null;
    var markupTable = null;
    var markupTableData = [];

    var init = function () {
        buildJobTable();
    };

    var buildJobTable = function () {
        if (jobTable != null) {
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
                        d.search = d.search.value;
                    }
                },
                language: {
                    emptyTable: 'No jobs available'
                },
                drawCallback: function (settings) {
                    bindButtons();
                },
                scrollCollapse: true,
                scrollY:'65vh',
                lengthMenu: [[5, 10, 25, 50, 100], [5, 10, 25, 50, 100]],
                pageLength: 25,
                ordering: false,
                searchHighlight: true,
                renderer: "bootstrap",
                columns: [
                    {
                        data: "jobId"
                    },
                    {
                        data: "pipelineName"
                    },
                    {
                        data: "startDate",
                        render: function (data, type, job) {
                            return (moment(job.startDate).format("MM/DD/YYYY hh:mm A"));
                        }
                    },
                    {
                        data: "endDate",
                        render: function (data, type, job) {
                            if (job.endDate && job.jobStatus.startsWith('COMPLETE'))
                                return (moment(job.endDate).format("MM/DD/YYYY hh:mm A"));
                            return "";
                        }
                    },
                    {
                        data: "jobStatus",
                        render: function (data, atype, job) {
                            var type = "label-default";
                            if (job.jobStatus.toLowerCase().indexOf("error") > 0) {
                                type = "label-danger";
                            } else if (job.jobStatus.toLowerCase().indexOf("warnings") > 0) {
                                type = "label-warning";
                            } else if (job.jobStatus.toLowerCase().indexOf("unknown") > 0) {
                                type = "label-primary";
                            }
                            var hideProgress = 'style="display:none;"';
                            if (job.jobStatus.startsWith('IN_PROGRESS') && job.jobProgress < 100) hideProgress = "";
                            var progress = job.jobProgress.toFixed();
                            var progressDiv = '<div class="progress" ' + hideProgress + '><div class="progress-bar progress-bar-success" role="progressbar"  id="jobProgress' + job.jobId + '" aria-valuenow="0" aria-valuemin="' + progress + '" aria-valuemax="100" style="width:' + progress + '%">' + progress + '%</div></div>';
                            return '<span class="job-status label ' + type + '" id="jobStatusCell' + job.jobId + '">' + job.jobStatus + '</span>' + progressDiv;
                        }
                    },
                    {
                        data: "jobPriority",
                        render: function (data, type, job) {
                            return '<div class="jobPriorityCell" id="jobPriorityCell' + job.jobId + '">' + job.jobPriority + '</div>';
                        }
                    },
                    {//actions
                        data: "null", "defaultContent": '', orderable: false,
                        render: function (data, type, job) {
                            var cancel_disabled = "";
                            if (job.terminal || job.jobStatus == 'CANCELLING' || job.jobStatus == 'COMPLETE') cancel_disabled = "disabled=disabled";
                            var isterminal = "";
                            if (!job.terminal) isterminal = "disabled=disabled";
                            var hasOutput = "disabled=disabled";
                            var output_link = "";
                            if (job.outputFileExists) {
                                hasOutput = "";
                            }
                            return '<div class="btn-group btn-group-sm" role="group" >' +
                                '<button type="button" class="btn btn-default cancelBtn" id="cancelBtn' + job.jobId + '"' + cancel_disabled + ' title="Stop"><i class="fa fa-stop"></i></button>' +
                                '<button type="button" class="btn btn-default resubmitBtn"  id="resubmitBtn' + job.jobId + '"' + isterminal + ' title="Resubmit"><i class="fa fa-refresh"></i></button>' +
                                '<button type="button" class="btn btn-default markupBtn" id="markupBtn' + job.jobId + '" title="Media" ><i class="fa fa-picture-o" title="Media"></i></button>' +
                                '<a type="button" href="jobs/output-object?id=' + job.jobId + '" class="btn btn-default jsonBtn" id="jsonBtn' + job.jobId + '" target="_blank"  ' + hasOutput + ' title="JSON Output">{ }</a></div>';
                        }
                    }
                ],
                initComplete: function (settings, json) {
                    $log.debug('jobsTables has finished its initialization.');
                }
            });
        }
    };

    var getJobFromTableEle = function (ele) {
        var idx = jobTable.row($(ele).closest('tr')[0]).index();
        return jobTable.rows(idx).data()[0];
    };

    var bindButtons = function () {
        $(".markupBtn").click(function () {
            showMarkup(getJobFromTableEle(this));
        });
        $(".cancelBtn").click(function () {
            cancelJob(getJobFromTableEle(this));
        });
        $(".resubmitBtn").click(function () {
            resubmitJob(getJobFromTableEle(this));
        });
        $("#infoModalBtn").click(function () {
            $("#infoModal").modal('show');
        });
    };

    //listen for updates from the server
    $scope.$on('SSPC_JOBSTATUS', function (event, msg) {
        $log.debug("SSPC_JOBSTATUS: " + JSON.stringify(msg));
        var job = msg.content;

        //send -1 and -1 on connect
        if (job.id != -1 && job.progress != -1) {
            if (!$("#jobStatusCell" + job.id).length) {//missing the new job
                jobTable.ajax.reload(null, false);
            }
            var progress = job.progress.toFixed();
            $("#jobStatusCell" + job.id).html(job.jobStatus);

            //keep the job progress val at 99% until it is complete or cancelled
            if (progress > 99 && !(job.jobStatus.startsWith('COMPLETE') || job.jobStatus.startsWith('CANCELLED'))) {
                progress = 99;
            } else if (job.jobStatus.startsWith('COMPLETE') || job.jobStatus.startsWith('CANCELLED')) {
                jobTable.ajax.reload(null, false);
            }
            if (progress < 100) {
                $("#jobProgress" + job.id).parent().show();
                $("#jobProgress" + job.id).html(progress + "%");
                $("#jobProgress" + job.id).css("width", progress + "%");
                //disable buttons if necessary
                if (job.jobStatus.startsWith('CANCELLING')) {
                    $("#cancelBtn" + job.id).attr("disabled", "disabled");
                } else {
                    $("#cancelBtn" + job.id).removeAttr("disabled");
                }
                $("#resubmitBtn" + job.id).attr("disabled", "disabled");
                $("#markupBtn" + job.id).attr("disabled", "disabled");
                $("#jsonBtn" + job.id).attr("disabled", "disabled");
            }

            if (job.jobStatus == 'CANCELLED') {
                console.log('job cancellation complete for id: ' + job.id);
                NotificationSvc.info('Job cancellation of job ' + job.id + ' is now complete.');
            }
        }
    });

    var resubmitJob = function (job) {
        $log.debug("resubmitJob:", job);
        $("#jobStatusCell" + job.jobId).html("RESUBMITTING");
        $("#resubmitBtn" + job.jobId).attr("disabled", "disabled");
        JobsService.resubmitJob(job.jobId, job.jobPriority).then(function (resp) {
            if (resp && resp.hasOwnProperty("mpfResponse") &&
                resp.hasOwnProperty("jobId")) {
                if (resp.mpfResponse.responseCode != 0) {
                    NotificationSvc.error(resp.mpfResponse.message);
                } else {
                    NotificationSvc.success('Job ' + job.jobId + ' has been resubmitted!');
                }
            } else {
                NotificationSvc.error('Failed to send a resubmit request');
            }
        });
    };

    var cancelJob = function (job) {
        $("#jobStatusCell" + job.jobId).html("CANCELLING");
        JobsService.cancelJob(job.jobId).then(function (resp) {
            if (resp && resp.hasOwnProperty("responseCode") &&
                resp.hasOwnProperty("message")) {
                if (resp.responseCode != 0) {
                    NotificationSvc.error('Error with cancellation request with message: ' + mpfResponse.message);
                } else {
                    NotificationSvc.success('A job cancellation request for job ' + job.jobId + ' has been sent.');
                }
            } else {
                NotificationSvc.error('Failed to send a cancellation request');
            }
        });
    };

    var showMarkup = function (job) {
        $scope.selectedJob = job;
        renderMarkupTable();
        $("#mediaModal").modal('show');
    };

    //markupTable - paged for efficiency
    var renderMarkupTable = function () {
        markupTable = $('#markupsTable').DataTable({
            destroy: true,
            data: markupTableData,
            stateSave: false,
            serverSide: true,
            processing: true,
            scrollCollapse: false,
            lengthMenu: [[5, 10, 25, 50, 100, 250], [5, 10, 25, 50, 100, 250]],
            pageLength: 25,
            ordering: false,
            searchHighlight: true,
            ajax: {
                url: "markup/get-markup-results-filtered",
                type: "POST",
                data: function (d) {//extra params
                    d.search = d.search.value;//pull out because spring is a pain to pass params
                    d.jobId = $scope.selectedJob.jobId;
                }
            },
            renderer: "bootstrap",
            columns: [
                {
                    data: "sourceImg",
                    render: function (data, type, obj) {
                        if (obj.sourceUri && obj.sourceUri.length > 0 && obj.sourceFileAvailable) {
                            obj.sourceImg = "server/node-image?nodeFullPath=" + obj.sourceUri.replace("file:", "");
                            obj.sourceDownload = "server/download?fullPath=" + obj.sourceUri.replace("file:", "");
                            obj.sourceType = getMarkupType(obj.sourceUriContentType);
                            if (obj.sourceType == 'image') {
                                return '<img src="' + obj.sourceImg + '" alt="" class="img-btn" data-download="' + obj.sourceDownload + '" data-file="' + obj.sourceUri + '" >';
                            }
                            else if (obj.sourceType == 'audio') {
                                return '<span class="glyphicon glyphicon-music"></span>';
                            }
                            else if (obj.sourceType == 'video') {
                                return '<span class="glyphicon glyphicon-film"></span>';
                            }
                            else {
                                return '<span class="glyphicon glyphicon-file"></span>';
                            }
                        }
                        return '<p class="text-muted">Source file remotely hosted or not available</p>';
                    }
                },
                {data: "sourceUri"},
                {
                    data: "sourceDownload",
                    render: function (data, type, obj) {
                        if (obj.sourceDownload) {
                            return '<a href="' + obj.sourceDownload + '" download="' + obj.sourceUri + '" class="btn btn-default" role="button" title="Download"><i class="fa fa-download"></i></a>';
                        } else {
                            return '<p class="text-muted">Source file remotely hosted or not available</p>';
                        }
                    }
                },
                {
                    data: "markupImg",
                    render: function (data, type, obj) {
                        if (obj.markupUri && obj.markupUri.length > 0 && obj.markupFileAvailable) {
                            obj.markupImg = "markup/content?id=" + obj.id;
                            obj.markupDownload = "markup/download?id=" + obj.id;
                            obj.markupType = getMarkupType(obj.markupUriContentType);
                            if (obj.markupType == 'image') {
                                return '<img src="' + obj.markupImg + '" alt="" class="img-btn" data-download="' + obj.markupDownload + '" data-file="' + obj.markupUri + '" >';
                            }
                            else if (obj.markupType == 'audio') {
                                return '<span class="glyphicon glyphicon-music"></span>';
                            }
                            else if (obj.markupType == 'video') {
                                return '<span class="glyphicon glyphicon-film"></span>';
                            } else {
                                return '<span class="glyphicon glyphicon-file"></span>';
                            }
                        }
                        return '<p class="text-muted">No markup</p>';
                    }
                },
                {
                    data: "markupUri",
                    render: function (data, type, obj) {
                        if (obj.markupUri) {
                            return obj.markupUri;
                        } else {
                            return '<p class="text-muted">No markup</p>';
                        }
                    }
                },
                {
                    data: "markupDownload",
                    render: function (data, type, obj) {
                        if (obj.markupDownload) {
                            return '<a href="' + obj.markupDownload + '" download="' + obj.markupUri + '" class="btn btn-default" role="button"><i class="fa fa-download" title="Download"></i></a>';
                        } else {
                            return '<p class="text-muted">No markup</p>';
                        }
                    }
                }
            ],
            initComplete: function (settings, json) {
                $log.debug("MediaTable complete");
            },
            drawCallback: function (settings) {
                $('.img-btn').on('click', function () {
                    $('#imageModalTitle').text($(this).data('file'));
                    $('#imageModal .modal-body img').attr("src", $(this).prop('src'));
                    $('#imageModalDownloadBtn').attr("href", $(this).data('download'));
                    $('#imageModalDownloadBtn').attr("download", $(this).data('file'));
                    $('#imageModal').modal('show');
                });
            }
        });
    };

    var getMarkupType = function (content_type) {
        if (content_type != null) {
            var type = content_type.split("/")[0].toLowerCase();
            if ((type == "image" && content_type.indexOf("tif") == -1) || type == "audio" || type == "video") {
                return type;
            }
            return "file";
        }
        return null;
    };

    init();
};
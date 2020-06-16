/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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
 * AdminStatsCtrl
 * @constructor
 */
var AdminStatsCtrl = function ($scope, $http, JobsService, TimerService) {

    var simon_service_url = "javasimon-console/data/list.json?pattern=org.mitre.mpf.wfm.*&type=STOPWATCH";
    var simon_recent_data = [];//most recent data from last pull to
    $scope.jobs_recent_data = [];//most recent data from last pull to
    $scope.timer_since = "";
    var default_timer_counter = 15;
    $scope.timer_counter = default_timer_counter;
    var astats_table = null;
    $scope.jobs_message = "Loading....";
    var jobPlot = null;

    var init = function () {
        //add the table and wait until the table is on page then start updates
        astats_table = $('#astats_dataTable-processes').DataTable({
            //responsive : false,
            "autoWidth": false,
            bFilter: false,
            paginate: false,
            "ordering": true,
            "initComplete": function () {
                updateData();
                TimerService.register("admin_stats_counter", $scope.timerTickHandler, 1000, 1000);//start the table
            },
            "createdRow": function (row, data, index) {//style the row cell   bootstrap styles: primary,info,link,danger,success, warning
                if (data[2] > 0) {
                    $('td', row).eq(2).addClass('success');
                } else {
                    $('td', row).eq(2).addClass('danger');
                }
            }
        });
    };

    $scope.timerTickHandler = function () { //every second
        //console.log('AdminStats - timerTickHandler');
        if ($scope.timer_counter <= 0) {
            //console.log('AdminStats - timerTickHandler - refresh');
            updateData();
            $scope.timer_counter = default_timer_counter;
        }
        $scope.timer_counter -= 1;
    };

    var updateData = function () {
        refreshSimonData();
        refreshJobsData();
        $scope.timer_since = Utils.getTime();
    };

    $scope.refreshRequest = function () {
        $scope.timer_counter = default_timer_counter;
        updateData();
    };

    //get simon data
    var refreshSimonData = function () {
        $http.get(simon_service_url).success(function (json) {
           // console.log('Simon Data returned', json);
            if (json) { //should be array
                astats_table.clear();
                $(json).each(
                    function (idx, ele) {
                        var d = [ele.name.replace(/org.mitre.mpf.wfm./, ""),
                            ele.counter, ele.total, ele.min || 0,
                            ele.mean || 0, ele.max || 0];

                        astats_table.row.add(d);
                    });
                astats_table.draw();
            } else {
                Utils.debug("No Simon data", json);
            }
            simon_recent_data = json;
            //force refresh to reload
            if (!$scope.$$phase) $scope.$apply();
        });
    };
    var refreshJobsData = function () {
        JobsService.getStats().then(function (json) {
          //  console.log('Job Stats Data returned', json);
            var html = "<h2>Total Jobs:" + json.totalJobs + "</h2>";
            var job_arr = [];
            var cnt = 0;

            for (var idx in json.aggregatePipelineStatsMap) {
                //console.log(idx,json.data[idx]);
                var txt = "<br/><strong>" + idx + "</strong> <br/># Jobs:"
                    + json.aggregatePipelineStatsMap[idx].count + " <br/>Total time:"
                    + json.aggregatePipelineStatsMap[idx].totalTime + " <br/>Average time:"
                    + json.aggregatePipelineStatsMap[idx].totalTime
                    / json.aggregatePipelineStatsMap[idx].validCount + " (ms)<br/> Max time:"
                    + json.aggregatePipelineStatsMap[idx].maxTime + " (ms)<br/> Min time:"
                    + json.aggregatePipelineStatsMap[idx].minTime + " (ms)";
                var time = json.aggregatePipelineStatsMap[idx].maxTime / 1000 / 60; //minutes
                job_arr.push({
                    data: [[cnt, time]],
                    label: cnt + " " + idx,
                    info: txt
                });
                cnt += 1;
                //html += txt;
            }
            $scope.jobs_recent_data = json.aggregatePipelineStatsMap;
            $scope.jobs_message = "";

            //update plot
            Utils.debug("[admin_statistics view] plot", job_arr);

            if (job_arr.length > 0) {
                if(jobPlot) {
                    jobPlot.destroy();
                    jobPlot = null;
                }
                jobPlot = $.plot("#job-stats-container-placeholder", job_arr, {
                    series: {
                        bars: {
                            show: true,
                            barWidth: 0.6,
                            align: "center"
                        }
                    },
                    xaxis: {
                        mode: "categories",
                        tickLength: 0
                    },
                    yaxis: {
                        ticks: [1, 10, 60, 720, 1440],
                        transform: function (v) {
                            return Math.log(v + 1); //move away from zero
                        }
                    },
                    grid: {
                        hoverable: true
                        //clickable: true
                    },
                    legend: {
                        container: $("#job-stats-legend")
                    }
                });
            }

            $("#job-stats-container-placeholder")
                .bind("plothover",
                    function (event, pos, item) {
                        var str = "(" + pos.x.toFixed(2) + ", " + pos.y.toFixed(2) + ")";
                        $("#hoverdata").text(str);
                        if (item) {
                            Utils.debug(item.series.label, item);
                            var job = job_arr[item.datapoint[0]];
                            var x = item.datapoint[0].toFixed(2), y = item.datapoint[1]
                                .toFixed(2);
                            var plotoffset = jobPlot.offset();
                            var plotw = jobPlot.width();
                            var ploth = jobPlot.height();

                            $("#job-stats-tooltip").html(job.info).css({
                                top: plotoffset.top + ploth / 2,
                                left: plotoffset.left + plotw / 4
                            }).fadeIn(200);
                        } else {
                            $("#job-stats-tooltip").hide();
                        }
                    });

            if (!$scope.$$phase) $scope.$apply();
        });
    }
    $scope.switchTab = function (tab) {
        refreshJobsData();//table shrinks, need to reload and resize
        $("#job-stats-tooltip").hide();//close tool tip anyway
    }

    //cleanup after leaving page
    $scope.$on("$destroy", function() {
       // console.log("destroying AdminStats timer");
        TimerService.unregister("admin_stats_counter");//kill the timer
    });

    init();
};
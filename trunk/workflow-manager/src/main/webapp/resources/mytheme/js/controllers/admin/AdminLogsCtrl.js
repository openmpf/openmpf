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
 * AdmingLogsCtrl
 * @constructor
 */
var AdminLogsCtrl = function ( $scope, $log, $http, $interval, $timeout, LogService ) {

    // NOTE: The performance bottleneck of viewing logs is a result of:
    //
    // 1. Initially parsing the entire log file for relevant lines. This is performed server side.
    // All file characters are parsed from beginning to end in O(n) time to look for newline chars.
    // This happens every time the user switches log files or the log level filter because nothing
    // is cached.
    //
    // 2. Manipulating the textarea string in javascript. Thus, retrieve the log file data from the
    // server in as few requests as possible to prevent unnecessary calls to appendLogText(). Assume
    // that a GET response can handle 300 MB of data, which should be much more than any of the log
    // file size limits (as configured through log4j or log4cxx).

    // NOTE: maxLines may be greater than maxChars. This simply forces the server to parse a larger
    // chunk of the log file (in a single request) than can be displayed in the text area. It's more
    // efficient to drop lines from a large response.text than it is to send multiple requests.
    var maxLines = 1.0 * 1024 * 1024; // per request
    var maxChars = 0.5 * 1024 * 1024; // 0.5 MB; text area limit
    var timeUpdateInterval = 1000;
    var timerCounter=5;
    var dataUpdateInterval = 5;//seconds

    var timeFetched = null;
    var nodeSelection = null;
    var logSelection = null;
    var logLevelSelection = "info"; // default
    var availableLogs = [];
    $scope.availableLogLevels = ["ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"];
    $scope.autoScroll = true;//checkbox

    var mapTimer = null; // timer used to retrieve nodes and logs map

    var lastChecked = -1; // last time the file was checked for changes
    var lastPosition = -1; // last position within the file
    var lastLineLevel = null; // log level of last line read from file
    var cycleId = 0; // increment each time the user switches to new search criteria (log file, log level, etc.)

    $scope.truncModeEnabled = false;

    $scope.alertType="alert-info";
    $scope.alertMessage ="Please select a host, log and level using the drop down menus.";
    $scope.alertShow=true;
    $scope.alertLimitDefault ="Log history exceeds viewing limit. Some older content may not be displayed.";

    var nodeTitleDefault = "Available Hosts";
    $scope.nodeTitle = nodeTitleDefault;
    var logTitleDefault = "Available Logs";
    $scope.logTitle = logTitleDefault;
    var logPlaceholderDefault = "Log entries that are more severe than, or equal to, the selected log level will also be displayed: FATAL > ERROR > WARN > INFO > DEBUG > TRACE > ALL.";
    $scope.logPlaceholder = logPlaceholderDefault;
    $scope.logLevelTitle = "INFO";
    $scope.logTime="";

    $scope.nodesAndLogs = {}; // map of hostnames to collections of log files

    var initView = function () {

        configureLogTextAreaHeight();
        //resize event of the page:
        $(window).resize(configureLogTextAreaHeight);

        // handler for when a key is pressed
        $("#logText").keypress(function (e) {
            e.preventDefault(); // ignore key presses (make text area unmodifiable)
        });

        update();

        mapTimer = $interval(function () {
            update();
        }, timeUpdateInterval);


        //Grab existing node, log, and level if exists!
        if (LogService.lastNodeSelection) {
            $scope.handleNodeSelect(LogService.lastNodeSelection);
        }
        if (LogService.lastLogSelection) {
            $scope.handleLogSelect(LogService.lastLogSelection);
        }
        if (LogService.lastLogLevelSelection) {
            $scope.handleLogLevelSelect(LogService.lastLogLevelSelection);
        }

        //update if set
        if (nodeSelection && logSelection) {
            prepareNewCycle();
            updateLogTail();
        }

        $log.debug("admin_logs ready!"); // DEBUG
    }

    //this will need to change if the height of the navbar or the height of the buttons row changes
    var configureLogTextAreaHeight = function () {
        var padding = 80;
        if($scope.alertShow) padding +=80;
        if($scope.truncModeEnabled) padding +=25;
        var viewportHeight = $(window).height();
        var fitHeight = viewportHeight - $("#navbarMain").height() - $("#logButtonsRow").height() - padding;
        $("#logText").height(fitHeight);
    };

    // handler for when a node is chosen
    $scope.handleNodeSelect = function (node) {
        // handle new node selection
        nodeSelection = node;

        LogService.lastNodeSelection = node; //store globally

        // kill current cycle and force user to select a valid log file
        prepareNewCycle();

        //update some ui
        $scope.nodeTitle = node;//button text
        $scope.logPlaceholder = logPlaceholderDefault;
        $scope.logTitle = logTitleDefault;
        logSelection = null;//clear the available logs

        if (!$scope.$$phase) $scope.$apply();

        // update log list
        updateAvailableLogs();//populate the logs for this node
    }

    // handler for when a log level is chosen
    $scope.handleLogSelect = function (log) {
        logSelection = log;
        LogService.lastLogSelection = log;//store globally
        $scope.logTitle = log;//button text
        $scope.alertShow = false;
        if (!$scope.$$phase) $scope.$apply();
        configureLogTextAreaHeight();

        if (nodeSelection && logSelection) {
            prepareNewCycle();
            updateLogTail();
        }
    }

    $scope.handleLogLevelSelect = function (level) {
        $scope.logLevelTitle = level;
        logLevelSelection = level;
        LogService.lastLogLevelSelection = level;//store globally
        if (!$scope.$$phase) $scope.$apply();

        if (nodeSelection && logSelection) {
            prepareNewCycle();
            updateLogTail(); // repeats
        }
    }

    var update = function () {
        if(timerCounter >=dataUpdateInterval ) {
            //$log.debug("update");
            updateTime();

            LogService.getLogsMap().then(function (data) {
                $scope.nodesAndLogs = data;
                updateAvailableLogs();
                if (!$scope.$$phase) $scope.$apply();
                if (nodeSelection && logSelection) {
                    $scope.alertShow = false;
                    updateLogTail();
                }
            });
            timerCounter=0;
        }
        timerCounter ++;
    };

    var updateAvailableLogs = function () {
        if (nodeSelection) {
            $("#logSelection").prop("disabled", false);
            $scope.availableLogs = $scope.nodesAndLogs[nodeSelection];
            if (!$scope.$$phase) $scope.$apply();
        }
    };

   var updateLogTail = function () {
        var params = {
            nodeSelection: nodeSelection,
            logSelection: logSelection,
            logLevelSelection: logLevelSelection,
            maxLines: maxLines,
            maxChars: maxChars,
            cycleId: cycleId,
            lastChecked:lastChecked,
            lastPosition:lastPosition,
            lastLineLevel: lastLineLevel
        };
        LogService.getLogs(params).then(function (data) {
            //$log.debug('adminLogsMap response: ',data); // DEBUG
            updateLogData(data);
            updateAvailableLogs();
            $scope.alertShow=false;
            if (!$scope.$$phase) $scope.$apply();
            configureLogTextAreaHeight();
        });
    };

    var updateTime = function () {
        timeFetched = moment(); // get current time from moment library; result is an instance of moment (the js lib object)
        if(logSelection) {
            $scope.logTime = ' Last checked ' + timeFetched.fromNow() + ' (at ' + timeFetched.format('h:mm:ss a') + ')'
            if (!$scope.$$phase) $scope.$apply();
        }
    };

    var updateLogData = function (response) {

        if (response.cycleId != cycleId) {
            return; // this is a response for an old request we no longer care about
        }
         if (response.logExists === true) {
            lastChecked = response.lastChecked;
            lastPosition = response.lastPosition;
            lastLineLevel = response.lastLineLevel;

             LogService.lastLogResponseInfo = {
             lastChecked: lastChecked,
             lastPosition: lastPosition,
             lastLineLevel: lastLineLevel
             };

             if (response.text) {
                if (response.skippedAhead) {
                    enableTruncMode(true);
                }
                appendLogText(response.text);
            } else {
                if (!$("#logText").val()) {
                    $scope.logPlaceholder = "No matching log content (yet) ...";
                }
            }
            timeFetched = moment(); // get current time from moment library; result is an instance of moment (the js lib object)
            updateTime();
            if (!$scope.$$phase) $scope.$apply();
            if (response.numLines == maxLines) {
                updateLogTail(); // immediately request remaining data
                return;
            }
        } else {
            $scope.logPlaceholder = "Log does not exist (yet) ...";
            if (!$scope.$$phase) $scope.$apply();
        }
    };

    var appendLogText = function (newText) {
        if(newText && newText.length > 0) {
            var oldText = $("#logText").val();

            if (oldText) {
                oldText += '\n';
            }
            var allText = oldText + newText
            if (allText.length > maxChars) {
                var startIndex = allText.indexOf("\n", allText.length - maxChars); // try to capture a full starting line
                if (startIndex == -1) {
                    startIndex = allText.length - maxChars;
                } else {
                    startIndex++; // one char beyond the newline
                }
                allText = allText.substring(startIndex, allText.length); // truncate starting chars
                enableTruncMode(true);
            }

            $("#logText").val(allText); // append

            //store the log text if it exists
            LogService.lastAllLogText = allText;

            if ($scope.autoScroll) {
                $("#logText").scrollTop($("#logText")[0].scrollHeight); // auto-scroll to bottom
            }
        }
    }

    var enableTruncMode = function (enable) {
        $scope.truncModeEnabled = enable;
        configureLogTextAreaHeight();
    }

    var prepareNewCycle = function () {
        lastChecked = -1;
        lastPosition = -1;
        lastLineLevel = null;

        $("#logText").val(""); // clear
        $scope.logPlaceholder = "Please wait. This may take a minute or two for large files ...";

        enableTruncMode(false);

        if (!$scope.$$phase) $scope.$apply();

        cycleId++; // start a new cycle
    }

    //cleanup after leaving page
    $scope.$on("$destroy", function() {
        if (angular.isDefined(mapTimer)) {
            $interval.cancel(mapTimer);
            mapTimer = undefined;
        }
    });

    initView();
};

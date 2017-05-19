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
/* globals angular, _ */

(function () {

var templateUrlPath = 'resources/js/directives';


    var module = angular.module('simplePipeline', []);

    module.directive('taskSequence', [ function () {
        return {
            templateUrl: templateUrlPath + '/simplePipeline/taskSequence.html',
            restrict: 'EA',
            scope: {
                tasks: "=",
                canEdit: "=",
                opObj: "="
            },
            link: function( scope /*, element, attrs*/ ) {
                scope.isArray = angular.isArray;

                scope.logTasks = function() {
                    console.log("tasks->");
                    console.log(scope.tasks);
                };
            }
        };
    }]);


    module.directive('mpfTask', [function () {
        return {
            templateUrl: templateUrlPath + '/simplePipeline/mpfTask.html',
            restrict: 'EA',
            scope: {
                task: "=",
                canEdit: "=",
                indexInSequence: "@",
                opObj: "="
            },
            link: function (scope /*, element, attrs*/) {
                scope.logTask = function () {
                    console.log("task->");
                    console.log(scope.task);
                    console.log("indexInSequence=" + scope.indexInSequence)
                    console.log("containsParallelActions()->");
                    console.log(scope.containsParallelActions());
                };

                scope.containsParallelActions = function () {
                    return ( scope.task.actions.length > 1 )
                };

                // remove a task from task sequence (pipeline)
                //  if task is null, then user wants to create a new action/task
                scope.removeTaskFromPipeline = function ($event) {
                    console.log("removeTaskFromPipeline( $event -> )");
                    console.log($event);
                    $event.stopPropagation();   // so it doesn't also fire popover
                    scope.opObj.removeTaskFromPipeline(scope.indexInSequence);
                };

            },
            controller: ['$scope', function ($scope) {
                $scope.canEdit = function () {
                    if ($scope.opObj) {
                        return $scope.opObj.inEditMode();
                    }
                    else {
                        return true;
                    }
                };
            }]
        };
    }]);


    module.directive('action', [
        'ActionService', 'TaskService',
        function ( ActionService, TaskService ) {
            return {
                templateUrl: templateUrlPath + '/simplePipeline/action.html',
                restrict: 'A',
                transclude: true,
                scope: {
                    actionObj: "=?",
                    noPopover: "=?",
                    indexInSequence: "@?",  // for actions inside both parallel and non-parallel tasks
                    opObj: "=?",
                    taskObj: "="
                },
                link: function( scope, element, attrs ) {
                    scope.arrowIn = attrs.hasOwnProperty('arrowIn');
                    scope.showPopover = !attrs.hasOwnProperty('noPopover');
                    // arrowOut without a condition is equivalent to arrowOut="true"
                    scope.arrowOut = attrs.hasOwnProperty('arrowOut');
                    if (scope.arrowOut && attrs.arrowOut !== "") {
                        attrs.$observe('arrowOut', function (value) {
                            scope.arrowOut = scope.$eval(value);
                        })
                    }
                    // scope.isParallel = attrs.hasOwnProperty('isParallel');

                    // get all available tasks
                    scope.updateAvailableTasks = function () {
                        scope.availableTasks = TaskService.query();
                    };

                    // add a task to task sequence (pipeline)
                    //  if task is null, then user wants to create a new action/task
                    scope.addTaskToPipeline = function (task) {
                        // console.log("addTaskToPipeline( task -> )");
                        // console.log(task);
                        // console.log("indexInSequence=" + scope.indexInSequence)
                        scope.opObj.addTaskToCurrentPipeline(task.name, scope.indexInSequence);
                    };

                    // remove a task from task sequence (pipeline)
                    //  if task is null, then user wants to create a new action/task
                    scope.removeTaskFromPipeline = function ($event) {
                        // console.log("removeTaskFromPipeline( $event -> )");
                        // console.log($event);
                        $event.stopPropagation();   // so it doesn't also fire popover
                        scope.opObj.removeTaskFromPipeline(scope.indexInSequence);
                    };

                },
                controller: ['$scope', function ($scope) {
                    $scope.canEdit = function() {
                        if ( $scope.opObj ) {
                            return $scope.opObj.inEditMode();
                        }
                        else {
                            return true;
                        }
                    };
                    $scope.inParallelTask = function() {
                        if ( $scope.opObj && $scope.opObj.tasks2 ) {
                            return $scope.opObj.tasks2.containsParallelActions();
                        }
                        else {
                            return false;
                        }
                    };
                    //
                    // // remove a action
                    // //      either from task (if it's part of a parallel task)
                    // //      or its related task from the pipeline (if it's not part of a parallel task)
                    // $scope.remove = function ($event) {
                    //     console.log("remove( $event -> )");
                    //     console.log($event);
                    //     $event.stopPropagation();   // so it doesn't also fire popover
                    //     if ( $scope.inParallelTask() ) { // this is part of a series of parallel actions
                    //         // $scope.opObj.tasks2.removeActionFromTask($scope.taskObj,$scope.indexInSequence);
                    //     }
                    //     else {
                    //         $scope.opObj.removeTaskFromPipeline($scope.indexInSequence);
                    //     }
                    // };

                    // $scope.$on( 'PIPELINE_ENTER_EDIT_MODE', function( event, data ) {
                    //     console.log("received event-> and data-> in action directive containing action->");
                    // });
                    // $scope.$on( 'PIPELINE_EXIT_EDIT_MODE', function( event, data ) {
                    //     console.log("received event-> and data-> in action directive containing action->");
                    // });
                }]
            };
        }
    ]);

}());
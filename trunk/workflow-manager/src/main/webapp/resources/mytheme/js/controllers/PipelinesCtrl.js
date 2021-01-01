/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

/* globals angular, _, console */


/**
 * PipelinesCtrl
 * @constructor
 */
var PipelinesCtrl = function($scope,$http, $timeout, NotificationSvc) {

    $scope.selectedAction = null;
    $scope.selectedTask = null;
    $scope.actionsToAdd = [];
    $scope.tasksToAdd = [];
    //use like this: modifiedAlgValuesMap[propertyName] = value
    $scope.modifiedAlgValuesMap = {};

    var getAllPipelineElements = function() {
        $http.get('actions')
            .success(function (actions) {
                $scope.actions = actions;
            });

        $http.get('tasks')
            .success(function (tasks) {
                $scope.tasks = tasks;
            });

        $http.get('pipelines')
            .success(function (pipelines) {
                $scope.pipelines = pipelines;
            });

        $http.get('algorithms')
            .success(function (algorithms) {
                $scope.algorithms = algorithms;
            });
    };

    $scope.resetModifiedAlgoProps = function() {
        $scope.modifiedAlgValuesMap = {};
    };

    $scope.createAction = function() {
        if($scope.selectedAlgorithm && $scope.modifiedAlgValuesMap &&
            $scope.modalName && $scope.modalDescription) {

            var actionProps = _.map($scope.modifiedAlgValuesMap, function (value, key) {
                return {name: key, value: value};
            });

            var dataObj = {
                name: $scope.modalName,
                description: $scope.modalDescription,
                algorithm: $scope.selectedAlgorithm.name,
                properties: actionProps
            };
            var res = $http.post('actions', dataObj);
            res.success(function(responseTuple, status, headers, config) {
                //should be true/null on success
                var successMsg = 'Successfully added the action ' + $scope.modalName;

                $scope.modalName = "";
                $scope.modalDescription = "";
                $scope.modalType = "";
                //reload model
                getAllPipelineElements();

                $scope.selectedAlgorithm = "";
                $scope.modifiedAlgValuesMap = {};
                $scope.showAddAction = false;

                //temporary jquery solution to dismiss modal
                $('#addModal').modal('toggle');

                $timeout(function() {
                    NotificationSvc.success(successMsg);
                }, 350);
            });
            res.error(function(data) {
                alert( "failure message: " + data.message);
            });

        } else {
            alert('missing fields');
        }
    };

    $scope.createTaskOrPipeline = function() {
        if($scope.modalName && $scope.modalDescription) {
            var modelToSend = {
                name : $scope.modalName,
                description : $scope.modalDescription
            };

            var url;
            if ($scope.modalType === 'task') {
                modelToSend.actions = $scope.actionsToAdd;
                url = 'tasks';
            }
            else {
                modelToSend.tasks = $scope.tasksToAdd;
                url = 'pipelines';
            }

            var res = $http.post(url, modelToSend);
            res.success(function() {
                var successMsg = 'Successfully added the ' + $scope.modalType+ ' ' + $scope.modalName;

                $scope.modalName = "";
                $scope.modalDescription = "";
                //reload model
                getAllPipelineElements();
                if($scope.modalType === 'task') {
                    $scope.actionsToAdd = [];
                    $scope.showAddTask = false;
                } else { /*pipeline*/
                    $scope.tasksToAdd = [];
                    $scope.showAddPipeline = false;
                }
                $scope.modalType = "";
                //temporary jquery solution to dismiss modal
                $('#addModal').modal('toggle');

                $timeout(function() {
                    NotificationSvc.success(successMsg);
                }, 350);
            });
            res.error(function(data) {
                alert( "failure message: " + data.message);
            });
        } else {
            alert('missing fields');
        }
    };

    $scope.create = function() {
        if($scope.modalType === 'action') {
            $scope.createAction();
        } else {
            $scope.createTaskOrPipeline();
        }
    }

    $scope.removeAction = function(index) {
        console.log('index: ' + index);
        $scope.actionsToAdd.splice(index,1);
    };

    $scope.removeTask = function(index) {
        console.log('index: ' + index);
        $scope.tasksToAdd.splice(index,1);
    };


    $scope.deleteAction = function (actionName) {
        $http.delete('actions?name=' + actionName)
            .success(function () {
                $scope.actions = _.reject($scope.actions, {name: actionName});
            });
    };

    $scope.deleteTask = function (taskName) {
        $http.delete('tasks?name=' + taskName)
            .success(function () {
                $scope.tasks = _.reject($scope.tasks, {name: taskName});
            });
    };

    $scope.deletePipeline = function (pipelineName) {
        $http.delete('pipelines?name=' + pipelineName)
            .success(function () {
                $scope.pipelines = _.reject($scope.pipelines, {name: pipelineName});
            });
    };

    getAllPipelineElements();
};

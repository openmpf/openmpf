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

/* globals angular, _ */

/**
 *
 * Pipelines are the same as in the client code;
 *  They wrap 1 or more tasks.
 *
 *  Pipeline objects are of the form:
 *      name:  name of pipeline, which is always all caps, and represents
 *              both the human readable name as well as UID of the object
 *      description:  description of pipeline
 *      taskRef:  array of tasks, which are of the form:
 *          name: name of task
 *
 */

var global_p2 = null;
var global_var = null;
var global_var2 = null;

(function () {

    'use strict';

    var module = angular.module('mpf.wfm.pipeline2', [
        'ngResource', 'simplePipeline',
        'mpf.wfm.pipeline2.task', 'mpf.wfm.pipeline2.action', 'mpf.wfm.pipeline2.algorithm']);

    module.factory('Pipelines2Service',
    ['$q', '$resource', 'ActionService', 'TaskService',
    function ($q, $resource, ActionService, TaskService) {

        var pipelineResource = $resource('pipelines/:name');

        var getPipeline = function (pipelineName) {
            return pipelineResource.get({name: pipelineName})
                .$promise
                .then(function (pipelineDetail) {

                    var taskPromises = pipelineDetail.taskRefs.map(function (t) {
                        return TaskService.get( t.name );
                    });

                    return $q.all(taskPromises).then(function (tasks) {
                        pipelineDetail.vmTasks = tasks;
                        return pipelineDetail;
                    });
                });
        };


        return {
            getCompletePipelineDetails: function (pipelineName) {
                var pipeline = new pipelineResource({
                    $resolved: false,
                    $promise: getPipeline(pipelineName)
                        .then(function (pipelineData) {
                            angular.forEach(pipelineData, function (value, key) {
                                console.log("pipelineData->");
                                console.log(pipelineData);
                                if (key !== '$resolved' && key !== '$promise') {
                                    pipeline[key] = value;
                                    console.log("pipeline[key->]->");
                                    console.log(key);
                                    console.log(pipeline[key]);
                                }
                            });
                            pipeline.$resolved = true;
                            return pipeline;
                        })
                });
                return pipeline;
            },
            query: function () {
                return pipelineResource.query();
            },
            save: function (pipeline) {
                console.log("PipelineResource.save( pipeline-> )");
                console.log(pipeline);
                var pack = {
                    name: pipeline.name,
                    description: pipeline.description,
                    tasksToAdd: _.pluck(pipeline.taskRefs, "name")
                };
                console.log("pack->");
                console.log(pack);
                return pipelineResource.save(pack, function () {
                    console.log("saved pack")
                });
            },
            delete: function (pipeline) {
                console.log("PipelineResource.delete( pipeline -> )");
                console.log(pipeline);
                return pipelineResource.delete({name: pipeline.name}, function () {
                    console.log("deleted " + pipeline.name);
                });
            }
        };
    }]
    );


    module.controller('Pipelines2Ctrl', [
        '$scope', '$log', '$confirm', 'orderByFilter',
        'Pipelines2Service', 'TaskService', 'ActionService', 'AlgorithmService',
        function ( $scope, $log, $confirm, orderByFilter,
                   Pipelines2Service, TaskService, ActionService, AlgorithmService) {


            var pipes2 = this;


            /** create new pipeline */
            pipes2.newPipeline = function() {
                $scope.currentPipeline = {
                    name: "",
                    description: "",
                    taskRefs: [],
                    vmTasks: []
                };
                $scope.editMode = true;
                console.log("newPipeline");
            };


            /** add named task to current pipeline
             *  Note this updates the view and model, but does not save to the server*/
            pipes2.addTaskToCurrentPipeline = function( taskname, index )
            {
                if ( taskname ) {
                    var ref = {name: taskname};
                    TaskService.get(taskname)
                        .then(function (taskDetail) {
                            if ( !index ) {
                                $scope.currentPipeline.vmTasks.push(taskDetail);
                                $scope.currentPipeline.taskRefs.push(ref);
                            }
                            else {
                                $scope.currentPipeline.vmTasks.splice(index, 0, taskDetail);
                                $scope.currentPipeline.taskRefs.splice(index, 0, ref);
                            }
                        });
                }
                console.log("addTaskToCurrentPipeline | $scope.currentPipeline ->");
                console.log($scope.currentPipeline);
            };


            /** removes task at index from current pipeline
             *  Note this updates the view and model, but does not save to the server*/
            pipes2.removeTaskFromPipeline = function( index )
            {
                console.log("removeTaskFromPipeline("+index+")");
                if ( index ) {
                    $scope.currentPipeline.vmTasks.splice(index, 1);
                    $scope.currentPipeline.taskRefs.splice(index, 1);
                }
                console.log("removeTaskFromPipeline | $scope.currentPipeline ->");
                console.log($scope.currentPipeline);
            };


            /** save current pipeline to server */
            pipes2.savePipeline = function( pipeline ) {
                Pipelines2Service.save( pipeline )
                    .$promise
                    .then( function() {
                        $confirm({
                            title: 'Success',
                            text: '"' + pipeline.name + '" was successfully saved.'});
                        initPipelinesList();
                    })
                    .catch( function( error ) {
                        // todo: P038: should display actual error to user, but need the server to return the error in JSON format;
                        //  currently, it only returns a string
                        $confirm({
                            title: 'Error',
                            text: 'An error occurred when saving the pipeline.  Most likely, this is because the name is not unique, or no tasks are defined.'});
                        console.log("***Error from Pipelines2Service.save() :");
                        console.log(error);
                    });
            };


            /** deletes the pipeline from the server */
            pipes2.deletePipeline = function( pipeline ) {
                // todo:  should check to make sure this pipeline can be deleted
                //          by verifying it is not being used, and then
                //          the confirm message below can be changed
                $confirm({text: 'Are you sure you want to delete ' + pipeline.name
                + '?  There may be jobs still using it.'})
                    .then(
                        function() {    // alert("You clicked OK");
                            Pipelines2Service.delete( pipeline )
                                .$promise
                                .then( function() {
                                    initPipelinesList();
                                });
                        }
                    );
            };



            $scope.editMode = false;
            $scope.userShowAllProperties = false;

            $scope.pipelines = [];  // all the pipelines from the server (only name and description are received)
            $scope.tasks = [];  // all tasks from the server
            $scope.actions = [];  // all the actions from the server (only name and description are received)
            $scope.algorithms = []; // all the algorithms from the server

            $scope.currentPipeline = {} ; // the pipeline currently selected
            $scope.currentAction = {} ; // the algorithm currently selected

            /** retrieves and re-renders the actions list */
            var initActionsList = function( selectActionName ) {
                $scope.actions = ActionService.query();
                $scope.actions.$promise.then(function () {
                    $scope.actions = orderByFilter( $scope.actions, '+name');
                    if ( selectActionName ) {
                        $scope.selectAction( selectActionName );
                    }
                    else if ( $scope.actions.length > 0 ) {
                        $scope.selectAction($scope.actions[0].name);
                        $scope.editMode = false;
                    }
                    global_var = $scope.actions[0];
                });
            };

            /** retrieves and re-renders the pipelines list */
            var initPipelinesList = function( selectPipeline ) {
                $scope.pipelines = Pipelines2Service.query();
                $scope.pipelines.$promise.then(function () {
                    $scope.pipelines = orderByFilter( $scope.pipelines, '+name');
                    if ( selectPipeline ) {
                        $scope.selectPipeline( selectPipeline );
                    }
                    else if ($scope.pipelines.length > 0) {
                        $scope.selectPipeline($scope.pipelines[0]);
                        $scope.editMode = false;
                    }
                });
            };

            /** retrieves tasks list  */
            var initTasksList = function() {
                $scope.tasks = TaskService.query();
            };

            /** retrieves algorithms list */
            var initAlgorithmsList = function() {
                $scope.algorithms = AlgorithmService.getAll();
            };

            /** initializes this page */
            var init = function () {
                initPipelinesList();
                initTasksList();
                initActionsList();
                initAlgorithmsList();
            };

            /** returns an object of all properties of algorithm that has been changed
             *  i.e., all the properties that has a value, because
             *  if it does not have a value, it's relying on the defaultValue
             */
            var getChangedActionProperties = function ( action ) {
                var ret = {};
                _.each( action.viewProperties, function( prop )  {
                    if ( prop.value!==undefined && prop.value!=="" ) {
                        ret[prop.name] = prop.value;
                    }
                });
                return ret;
            };


            /** called when the user selects a pipeline from the pipeline list */
            $scope.selectPipeline = function( pipeline ) {
                $scope.editMode = false;
                $scope.currentPipeline = Pipelines2Service.getCompletePipelineDetails(pipeline.name);
                global_var2 = $scope.currentPipeline;
            };


            /** create new action */
            $scope.newAction = function() {
                $scope.currentAction = {};
                $scope.editMode = true;
            };


            /** sets the algorithmRef for an action */
            $scope.setAlgorithmRef = function( alg ) {
                if ( $scope.editMode ) {
                    $scope.currentAction.algorithmRef = alg;
                    $scope.currentAction.algorithm = AlgorithmService.get( alg )
                        .$promise
                        .then( function( algo ) {
                            $scope.currentAction.algorithm = algo;
                            $scope.setViewProperties();
                            // console.log("action=" + JSON.stringify($scope.currentAction));
                        });
                }
            };


            /** saves the action and task to the server */
            $scope.saveActionAndTask = function( action ) {
                saveAction( action )
                    .then( function() {
                        saveTaskFromAction( action );
                    })
            };


            var makeCustomActionName = function( action ) {
                return "CUSTOM " + action.name.toUpperCase() + " ACTION"
            };


            /** saves action and returns the promise from the service */
            var saveAction = function( action ) {
                console.log("saveAction( action -> )");
                console.log(action);

                var actionName = makeCustomActionName( action );
                var actionObj = {
                    algorithmName: action.algorithmRef,
                    actionName: actionName,
                    actionDescription: action.description,
                    properties: JSON.stringify( getChangedActionProperties( action ) )
                };

                console.log("actionObj="+JSON.stringify(actionObj));

                var opPromise = ActionService.save( actionObj )
                    .$promise
                    .then( function() {
                        $confirm({
                            title: 'Success',
                            text: '"' + actionName + '" was successfully saved.'});
                        initActionsList( actionName );
                    })
                    .catch( function( error ) {
                        // todo: P038: should display actual error to user, but need the server to return the error in JSON format;
                        //  currently, it only returns a string
                        $confirm({
                            title: 'Error',
                            text: 'An error occurred when saving the action.  Most likely, this is because the name is not unique, or a parameter is missing.'});
                        console.log("***Error from ActionService.save() :");
                        console.log(error);
                    });
                return opPromise;
            };


            var makeCustomTaskName = function( task ) {
                return "CUSTOM " + task.name.toUpperCase() + " TASK"
            };


            /** saves a single action task from the specified action */
            var saveTaskFromAction = function( action ) {
                console.log("saveTaskFromAction( action -> )");
                console.log(action);

                var taskObj = {
                    name: makeCustomTaskName( action ),
                    description: action.description,
                    actionsToAdd: [ makeCustomActionName( action ) ]
                };
                console.log("taskObj="+JSON.stringify(taskObj));

                TaskService.save( taskObj )
                    .$promise
                    .then( function() {
                        $confirm({
                            title: 'Success',
                            text: '"' + taskObj.name + '" was successfully saved.'});
                        initTasksList();
                    })
                    .catch( function( error ) {
                        // todo: P038: should display actual error to user, but need the server to return the error in JSON format;
                        //  currently, it only returns a string
                        $confirm({
                            title: 'Error',
                            text: 'An error occurred when saving the task.'});
                        console.log("***Error from TaskService.save() :");
                        console.log(error);
                    });
            };


            /** deletes the action from the server */
            $scope.deleteAction = function() {
                // todo:  should check to make sure this action can be deleted
                //          by verifying it is not being used in a task, and then
                //          the confirm message below can be changed
                $confirm({text: 'Are you sure you want to delete ' + $scope.currentAction.name
                            + '?  There may be tasks that are still using it.'})
                    .then(
                        function() {    // alert("You clicked OK");
                            ActionService.delete( $scope.currentAction.name )
                                .$promise
                                .then( function() {
                                    initActionsList();
                                });
                        }
                    );
            };


            /** sets up the viewProperties array for ease of generating the view */
            $scope.setViewProperties = function()  {
                $scope.currentAction.viewProperties = [];
                angular.copy($scope.currentAction.algorithm.providesCollection.algorithmProperties,
                    $scope.currentAction.viewProperties);
                var index;
                _.each( $scope.currentAction.properties, function( prop )  {
                    index = _.findIndex($scope.currentAction.viewProperties, { "name": prop.name });
                    $scope.currentAction.viewProperties[index].value = prop.value;
                });
            };


            /** selects the actions */
            $scope.selectAction = function( actionName ) {
                $scope.editMode = false;
                $scope.currentAction = ActionService.get(actionName);
                $scope.currentAction.$promise.then(function () {
                    $scope.setViewProperties();
                });
                global_var = $scope.currentAction;
            };


            $scope.toggleShowAllProperties = function()
            {
                $scope.userShowAllProperties = !$scope.userShowAllProperties;
            };


            init();
            global_p2 = $scope;
            // return p2Ctrl;
        }
    ]);
})();

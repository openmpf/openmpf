/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

/* globals angular, _, console */

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
 *  Pipelines2Ctrl publishes 'PP2_xxx' messages via $scope.$broadcast(); this assumes that
 *  descendants will process these messages in a $scope.$on() handler:
 *      'PIPELINE_ENTER_EDIT_MODE'
 *      'PIPELINE_EXIT_EDIT_MODE'
 *      'ACTION_ENTER_EDIT_MODE'
 *      'ACTION_EXIT_EDIT_MODE'
 */


(function () {

    'use strict';

    var module = angular.module('mpf.wfm.pipeline2', [
        'ngResource', 'simplePipeline',
        'mpf.wfm.pipeline2.task', 'mpf.wfm.pipeline2.action', 'mpf.wfm.pipeline2.algorithm']);

    module.factory('Pipelines2Service', [
        '$q', '$resource', 'ActionService', 'TaskService',
        function ($q, $resource, ActionService, TaskService) {

            var pipelineResource = $resource('pipelines');

            var getPipeline = function (pipelineName) {
                return pipelineResource.get({name: pipelineName})
                    .$promise
                    .then(function (pipelineDetail) {

                        var taskPromises = pipelineDetail.tasks.map(function (taskName) {
                            return TaskService.get(taskName)
                                .catch(function (error) {
                                    if (error.status === 404) {
                                        return {
                                            name: taskName,
                                            missing: true,
                                            actions: []
                                        };
                                    }
                                });
                        });

                        return $q.all(taskPromises).then(function (tasks) {
                            pipelineDetail.vmTasks = tasks;
                            return pipelineDetail;
                        });
                    });
            };


            return {
                getCompletePipelineDetails: function (pipelineName) {
                    if ( pipelineName ) {
                        var pipeline = new pipelineResource({
                            $resolved: false,
                            $promise: getPipeline(pipelineName)
                                .then(function (pipelineData) {
                                    angular.forEach(pipelineData, function (value, key) {
                                        if (key !== '$resolved' && key !== '$promise') {
                                            pipeline[key] = value;
                                        }
                                    });
                                    pipeline.$resolved = true;
                                    return pipeline;
                                })
                        });
                        return pipeline;
                    }
                },
                query: function () {
                    return pipelineResource.query();
                },
                save: function (pipeline) {
                    // console.log("PipelineResource.save( pipeline-> )");
                    // console.log(pipeline);
                    var pack = {
                        name: pipeline.name,
                        description: pipeline.description,
                        tasks: _.pluck(pipeline.taskRefs, "name")
                    };
                    return pipelineResource.save(pack, function () {
                        // console.log("saved pack")
                    });
                },
                delete: function (pipeline) {
                    return pipelineResource.delete({name: pipeline.name}, function () {
                        // console.log("deleted " + pipeline.name);
                    });
                }
            };
        }
    ]);


    module.controller('Pipelines2Ctrl', [
        '$scope', '$log', '$confirm', 'orderByFilter',
        'Pipelines2Service', 'TaskService', 'ActionService', 'AlgorithmService',
        function ( $scope, $log, $confirm, orderByFilter,
                   Pipelines2Service, TaskService, ActionService, AlgorithmService) {


            /* *****
             *    UTILITIES
             * *****
             */


            var renderAsCustomName = function(name, suffix) {
                var prefix = "CUSTOM ";
                var renderedName = name.trim().toUpperCase();
                var newSuffix = ' ' + suffix.trim().toUpperCase();
                if (!renderedName.startsWith(prefix)) {
                    renderedName = prefix + renderedName;
                }
                if (!renderedName.endsWith(newSuffix)) {
                    renderedName = renderedName + newSuffix;
                }
                return renderedName;
            };


            /* *****
             *    PIPELINE VIEW MANIPULATION OBJECT
             * *****
             */

            var pipes2 = this;
            $scope.pipes2 = pipes2;


            /** create new pipeline */
            pipes2.newPipeline = function() {
                $scope.currentPipeline = {
                    name: "",
                    description: "",
                    taskRefs: [],
                    vmTasks: []
                };
                pipes2.enterEditMode();
            };


            /** properly reformat custom names */
            pipes2.renderAsCustomName = function( name ) {
                return renderAsCustomName( name, "PIPELINE" )
            };


            /** returns the number of tasks in the pipeline */
            pipes2.getNumTasks = function() {
                return $scope.currentPipeline.vmTasks.length;
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
            };


            /** removes task at index from current pipeline
             *  Note this updates the view and model, but does not save to the server*/
            pipes2.removeTaskFromPipeline = function( index )
            {
                // console.log("removeTaskFromPipeline("+index+")");
                if ( index ) {
                    $scope.currentPipeline.vmTasks.splice(index, 1);
                    $scope.currentPipeline.taskRefs.splice(index, 1);
                }
                // console.log("removeTaskFromPipeline | $scope.currentPipeline ->");
                // console.log($scope.currentPipeline);
            };


            /** save current pipeline to server */
            pipes2.savePipeline = function( pipeline ) {
                pipeline.name = pipes2.renderAsCustomName( $scope.currentPipeline.name );
                Pipelines2Service.save( pipeline )
                    .$promise
                    .then( function() {
                        $confirm({
                            title: 'Success',
                            text: '"' + pipeline.name + '" was successfully saved.'});
                        initPipelinesList( pipeline );
                    })
                    .catch( function( error ) {
                        // todo: P038: should display actual error to user, but need the server to return the error in JSON format;
                        //  currently, it only returns a string
                        $confirm({
                            title: 'Error',
                            text: 'An error occurred when saving the pipeline: ' + error.data.message});
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


            pipes2.editMode = false;


            /* returns true iff pipeline is in edit mode */
            pipes2.inEditMode = function() {
                return ( pipes2.editMode === true );
            };


            /* sets editing on pipeline */
            pipes2.enterEditMode = function()  {
                // $scope.$broadcast(
                //     "PIPELINE_ENTER_EDIT_MODE", {
                //         pipeline: $scope.currentPipeline,
                //         state: pipes2,
                //         type: 'NEW_PIPELINE' } );
                pipes2.editMode = true;
            };


            /* sets read-only mode on pipeline */
            pipes2.exitEditMode = function()  {
                // $scope.$broadcast(
                //     "PIPELINE_EXIT_EDIT_MODE", { pipeline: $scope.currentPipeline } );
                pipes2.editMode = false;
            };


            /* *****
             *    TASK VIEW MANIPULATION OBJECT
             *    Note: currently, this is only useful for tasks with parallel actions
             * *****
             */


            var tasks2 = {};
            $scope.tasks2 = tasks2;


            /** properly reformat custom name
             *  Note that for tasks, if you use the action name for the name parameter
             *  this method properly recognizes it and does the right thing
             */
            tasks2.renderAsCustomName = function(name) {
                // basically, trim off ACTION if it exists, then call renderCustomName
                var renderedTaskName = name.toLocaleUpperCase();
                var actionSubStringIndex = renderedTaskName.lastIndexOf("ACTION");
                if (actionSubStringIndex >= 0) {
                    renderedTaskName = renderedTaskName.substring(0, actionSubStringIndex);
                }
                return renderAsCustomName(renderedTaskName, "TASK");
            };


            // returns true iff this task contains parallel actions
            tasks2.containsParallelActions = function()  {
                console.log("tasks2.containsParallelActions");
                var actions = $scope.currentPipeline.tasks[0].actions;
                var retval = false;
                if ( actions )
                {
                    retval = ( actions.length > 1 )
                }
                console.log(" -> returning " + retval );
                return retval;
            };


            // note that edit mode for tasks is automatically entered
            //  after selecting "add parallel action task" from the dropdown menu
            tasks2.editMode = false;


            /* returns true iff pipeline is in edit mode */
            tasks2.inEditMode = function() {
                return ( tasks2.editMode === true );
            };


            /* sets editing on pipeline */
            tasks2.enterEditMode = function()  {
                tasks2.editMode = true;
            };


            /* sets read-only mode on pipeline */
            tasks2.exitEditMode = function()  {
                tasks2.editMode = false;
            };

            // tasks2.removeActionFromTask = function( task, index ) {
            //     console.log("removeActionFromTask(task---v,"+index+")")
            //     console.log(task);
            // };


            /* *****
             *    ACTION VIEW MANIPULATION OBJECT
             * *****
             */

            var actions2 = {};
            $scope.actions2 = actions2;


            actions2.editMode = false;


            /* returns true iff pipeline is in edit mode */
            actions2.inEditMode = function() {
                return ( actions2.editMode === true );
            };


            /* sets editing on pipeline */
            actions2.enterEditMode = function()  {
                actions2.editMode = true;
            };


            /* sets read-only mode on pipeline */
            actions2.exitEditMode = function()  {
                actions2.editMode = false;
            };


            $scope.userShowAllProperties = false;


            /** properly reformat custom names */
            actions2.renderAsCustomName = function( name ) {
                return renderAsCustomName( name, "ACTION" )
            };


            /* *****
             *    PIPELINE CONTROLLER
             * *****
             */


            $scope.pipelines = [];  // all the pipelines from the server (only name and description are received)
            $scope.tasks = [];  // all tasks from the server
            $scope.actions = [];  // all the actions from the server (only name and description are received)
            $scope.algorithms = []; // all the algorithms from the server

            $scope.currentPipeline = {} ; // the pipeline currently selected
            $scope.currentAction = {} ; // the algorithm currently selected

            /** retrieves and re-renders the actions list */
            var initActionsList = function( selectAction ) {
                $scope.actions = ActionService.query();
                $scope.actions.$promise.then(function () {
                    $scope.actions = orderByFilter( $scope.actions, '+name');
                    if ( selectAction ) {
                        $scope.selectAction( selectAction );
                    }
                    else if ( $scope.actions.length > 0 ) {
                        $scope.selectAction($scope.actions[0]);
                    }
                    actions2.exitEditMode();
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
                        pipes2.exitEditMode();
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
            $scope.init = function () {
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
                return action.viewProperties.filter(function (prop) {
                    return prop.value !== undefined && prop.value !== "";
                });
            };


            /** called when the user selects a pipeline from the pipeline list */
            $scope.selectPipeline = function( item, model ) {
                // console.log("selectPipeline(item---v,model---v)");
                // console.log(item);
                // console.log(model);
                pipes2.exitEditMode();
                $scope.currentPipeline = Pipelines2Service.getCompletePipelineDetails(item.name);
            };


            /** create new action */
            $scope.newAction = function() {
                $scope.currentAction = {};
                actions2.enterEditMode();
            };


            /** sets the algorithmRef for an action */
            $scope.setAlgorithmRef = function( alg ) {
                if ( actions2.inEditMode() ) {
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
                action.name = actions2.renderAsCustomName( action.name );
                saveAction( action )
                    .then( function() {
                        saveTaskFromAction( action );
                    })
            };


            /** saves action and returns the promise from the service */
            var saveAction = function( action ) {

                action.name = actions2.renderAsCustomName( action.name );
                var actionObj = {
                    name: action.name,
                    description: action.description,
                    algorithm: action.algorithmRef,
                    properties: getChangedActionProperties( action )
                };

                var opPromise = ActionService.save( actionObj )
                    .$promise
                    .then( function() {
                        $confirm({
                            title: 'Success',
                            text: '"' + action.name + '" was successfully saved.'});
                        actionObj.name = action.name;   // needed because the REST service expects actionName in the POST, but name in the GET
                        initActionsList( actionObj );
                    })
                    .catch( function( error ) {
                        // todo: P038: should display actual error to user, but need the server to return the error in JSON format;
                        //  currently, it only returns a string
                        $confirm({
                            title: 'Error',
                            text: 'An error occurred when saving the action: ' + error.data.message});
                        console.log("***Error from ActionService.save() :");
                        console.log(error);
                    });
                return opPromise;
            };


            /** saves a single action task from the specified action */
            var saveTaskFromAction = function( action ) {
                var actionName = actions2.renderAsCustomName( action.name );
                var taskObj = {
                    name: tasks2.renderAsCustomName( actionName ),
                    description: action.description,
                    actions: [ actionName ]
                };
                // console.log("taskObj="+JSON.stringify(taskObj));

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
                            text: 'An error occurred when saving the task: ' + error.data.message});
                        console.log("***Error from TaskService.save() :");
                        console.log(error);
                    });
            };


            /** deletes the action from the server */
            $scope.deleteAction = function() {
                var actionName = $scope.currentAction.name;
                var actionPrefix = 'CUSTOM ';
                var actionSuffix = ' ACTION';

                var confirmMessage = 'Are you sure you want to delete ' + actionName;

                var taskName;
                if (actionName.startsWith(actionPrefix) && actionName.endsWith(actionSuffix)) {
                    var nameMiddle = actionName.substring(
                        actionPrefix.length, actionName.length - actionSuffix.length);
                    taskName = 'CUSTOM ' + nameMiddle + ' TASK';
                    confirmMessage += ' and ' + taskName + '? There may be other tasks or pipelines still using them.';
                }
                else {
                    confirmMessage += '? There may be other tasks that are still using it.';
                    taskName = null;
                }
                $confirm({text: confirmMessage})
                    .then(function() {
                        if (taskName) {
                            TaskService.delete(taskName);
                        }
                        return ActionService.delete(actionName).$promise;
                    })
                    .then(function () {
                        initActionsList();
                    });
            };


            /** sets up the viewProperties array for ease of generating the view */
            $scope.setViewProperties = function()  {
                $scope.currentAction.viewProperties = [];
                angular.copy($scope.currentAction.algorithm.providesCollection.properties,
                    $scope.currentAction.viewProperties);
                var index;
                _.each( $scope.currentAction.properties, function( prop )  {
                    index = _.findIndex($scope.currentAction.viewProperties, { "name": prop.name });
                    $scope.currentAction.viewProperties[index].value = prop.value;
                });
            };


            /** selects the actions */
            $scope.selectAction = function( item, model ) {
                actions2.exitEditMode();
                if ( item.name ) {
                    $scope.currentAction = ActionService.get(item.name);
                    $scope.currentAction.$promise.then(function () {
                        $scope.setViewProperties();
                    });
                }
            };


            $scope.toggleShowAllProperties = function()
            {
                $scope.userShowAllProperties = !$scope.userShowAllProperties;
            };


            $scope.init();
            // return p2Ctrl;
        }
    ]);
})();

/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

/* globals angular */

/**
 * A task can be one of 2 things:
 *  1.  a wrapper around a single action (this is the "normal" case)
 *  2.  a wrapper around a list of actions that are meant to performed in parallel
 *      and currently, MUST be the last task in any pipeline
 *
 * Because a task is just a wrapper from the UI's point of view, the UI does not offer it
 * as a separate tab, instead it autogenerates tasks as needed:
 *  * for a single-action task, a user selects an action from the actions list, and
 *      the UI logic generates a task based on the name and description of the selected action
 *  * for a parallel action task, a user adds the parallel actions to a virtual task; because
 *      there is no way to autogenerate the name and description for a parallel task, the UI
 *      offers up edit boxes for the user to enter that information
 *
 *  Task objects are of the form:
 *      name:  name of task, which is always all caps, and represents
 *              both the human readable name as well as UID of the object
 *      description:  description of task
 */

(function () {

    'use strict';

    var module = angular.module('mpf.wfm.pipeline2.task', ['ngResource','simplePipeline']);

    module.factory('TaskService',
        ['$q', '$resource', 'ActionService', 'orderByFilter',
            function ($q, $resource, ActionService, orderByFilter ) {

                var taskResource = $resource('pipeline-tasks/:name');

                var getAction = function (actionName) {
                    return ActionService.get(actionName)
                        .$promise
                        .then(function (actionDetail) {
                            actionDetail._alg = actionDetail.algorithm;
                            return actionDetail;
                        });
                };

                var getTask = function (taskName) {
                    return taskResource.get( {name: taskName} )
                        .$promise
                        .then(function (taskDetail) {

                            var actionPromises = taskDetail.actions
                                .map(function (a) {
                                    return getAction(a.name);
                                });

                            return $q.all(actionPromises)
                                .then(function (actions) {
                                    taskDetail._actions = actions;
                                    return taskDetail;
                                });
                        });
                };

                return {
                    get: function( taskName ) {
                        return getTask( taskName );
                    },
                    query: function () {
                        var list = taskResource.query();
                        list.$promise.then( function() {
                            list =  orderByFilter( list, '+name');
                        });
                        return list;
                    },
                    save: function (dataObj) {
                        //console.log("dataObj="+JSON.stringify(dataObj));
                        return taskResource.save(dataObj);
                    },
                    delete: function ( taskName ) {
                        //console.log("taskName="+JSON.stringify(taskName));
                        return taskResource.delete({name: taskName});
                    }
                }
            }]);

})();

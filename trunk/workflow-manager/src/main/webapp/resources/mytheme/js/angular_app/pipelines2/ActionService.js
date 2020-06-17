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

/* globals angular */

/**
 *
 * Actions used to be called algorithms in the UI, but have since changed.  They
 *  should now be called actions in the client and server code;
 *  They are algorithms with 0 or more overridden parameters, specifically so that tasks
 *  can easily refer to them.
 *
 *  Action objects are of the form:
 *      name:  name of action, which is always all caps, and represents
 *              both the human readable name as well as UID of the object
 *      description:  description of action
 *      algorithmRef:  name of algorithm that this action is derived
 *      properties:  all parameters that overrides the ones in the algorithm
 *          it is a single object made up of key/value pairs, where key is
 *          the name of the algorithm parameter, and value is the overridden value
 *      viewProperties:  helper object contains all algorithm parameters (and their
 *          names, descriptions, default value, optional overridden value and type)
 *
 */

(function () {

    'use strict';

    var module = angular.module('mpf.wfm.pipeline2.action', ['ngResource']);

    module.factory('ActionService',
        ['$resource', 'AlgorithmService', 'orderByFilter',
            function ( $resource, AlgorithmService, orderByFilter ) {

                var actionResource = $resource('actions');

                var setActionsAlgo = function (actionDetails) {
                    return AlgorithmService.get(actionDetails.algorithm)
                        .$promise
                        .then(function (algo) {
                            actionDetails.algorithm = algo;
                            return actionDetails;
                        })
                        .catch(function () {
                            actionDetails.algorithm = {
                                name: actionDetails.algorithm,
                                missing: true
                            };
                            return actionDetails;
                        });
                };

                var getActionWithAlgoInfo = function (actionName) {
                    return actionResource.get({name: actionName})
                        .$promise
                        .then(setActionsAlgo);
                };

                return {
                    get: function (actionName) {
                        if ( actionName ) {
                            var action = new actionResource({
                                $resolved: false,
                                $promise: getActionWithAlgoInfo(actionName)
                                    .then(function (actionData) {
                                        //action.name = actionData.name;
                                        //action.description = actionData.description;
                                        angular.forEach(actionData, function (value, key) {
                                            if (key !== '$resolved' && key !== '$promise') {
                                                action[key] = value;
                                            }
                                        });
                                        action.$resolved = true;
                                        //console.log("action="+JSON.stringify(action));
                                        return action;
                                    })
                            });
                            return action;
                        }
                    },
                    query: function () {
                        var list = actionResource.query();
                        list.$promise.then( function() {
                            list =  orderByFilter( list, '+name');
                            //todo: the REST service needs to return algorithmRef
                            //  so that actions can be searched in the UI
                            //  by algorithm
                            // list[0].algorithmRef="ARBITRARY_ALGORITHM";
                        });
                        return list;
                    },
                    save: function (dataObj) {
                        //console.log("dataObj="+JSON.stringify(dataObj));
                        return actionResource.save(dataObj);
                    },
                    delete: function (actionName) {
                        //console.log("actionName="+JSON.stringify(actionName));
                        return actionResource.delete({name: actionName});
                    }

                };

            }]);
})();
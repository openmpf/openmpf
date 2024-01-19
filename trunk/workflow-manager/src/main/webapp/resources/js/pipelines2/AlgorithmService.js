/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
 * Algorithms are called algorithm base in the UI, but are the same as algorithms in server code;
 *
 *  Algorithms objects are of the form:
 *      actionType: currently, either "MARKUP" or "DETECTION", and refers to the type of algorithm
 *      name:  name of algorithm, which is always all caps, and represents
 *              both the human readable name as well as UID of the object
 *      description:  description of algorithm
 *      providesCollection:  all parameters are stored here under algorithmProperties, which follows the form:
 *          name:  name of property
 *          description:  description of property
 *          defaultValue: default value of property
 *          type: type of property
 */

(function () {

    'use strict';

    var module = angular.module('mpf.wfm.pipeline2.algorithm', ['ngResource']);

    module.factory('AlgorithmService',
        ['$resource',
            function ($resource) {
                var algoResource = $resource('algorithms/:name', {}, {
                    get: {
                        cache: true
                    }
                });

                return {
                    getAll: function () {
                        return algoResource.query();

                    },
                    get: function (name) {
                        return algoResource.get({name: name});
                    }
                };
            }]);

})();

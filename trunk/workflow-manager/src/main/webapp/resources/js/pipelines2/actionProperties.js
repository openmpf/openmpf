/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

(function () {

    'use strict';

    var module = angular.module('mpf.wfm.pipeline2');

    module.directive('actionProperty',
        [
            function () {
                return {
                    restrict: 'E',
                    templateUrl: 'resources/js/pipelines2/actionProperties.tpl.html',
                    scope: {
                        prop: "=",      // the property
                        editMode: "="   // true iff editable
                    },
                    // this is now really simple: if prop has a value then it by definition
                    //  has overwritten prop.defaultValue
                    link: function ($scope, element, attrs) {
                        $scope.hasChanged = function ( prop ) {
                            return ( prop.value );
                        }
                    }
                }
            }
        ]);

})();
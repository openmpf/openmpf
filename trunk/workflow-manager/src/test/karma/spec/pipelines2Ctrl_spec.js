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

describe('Controller: Pipelines2Ctrl', function () {

    // load the controller's module
    beforeEach(module('mpf.wfm'));

    var Pipelines2Ctrl,
        scope;

    // Initialize the controller and a mock scope
    beforeEach(inject(function ($controller, $rootScope) {
        scope = $rootScope.$new();
        Pipelines2Ctrl = $controller('Pipelines2Ctrl', {
            $scope: scope
            // place here mocked dependencies
        });
    }));

    it('should properly initialize $scope variables', function () {
        expect(scope.editMode).toBeDefined();
        expect(scope.editMode).toBeFalsy();

        expect(scope.pipelines).toBeDefined();
        expect(scope.pipelines.length).toBe(0);
        expect(scope.currentPipeline).toBeDefined();
        expect(scope.currentPipeline.keys).toBeUndefined();

        expect(scope.actions).toBeDefined();
        expect(scope.actions.length).toBe(0);
        expect(scope.currentAction).toBeDefined();
        expect(scope.currentAction.keys).toBeUndefined();

        expect(scope.userShowAllProperties).toBeFalsy();
    });

    //xit('should properly select an action when called', function () {
    //    var action = { name: "abc" };
    //    scope.selectAction( action );
    //    expect(scope.currentAction).toBeDefined();
    //    expect(scope.currentAction.keys).toBeDefined();
    //});
});

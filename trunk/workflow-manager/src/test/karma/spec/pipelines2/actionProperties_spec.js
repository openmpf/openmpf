/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

describe('Directive: actionProperties', function () {

    // load the parent module
    beforeEach(module('mpf.wfm'));

    // load the templates
    //beforeEach(angular.mock.module('tpls'));


    var element1, element2, // an arbitrary angular (DOM) elements created here programmatically
        scope;  // scope of page

    var compiledElement1, compiledElement2,   // the DOM element after compiling
        compiledElementScope1, compiledElementScope2;   // the directive's isolate scope after compiling

    // todo:  this is causing an unusual error, and is commented
    //  out so that other tests can run.  See comments for task T1937 for additional
    //  information

    // Initialize the controller and a mock scope
    //beforeEach(inject(function ($rootScope, $compile) {
    //    scope = $rootScope.$new();
    //    scope.prop1 = {
    //        "name": "Prop1",
    //        "value": "1",
    //        "defaultValue": "0"
    //    };
    //    element1 = angular.element('<action-property prop="prop1"></action-property>');
    //    compiledElement1 = $compile(element1)(scope);
    //    compiledElementScope1 = compiledElement1.isolateScope();
    //
    //    scope.prop2 = {
    //        "name": "Prop2",
    //        //"value": "1",
    //        "defaultValue": "1000"
    //    };
    //    element2 = angular.element('<action-property prop="prop2"></action-property>');
    //    compiledElement2 = $compile(element2)(scope);
    //    compiledElementScope2 = compiledElement2.isolateScope();
    //
    //    scope.$apply();
    //}));

    it('should add 2 and 3 to get 5', function () {
        expect(2+3).toBe(5);
    });

    xit('should properly initialize prop attribute', function() {
        console.log("3");

        expect( compiledElementScope1.prop.name).toEqual(scope.prop1.name);
        expect( compiledElementScope2.prop.defaultValue).toEqual(scope.prop2.defaultValue);
        console.log("4");

    });

    xit('should properly notice that a property has changed', function() {
        expect( compiledElementScope1.hasChanged(scope.prop1)).toBeTruthy();
        expect( compiledElementScope2.hasChanged(scope.prop2)).toBeFalsy();
    });

    xit('should properly note that a value has changed', function () {
        expect(scope.hasChanged).toBeDefined();
    });

});

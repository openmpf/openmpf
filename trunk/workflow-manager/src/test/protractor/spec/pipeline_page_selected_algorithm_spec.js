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

var ProtractorUtils = require('./../utils/protractor_utils.js');
var PipelinePage = require('./../pageObjects/pipeline.pageObject.js');

describe('Pipeline Page (after selecting an action in the algorithm tab)', function() {

    const URL = browser.baseUrl + '#/pipelines2';

    // ----- class variables -----
    var utils = new ProtractorUtils();
    var po = new PipelinePage();

    // ----- Helper Functions -----

    beforeAll(function () {
        utils.loginAsAdminUser();
        browser.get(URL);
    });

    beforeEach( function() {
        po.clickAlgorithmTab();
        po.actionList_action_A.click();
    });

    afterAll( function() {
        utils.logout();
    });

    // ----- algorithm tab tests -----

    it('should have some predefined actions', function() {
        utils.evaluateAtScope( po.pageName, 'algorithms.length')
            .then( function( numAlgorithms ) {  // numAlgorithms is the evaluated value
                expect( po.getNumPipelinesAvailable() === numAlgorithms )
            });
    });

    it('should properly render the action name when selected', function() {
        expect( po.getActionName())
            .toBe( po.actionName_A );
    });

    it('should properly render the action description when selected', function() {
        expect( po.getActionDescription())
            .toBe( po.actionDescription_A );
    });

    it('should properly show the algorithm that the action modifies', function() {
        expect( po.getAlgorithmDecorations().get(1).getText() )
            .toBe( po.algorithmName_A );
    });

    it('should properly show help text when hovering over question mark in parameters', function() {
        // verify it doesn't exist before clicking
        expect( po.getPopovers().get(0).isPresent() ).toBeFalsy();
        // hover and verify it's showing
        var element = po.getAlgorithmHelpButtons().get(1);
        utils.hoverOver( element )
            .then( function() {
                var popover = po.getPopovers().get(0);
                expect(popover.isPresent()).toBeTruthy();
                expect(popover.isDisplayed()).toBeTruthy();
                expect( po.getAlgorithmHelpText(popover).getText() )
                    .toContain('MIN_DETECTION_CONFIDENCE: This is the minimum dlib object detection confidence needed to start a new track. [DOUBLE]');
            });
    });

});
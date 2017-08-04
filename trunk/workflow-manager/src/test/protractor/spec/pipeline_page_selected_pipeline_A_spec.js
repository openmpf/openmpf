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

var ProtractorUtils = require('./../utils/protractor_utils.js');
var PipelinePage = require('./../pageObjects/pipeline.pageObject.js');

describe('Pipeline Page after selecting Pipeline A in the pipeline tab (simple pipeline)', function() {

    const URL = browser.baseUrl + '#/pipelines2';

    // ----- class variables -----
    var utils = new ProtractorUtils();
    var po = new PipelinePage();

    // ----- Helper Functions -----

    beforeAll(function () {
        utils.loginAsRegularUser();
        browser.get(URL);
    });

    beforeEach( function() {
        po.clickPipelinesTab();
        po.pipelineList_pipeline_A.click();
//        browser.driver.sleep(250);
    });

    afterAll( function() {
        utils.logout();
    });


    // ----- pipeline tab tests -----

    it('should properly render the pipeline name when selected', function() {
        expect( po.getPipelineName()).toBe( po.pipelineName_A );
    });

    it('should properly render the pipeline description when selected', function() {
        expect( po.getPipelineDescription()).toBe( po.pipelineDescription_A );
    });

    it('should properly render the actions of the simple pipeline', function() {
        var items = po.getTaskSequenceItems();
        expect( items.count() ).toBe( 1 + 2 );  // 1 task plus start and end
        expect( items.first().getText() ).toBe('start');
        expect( items.get(1).getText() ).toBe( po.pipeline_A_Action_1 );
        expect( items.last().getText() ).toBe('end');
    });

    it('should have the proper action definitions', function() {
        var actions = "currentPipeline.vmTasks[0]._actions";
        expect(utils.evaluateAtScope( po.pageName, actions + ".length") )
            .toBe(1);
        expect(utils.evaluateAtScope( po.pageName, actions + "[0].name") )
            .toBe(po.pipeline_A_Action_1);
    });

    it('should have the proper custom properties', function() {
        po.clickAction(1)   // build the popover
            .then( function() {
                expect( po.getNumActionProperties()).toBe( '0' );
            });
    });

    it('should have the proper default properties', function() {
        po.clickAction(1)   // build the popover
            .then( function() {
                expect( po.getNumDefaultProperties() ).toBe( '16' );
                expect( po.getPropertyParamName( 1 ) ).toBe( "MIN_DETECTION_CONFIDENCE" );
                expect( po.getPropertyParamValue( 3 ) ).toBe( "0.1" );
            });
    });

    it('should have the proper algorithm for its action', function() {
        expect( utils.evaluateAtScope( po.pageName, "currentPipeline.vmTasks[0]._actions[0].algorithmRef" ) )
            .toBe( po.pipeline_A_Task_1_AlgorithmName );
    });

    it('should properly render a popover for tasks/actions if clicked', function() {
        // verify it doesn't exist before clicking
        expect( po.getPopovers().get(0).isPresent() ).toBeFalsy();
        // click and verify it's showing
        po.clickAction(1)
            .then( function() {
                var popover = po.getPopovers().get(0);
                expect( popover.isPresent() ).toBeTruthy();
                expect( popover.isDisplayed() ).toBeTruthy();
                // click again to unshow the popover
                po.getTaskSequenceItems().get(1).click();
            });
    });
});
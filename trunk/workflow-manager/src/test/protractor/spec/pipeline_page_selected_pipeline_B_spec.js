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

var ProtractorUtils = require('./../utils/protractor_utils.js');
var PipelinePage = require('./../pageObjects/pipeline.pageObject.js');

describe('Pipeline Page after selecting Pipeline B in the pipeline tab (regular pipeline)', function() {

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
        po.clickPipelinesTab();
        po.pipelineList_pipeline_B.click();
//        browser.driver.sleep(250);
    });

    afterAll( function() {
        utils.logout();
    });


    // ----- pipeline tab tests -----

    it('should properly render the pipeline name when selected', function() {
        expect( po.getPipelineName()).toBe( po.pipelineName_B );
    });

    it('should properly render the pipeline description when selected', function() {
        expect( po.getPipelineDescription()).toBe( po.pipelineDescription_B );
    });

    it('should properly render the actions of the pipeline', function() {
        var items = po.getTaskSequenceItems();
        expect( items.count() ).toBe( 2 + 2 );  // 2 task plus start and end
        expect( items.first().getText() ).toBe('start');
        expect( items.get(1).getText() ).toBe( po.pipeline_B_Action_1 );
        expect( items.get(2).getText() ).toBe( po.pipeline_B_Action_2 );
        expect( items.last().getText() ).toBe('end');
    });

    it('should have the proper action definitions', function() {
        var actions = "currentPipeline.vmTasks[0]._actions";
        expect(utils.evaluateAtScope( po.pageName, actions + ".length"))
            .toBe(1);
        expect(utils.evaluateAtScope( po.pageName, actions + "[0].name"))
            .toBe(po.pipeline_B_Action_1);
    });

    xit('should have the proper custom properties', function() {
        po.clickAction(1)   // build the popover
            .then( function() {
                expect( po.getNumActionProperties()).toBe( '6' );
                expect( po.getPropertyParamName( 1 ) ).toBe( "TARGET_SEGMENT_LENGTH" );
                expect( po.getPropertyParamValue( 2 ) ).toBe( "500" );
            });
        //var actionDetailProperties = "currentPipelineTasks[0]._actions[0].properties";
        //expect( utils.evaluateAtScope( po.pageName, actionDetailProperties+".length" ) )
        //    .toBe( 6 );
        //expect( utils.evaluateAtScope( po.pageName, actionDetailProperties+"[1].name" ) )
        //    .toBe( "TARGET_SEGMENT_LENGTH" );
        //expect( utils.evaluateAtScope( po.pageName, actionDetailProperties+"[1].value" ) )
        //    .toBe( '500' );
    });

    xit('should have the proper action default properties', function() {
        var actionDefaultProperties = "currentPipelineTasks[0].algorithmDetail.providesCollection.algorithmProperties";
        expect( utils.evaluateAtScope( po.pageName, actionDefaultProperties+".length" ) )
            .toBe( 36 );
        expect( utils.evaluateAtScope( po.pageName, actionDefaultProperties+"[1].name" ) )
            .toBe( "MIN_SEGMENT_LENGTH" );
        expect( utils.evaluateAtScope( po.pageName, actionDefaultProperties+"[1].defaultValue" ) )
            .toBe( '20' );
    });

    xit('should have the proper algorithm for its action', function() {
        expect( utils.evaluateAtScope( po.pageName, "currentPipelineTasks[0]._actions[0].algorithmRef" ) )
            .toBe( po.pipeline_B_Task_1_AlgorithmName );
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
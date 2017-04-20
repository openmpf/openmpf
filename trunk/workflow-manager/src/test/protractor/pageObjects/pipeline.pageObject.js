/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

    /* todo Note that there are a lot of things being tested by protractor that
        will be moved to unit testing and the higher level system end-to-end tests.
        Much of the code below and in the specs that use them will be moved and extended.
     */
var PipelinePage = function() {

    // ----- Helper functions -----


    // ----- Constants -----

        // --- Pipeline A (simple, single task pipeline) ---

        this.pipelineName_A = 'DLIB FACE DETECTION PIPELINE';
        this.pipelineDescription_A = 'Performs dlib face detection.';
        this.pipeline_A_Action_1 = 'DLIB FACE DETECTION ACTION';
        this.pipelineList_pipeline_A = element( by.cssContainingText( '.selection-button', this.pipelineName_A ));
        this.pipeline_A_Task_1_AlgorithmName = 'DLIB';

        // --- Pipeline B (normal, 2 task pipeline) ---

        this.pipelineName_B = 'DLIB FACE DETECTION (WITH MOG MOTION PREPROCESSOR) PIPELINE';
        this.pipelineDescription_B = 'Performs MOG motion preprocessing and dlib face detection.';
        this.pipeline_B_Action_1 = 'MOG MOTION DETECTION PREPROCESSOR ACTION';
        this.pipeline_B_Action_2 = 'DLIB FACE DETECTION ACTION';
        this.pipelineList_pipeline_B = element( by.cssContainingText( '.selection-button', this.pipelineName_B ));
        this.pipeline_B_Task_1_AlgorithmName = 'MOG';

        // --- Pipeline P (parallel pipeline) ---

        this.pipelineName_P = 'UNIVERSAL FACE DETECTION PIPELINE';
        this.pipelineDescription_P = 'Performs multiple face detection algorithms.';
        this.pipeline_P_Task_1 = 'UNIVERSAL FACE DETECTION TASK';
        this.pipeline_P_Action_1 = 'OCV FACE DETECTION ACTION';
        this.pipeline_P_Action_2 = 'DLIB FACE DETECTION ACTION';
        this.pipelineList_pipeline_P = element( by.cssContainingText( '.selection-button', this.pipelineName_P ));

        // --- Action A ---

        this.actionName_A = 'DLIB FACE DETECTION ACTION';
        this.actionDescription_A = 'Executes the dlib face detection algorithm using the default parameters.';
        this.actionList_action_A = element( by.cssContainingText( '.selection-button', this.actionName_A ));

        this.algorithmName_A = 'DLIB';

    // ----- Page Objects -----

    this.pageName = element( by.id('pagename') );
    this.tabs = element.all( by.css('.uib-tab') );
    this.pipelineList = element.all( by.repeater('pipeline in pipelines') );
    this.algorithmList = element.all( by.repeater('action in actions' ) );

    // ----- Page Objects accessors -----

        // --- Pipelines ---

            // returns the total number of pipelines displayed in the list
            this.getNumPipelinesAvailable = function() {
                this.pipelineList
                    .count()
                    .then(function(num) {
                        return num
                    });
            };

            this.getPipelineName = function() {
                // NOTE: there is a known bug with WebDriver where getText() for input and textarea
                //  always returns ''; therefore we're using getAttribute('value') in place of getText()
                return element( by.css('input#pipeline-name') ).getAttribute('value');
            };

            this.getPipelineDescription = function() {
                return element( by.id('pipeline-description') ).getAttribute('value');
            };

            // returns all the items (including 'start' and 'end')
            this.getTaskSequenceItems = function() {
                return element.all( by.css('.pipelineActionContent') );
            };

            // returns all popovers (they only appear after clicking or hovering)
            this.getPopovers = function() {
                return element.all( by.css(".popover") );
            };

        // --- Actions (in UI, these are called algorithms) ---

            this.getActionName = function() {
                return element( by.css('#action-name') ).getAttribute('value');
            };

            this.getActionDescription = function() {
                return element( by.id('action-description') ).getAttribute('value');
            };

            this.getNumActionProperties = function() {
                return element.all( by.className('pp-popover-param-value')).get(0).getText();
            };

            this.getNumDefaultProperties = function() {
                return element.all( by.className('pp-popover-param-value')).get(1).getText();
            };

            this.getPropertyParamName = function( which ) {
                return element.all( by.className('pp-popover-param-name')).get( which ).getText();
            };

            this.getPropertyParamValue = function( which ) {
                return element.all( by.className('pp-popover-param-value')).get( which ).getText();
            };

            this.getAlgorithmDecorations = function () {
                return element.all( by.className('pp-algorithm-decoration') );
            };

            this.getAlgorithmHelpButtons = function() {
                return element.all( by.className('pp-information-icon') );
            };

            this.getAlgorithmHelpText = function( popover ) {
                return popover.element( by.className('popover-content') );
            };

    // ----- Page Actions -----

    this.clickPipelinesTab = function() {
        this.tabs.get(0).click();
    }

    this.clickAlgorithmTab = function() {
        this.tabs.get(1).click();
    }

    this.clickAction = function( which ) {
        return this.getTaskSequenceItems().get( which ).click()
    }

};
module.exports = PipelinePage;

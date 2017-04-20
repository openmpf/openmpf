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

var ProtractorUtils = require('./../utils/protractor_utils.js');
var PipelinePage = require('./../pageObjects/pipeline.pageObject.js');

describe('Pipeline Page---testing general elements not in tabs', function() {

    const URL = browser.baseUrl + '#/pipelines2';

    // ----- class variables -----
    var utils = new ProtractorUtils();
    var po = new PipelinePage();

    // ----- Helper Functions -----

    beforeAll(function () {
        utils.loginAsRegularUser();
        browser.get(URL);
    });

    afterAll( function() {
        utils.logout();
    });

    // ----- page tests -----

    it('should go to the correct page', function () {
        expect(browser.getCurrentUrl()).toBe(URL);
        expect(po.pageName.getText()).toBe('Pipelines');
    });

    it('should have fetched pipeline defintions from server', function() {
        expect( po.getNumPipelinesAvailable() > 0 );  // done different way (without toBeXXX which resolves promises)
    });

    it('should have fetched action definitions from server', function() {
        expect ( utils.evaluateAtScope( po.pageName, 'actions.length') )
            .toBeGreaterThan( 0 );
    });

    it('should have some predefined pipelines', function() {
        expect ( utils.evaluateAtScope( po.pageName, "pipelines.length" ) )
            .toBe( po.pipelineList.count() );
    });


});
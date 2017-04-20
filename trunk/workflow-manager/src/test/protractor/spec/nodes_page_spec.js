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
var NodesPage = require('./../pageObjects/nodes.pageObject.js');

describe('Nodes Page', function () {

    // ----- class variables -----
    var utils = new ProtractorUtils();
    var po = new NodesPage();
    var EC = protractor.ExpectedConditions;

    // ----- Helper Functions -----

    beforeAll(function () {
        utils.loginAsAdminUser();
        browser.get(po.URL);
        browser.driver.sleep(1000);
    });

    afterAll(function () {
        utils.logout();
    });

    // ----- tests -----
    it('is accessible', function () {
        expect(browser.driver.getCurrentUrl()).toBe(po.URL);
    });

    it('should go to the correct page and check heading', function () {
        expect(browser.getCurrentUrl()).toBe(po.URL);
        expect(element(by.css('.page-header h3')).getText()).toBe(po.pageName);
    });

    it('should have at least one node', function () {
        expect(po.getNumNodesAvailable() > 0);
    });

    it('check there is a service list', function () {
        console.log("get current list ");
        //open all the collapsed elements
        browser.executeScript("$('.collapse').addClass('in');").then(function () {
            var service_items = po.getAllServicesList();
            expect(service_items > 0);
        });
    });

    it('stop a node then start it', function () {
        browser.ignoreSynchronization = true;
        console.log("get current list and find first running service");
        //open all the collapsed elements
        browser.executeScript("$('.collapse').addClass('in');").then(function () {
            var node_service = po.getFirstServiceWithStatus('service-success');
            expect(node_service.isPresent()).toBeTruthy();
            node_service.getAttribute('id').then(function (value) {
                console.log("found service id:" + value);
                po.serviceId = value;
                console.log("stopping node id = #" + po.serviceId);
                po.pressStopBtn();
                console.log("waiting for stop");

                po.waitForDisabledChange('stop').then(function () {
                    expect($("#" + po.serviceId + " .btn-service-start").getAttribute('disabled')).toBe(null);
                    console.log("starting node id = #" + po.serviceId);
                    po.pressStartBtn();
                    po.waitForDisabledChange('start').then(function () {
                        console.log("completed");
                        browser.ignoreSynchronization = false;
                    });
                });
            });
        });
    });
});
//protractor protractor.conf.js --specs='spec/nodes_page_spec.js'
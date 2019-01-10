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
"use strict";

var ProtractorUtils = require('./../utils/protractor_utils.js');
var JobsPage = require('./../pageObjects/jobs.pageObject.js');
var ServerMediaPage = require('./../pageObjects/server_media.pageObject.js');

describe("Jobs Page", function () {

    jasmine.DEFAULT_TIMEOUT_INTERVAL = 1200000;//20 min
    // ----- class variables -----
    var utils = new ProtractorUtils();
    var po = new JobsPage();
    var serverMedia = new ServerMediaPage();

    console.log("JobsPage Test:" + po.URL);

    // ----- Helper Functions -----

    beforeAll(function () {
        utils.loginAsRegularUser();
        browser.get(po.URL);
    });

    beforeEach(function () {
        browser.get(po.URL);

        browser.driver.getCurrentUrl().then(function (url) {
            console.log("Reset url:" + url);
        });
    });

    afterAll(function () {
        utils.logout();
    });

    // ----- tests -----

    it('should go to the correct page and check heading', function () {
        expect(browser.getCurrentUrl()).toBe(po.URL);
        expect(element(by.css('.page-header h3')).getText()).toBe(po.pageName);
    });

    it('is accessible', function () {
        console.log("URL Test");
        expect(browser.driver.getCurrentUrl()).toBe(po.URL);
    });

    it('can CreateJobFromUrl Cancel and Resubmit', function () {
        console.log("Create a Job");

        console.log("-Resetting to url:" + serverMedia.URL);
        browser.get(serverMedia.URL);
        browser.driver.sleep(2000);
        console.log("-Upload some media:"+po.video_uri);
        serverMedia.uploadMedia(po.video_uri).then(function () {
            console.log("-Select the first media");
            serverMedia.selectFirstMedia();
            browser.driver.sleep(1000);
            console.log("-Trying to select pipeline: " + serverMedia.TEST_PIPELINE_LONG_NAME);
            utils.selectDropdownByText(by.id("jobPipelineSelectServer"), serverMedia.TEST_PIPELINE_LONG_NAME);
            console.log("-submit job");
            element(by.id("btn-submit-checked")).click();
            browser.driver.sleep(3000);

            console.log("Cancel and Resubmit the job");
            console.log("-should be on jobs page");
            expect(browser.driver.getCurrentUrl()).toBe(po.URL);

            browser.driver.sleep(3000);//wait for it to load
            po.getFirstJobRowId().then(function (jobId) {
                console.log("Found jobId:" + jobId);
                po.handleNotification();
                browser.driver.sleep(500);
                console.log("-waiting for IN_PROGRESS");
                po.waitForJobStatus(jobId, "IN_PROGRESS").then(function () {
                    console.log("-status changed to IN PROGRESS");
                    console.log("*clicking cancel");
                    po.handleNotification();
                    browser.driver.sleep(500);
                    po.pressCancelJobBtn(jobId);
                    po.waitForJobStatus(jobId, "CANCELLED").then(function () {
                        console.log("-status changed to CANCELLED");
                        browser.driver.sleep(500);//wait for resubmit to be enabled
                        po.handleNotification();
                        browser.driver.sleep(500);
                        po.pressResubmitJobBtn(jobId);
                        po.waitForJobStatus(jobId, "IN_PROGRESS").then(function () {
                            console.log("-status changed to IN_PROGRESS");
                            po.handleNotification();
                            browser.driver.sleep(500);
                            console.log("-waiting for COMPLETE");
                            po.waitForJobStatus(jobId, "COMPLETE").then(function () {
                                console.log("-status changed to COMPLETE");
                                po.handleNotification();
                                console.log("done");
                            });
                        });
                    });
                });
            });
        });
    });

});
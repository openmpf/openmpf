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

var JobsPage = function() {

    // ----- Constants -----
    this.URL = browser.baseUrl + '#/jobs';

    // ----- Page Objects -----

    this.pageName = "Job Status";
    this.currentJob = null;
    this.video_uri = "https://raw.githubusercontent.com/openmpf/openmpf/master/trunk/mpf-system-tests/src/test/resources/samples/face/new_face_video.avi";

    this.getFirstJobRow = function(){
        expect(browser.driver.getCurrentUrl()).toBe(this.URL);
        return $$('#jobTable tbody tr').first();
    };

    this.getFirstJobRowId = function(){
        expect(browser.driver.getCurrentUrl()).toBe(this.URL);
        return $$('#jobTable tbody tr').$$('td').get(0).getText().then(function(job_id) {
            return job_id;
        });
    };

    this.findJobRow = function(jobId){
        expect(browser.driver.getCurrentUrl()).toBe(this.URL);

        return $$('#jobTable tbody tr').filter(function(elem, index) {
            return elem.$$('td').get(0).getText().then(function(text) {
                return text === jobId+"";
            });
        }).each(function(row){
            return row;
        });
    };

    this.getJobStatus = function(jobId){
        return element(by.id("jobStatusCell"+jobId)).getText().then(function(status){
            return status;
        });
    };
    this.waitForJobStatus = function(jobId,status){
        console.log("waitForJobStatus "+status);
        return browser.wait(function(){
            return element(by.id("jobStatusCell"+jobId)).getText().then(function(cur_status){
                browser.sleep(1000);
                return status === cur_status;
            });
        },1200000);//can take a while
    };

    // ----- Buttons -----
    this.pressCancelJobBtn = function(jobId){
        console.log("*clicking cancelBtn" + jobId);
        element(by.id("cancelBtn" + jobId)).click();
        browser.sleep(500);
    };
    this.pressResubmitJobBtn = function(jobId) {
        console.log("*clicking resubmitBtn" + jobId);
        element(by.id("resubmitBtn" + jobId)).click();
        browser.sleep(500);
    };

    //click the Close All button of any notifications that popup and could block future action clicks
    this.handleNotification = function(){
        console.log("handleNotification popups");
        element(by.css(".noty_buttons .btn")).isPresent().then(function(result) {//isPresent
            if ( result ) {
                element.all(by.css(".noty_buttons .btn")).last().click();//click the Close All button
                browser.sleep(500);
            }
        });
    }
};
module.exports = JobsPage;
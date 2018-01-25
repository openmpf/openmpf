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
var ServerMediaPage = require('./../pageObjects/server_media.pageObject.js');

describe("Server Media Page", function () {

    // ----- class variables -----
    var utils = new ProtractorUtils();
    var po = new ServerMediaPage();
    var EC = protractor.ExpectedConditions;

    console.log("ServerMediaPage Test:" + po.URL);

    // ----- Helper Functions -----
    beforeAll(function () {
        utils.loginAsRegularUser();
        browser.get(po.URL);
    });

    beforeEach(function () {
        console.log("Resetting to url:" + po.URL);
        browser.get(po.URL);

        browser.driver.getCurrentUrl().then(function (url) {
            console.log("Reset url:" + url);
        });
    });

    // ----- tests -----

    it('is accessible', function () {
        expect(browser.driver.getCurrentUrl()).toBe(po.URL);
    });

    it('should go to the correct page and check heading', function () {
        expect(browser.getCurrentUrl()).toBe(po.URL);
        expect(element(by.css('.page-header h3')).getText()).toBe(po.pageName);
    });

    it('upload media', function () {
        console.log("URL Test");
        expect(browser.driver.getCurrentUrl()).toBe(po.URL);

        //click on the remote-media directory and wait for it
        console.log("click on the remote-media directory");
        var remotemedia = element.all(by.css('.list-group-item')).last();
        expect(remotemedia.getText()).toBe('remote-media');
        remotemedia.click();


        //click upload
        console.log("click:btn-upload")
        element(by.css(".btn-upload")).click();
        //browser.driver.sleep(2000);

        var media_url = browser.baseUrl + "resources/img/blue-cybernetic-background.jpg";
        console.log("entering media :" + media_url);
        element(by.id('URLsInput')).sendKeys(media_url);//insert a img url
        //browser.driver.sleep(2000);

        console.log("clicking submit");
        element(by.id("submitURLs")).click();

        console.log("clicking close");
        element(by.id("cancelURLUpload")).click();

        browser.wait(function () {
            console.log("Waiting for file upload");
            return element(by.id('file_list_server')).all(by.css('tr')).last().all(by.css('td')).get(2).getText().then(function (text) {
                return text.indexOf('workflow-manager-resources-img-blue-cybernetic-background') > -1;
            });
        }, 5000, 'file Upload wait').then(function () {
            console.log("selecting file");
            //click the checkbox of first item
            element.all(by.css('.node-check')).first().click();
            console.log('checked item');

            browser.driver.sleep(2000);
            expect(element(by.id('selectedFilesCount')).getText()).toEqual('1');

            console.log('Clicking view files');
            element(by.id('viewFilesBtn')).click();

            console.log("Wait on Selected File modal");

            var el = element(by.id('selected_file_list_wrapper'));
            browser.wait(EC.presenceOf(el), 3000, 'No modal popup').then(function () {
                console.log("Selected Files Modal up");

                browser.driver.sleep(2000);
                console.log("click properties on first");
                el.all(by.css('.set-properties-btn')).first().click();

                browser.driver.sleep(2000);
                console.log("Wait on properties modal");
                var el2 = element(by.id('AUTO_ROTATE'));
                browser.wait(EC.presenceOf(el2), 3000, 'No modal popup').then(function () {
                    console.log("Properties Modal available");
                    element(by.id('AUTO_ROTATE')).click();
                    element(by.id('AUTO_FLIP')).click();
                    element(by.id('submitTransforms')).click();
                    browser.driver.sleep(2000);
                    //verify
                    console.log("click properties on first again");
                    el.all(by.css('.set-properties-btn')).first().click();
                    browser.driver.sleep(2000);
                    console.log("Wait on properties modal");
                    var el2 = element(by.id('AUTO_ROTATE'));
                    browser.wait(EC.presenceOf(el2), 3000, 'No modal popup').then(function () {
                        console.log("Verifying items checked");
                        expect(element(by.id('AUTO_ROTATE')).isSelected()).toBeTruthy();
                        expect(element(by.id('AUTO_FLIP')).isSelected()).toBeTruthy();
                    });
                });
            });
        });
    });
});
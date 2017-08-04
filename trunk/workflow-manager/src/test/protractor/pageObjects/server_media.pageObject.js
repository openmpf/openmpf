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

var ServerMediaPage = function() {

    // ----- Constants -----
    this.URL = browser.baseUrl + '#/server_media';
    this.TEST_PIPELINE_NAME = "OCV FACE DETECTION PIPELINE";
    this.TEST_PIPELINE_LONG_NAME = "OCV PERSON DETECTION (WITH MOTION PREPROCESSOR) PIPELINE";
    this.pageName = "Create Job";

    // ----- Page Objects -----
    this.uploadMedia = function(media_url){
        var media_url = media_url || browser.baseUrl +"resources/img/blue-cybernetic-background.jpg";
        console.log("media_url",media_url);

        //click on the remote-media directory and wait for it
        console.log("click on the remote-media directory");
        var remotemedia = element.all(by.css('.list-group-item')).last();
        expect(remotemedia.getText()).toBe('remote-media');
        remotemedia.click();
        browser.driver.sleep(2000);

        //click upload
        console.log("click:btn-upload")
        element(by.css(".btn-upload")).click();
        browser.driver.sleep(2000);

        console.log("entering media :" + media_url);
        element(by.id('URLsInput')).sendKeys(media_url);//insert a img url
        browser.driver.sleep(2000);

        console.log("clicking submit");
        element(by.id("submitURLs")).click();

        //console.log("clicking close");
        //element(by.id("cancelURLUpload")).click();
        console.log("Waiting for file upload");
        browser.ignoreSynchronization = true;
        return browser.wait(function() {
            return element(by.css('.localName p')).isPresent();
        },60000,'file Upload wait').then(function(){
            element(by.css('.localName p')).getText().then(function(text){
                var isValid = (text.replace("Uploaded : ","").indexOf(media_url) > -1);
                expect(isValid);
                console.log("*cancelURLUpload click");
                element(by.id("cancelURLUpload")).click();
                browser.driver.sleep(1000);
                browser.ignoreSynchronization =false;
                console.log("uploadMedia complete");
            });
        });
    };

    this.selectFirstMedia = function (){
        //click on the remote-media directory and wait for it
        console.log("click on the remote-media directory");
        var remotemedia = element.all(by.css('.list-group-item')).last();
        expect(remotemedia.getText()).toBe('remote-media');
        remotemedia.click();
        browser.driver.sleep(1000);

        console.log("selecting file");
        //click the checkbox of first item
        var els = element.all(by.css('.node-check')).first().click();
        console.log('checked item');

        browser.driver.sleep(1000);
        expect(element(by.id('selectedFilesCount')).getText()).toEqual('1');

    }
};
module.exports = ServerMediaPage;
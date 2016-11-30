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

var env = require('./environment.js');

// conf.js
exports.config = {
    framework: 'jasmine',
    //seleniumAddress: env.seleniumAddress,  without, it will start its own local server http://192.168.196.157:59504/wd/hub
    specs: [
        'spec/**/*_spec.js'
    ],

    capabilities: env.capabilities,

    baseUrl: env.baseUrl + '/workflow-manager/',

    plugins: [],

    params: {
        login: {
            user: 'mpf',
            password: 'mpf123'
        }
    },

    onPrepare: function() {
        console.log("Base URL:"+browser.baseUrl);
        //console.log("Protractor environment:",process.env);

        // ----- set browser size to "minimum desktop" that we're supporting -----
        browser.driver.manage().window().setSize(1280, 800);

        // ----- login to workflow-manager as a user would -----
        //  code based on protractor sample code: https://github.com/angular/protractor/blob/master/spec/withLoginConf.js
        //  recommended in the protractor FAQ:  https://github.com/angular/protractor/blob/master/docs/faq.md
        browser.driver.get(browser.baseUrl);
        browser.driver.findElement(by.name('username')).sendKeys(browser.params.login.user);
        browser.driver.findElement(by.name('password')).sendKeys(browser.params.login.password);
        browser.driver.findElement(by.name('submit')).click();

        console.log("Login complete");
        return;

    }
};
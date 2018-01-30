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

var env = require('./environment.js');

exports.config = {
    framework: 'jasmine',
    specs: [
        'spec/**/*_spec.js'
    ],

    capabilities: env.capabilities,

    baseUrl: env.baseUrl + '/workflow-manager/',

    plugins: [],

    params: {
        login: {
            mpf: {
                username: 'mpf',
                password: 'mpf123'
            },
            admin: {
                username: 'admin',
                password: 'mpfadm'
            }
        }
    },

    onPrepare: function() {
        // ----- set up plug-ins -----

        // --- set up jasmine spec reporter for better output ---
        var SpecReporter = require('jasmine-spec-reporter');
        jasmine.getEnv().addReporter(new SpecReporter({
            displayStacktrace: 'all'
        }));

        // ----- set up end-to-end test -----
        console.log("Base URL:"+browser.baseUrl);
        console.log("Protractor environment:",process.env);

        // --- set browser size to "minimum desktop" that we're supporting ---
        browser.driver.manage().window().setSize(1280, 800);

        // --- login to workflow-manager as a user would ---
        //  code based on protractor sample code: https://github.com/angular/protractor/blob/master/spec/withLoginConf.js
        //  recommended in the protractor FAQ:  https://github.com/angular/protractor/blob/master/docs/faq.md

        browser.ignoreSynchronization = true;   // this seems to improve false positives a little when doing non-angular pages
        //browser.debugger();
        browser.driver.get(browser.baseUrl)
        browser.driver.findElement(by.name('username')).sendKeys(browser.params.login.mpf.username);
        browser.driver.findElement(by.name('password')).sendKeys(browser.params.login.mpf.password);
        browser.driver.findElement(by.name('submit')).click();

        //console.log("Login complete");

        browser.ignoreSynchronization = false;  // set it back for angular pages
        return;
    },

    jasmineNodeOpts: {
        print: function() {}  // disable protractor's default reporter in favor of jasmine-spec-reporter
    }
};

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

// Common configuration files with defaults plus overrides from environment vars
var webServerDefaultPort = 8080;

module.exports = {
    // The address of a running selenium server.
    //seleniumAddress:(process.env.SELENIUM_URL || 'http://localhost:4444/wd/hub'),

    // Capabilities to be passed to the webdriver instance.
    capabilities: {
        'browserName':
            (process.env.TEST_BROWSER_NAME || 'firefox'),
        'version':
            (process.env.TEST_BROWSER_VERSION || 'ANY')
    },

    // Default http port to host the web server
    webServerDefaultPort: webServerDefaultPort,

    // Protractor interactive tests
    interactiveTestPort: 6969,

    // A base URL for your application under test.
    baseUrl:(process.env.TOMCAT_BASE_URL || 'http://' + (process.env.HTTP_HOST || 'localhost') +
    ':' + (process.env.HTTP_PORT || webServerDefaultPort))

};
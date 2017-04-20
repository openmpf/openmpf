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


// Karma configuration

module.exports = function (config) {

    // current working directory is the location of karma.conf.js
    // setup vars to simplify file specifications later
    var _jsRootPath = '../main/webapp/resources/mytheme/',
        _jsPath = _jsRootPath + 'js/',
        _jsLibPath = _jsRootPath + 'js/lib/',
        _jsAngularPath = _jsRootPath + 'js/lib/angular/',
        _jsUiLibPath = _jsRootPath + 'ui-plugins/';

    config.set({

        // base path that will be used to resolve all patterns (eg. files, exclude)
        basePath: '..',  // should put us at the workflow-manager/src level

        // frameworks to use
        // available frameworks: https://npmjs.org/browse/keyword/karma-adapter
        frameworks: ['jasmine'],

        // list of files / patterns to load in the browser
        files: [
            _jsPath + 'jquery-1.11.1.js',   // todo: hardcoded version
            _jsAngularPath + 'angular.js',
            'devlibs/angular-mocks.js',
            _jsAngularPath + '*.min.js',
            _jsLibPath + 'angular-confirm.js',
            _jsLibPath + '*.js',
            _jsUiLibPath + 'moment/moment.js',
            _jsPath + 'ng-sortable.js',
            _jsPath + 'angular_app/*.js',
            _jsPath + 'angular_app/pipelines2/*.js',
            _jsPath + 'directives/**/*.js',
            _jsPath + 'controllers/**/*.js',
            'karma/**/*_spec.js'    // all the unit tests
        ],

        // list of files to exclude
        exclude: [],

        // preprocess matching files before serving them to the browser
        // available preprocessors: https://npmjs.org/browse/keyword/karma-preprocessor
        preprocessors: {},

        // test results reporter to use
        // possible values: 'dots', 'progress'
        // available reporters: https://npmjs.org/browse/keyword/karma-reporter
        reporters: ['progress'],

        // web server port
        port: 9876,

        // enable / disable colors in the output (reporters and logs)
        colors: true,

        // level of logging
        // possible values: config.LOG_DISABLE || config.LOG_ERROR || config.LOG_WARN || config.LOG_INFO || config.LOG_DEBUG
        logLevel: config.LOG_INFO,

        // enable / disable watching file and executing tests whenever any file changes
        autoWatch: true,

        // start these browsers
        // available browser launchers: https://npmjs.org/browse/keyword/karma-launcher
	
	// the config is currently setup to run the minimal Firefox browser;
	// if you have Chrome installed, uncomment the next line and comment out the following
        // browsers: ['Firefox', 'Chrome'],
        browsers: ['Firefox'],

        // Continuous Integration mode
        // if true, Karma captures browsers, runs the tests and exits
        singleRun: false,

        // Concurrency level
        // how many browser should be started simultaneous
        concurrency: Infinity
    })
}

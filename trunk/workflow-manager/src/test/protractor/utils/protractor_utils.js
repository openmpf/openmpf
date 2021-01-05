/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

/* todo Note that most of this code is commented out
 because it fails about 10% of the time due to timing issues.  Disabled
 the ability of each page to login as different users
 for now by commenting out the relevant code, but keeping
 in here as comments because a fix for this is expected soon that may leverage
 some of this code.
 */
var ProtractorUtils = function () {

    // remembers the logged in username;
    // if null, then we're not logged in
    this.username = null;

    // ----- Login/Logout functions -----

    // logs in as user + pw
    //  this is a null op until we can figure out the timing problem
    //  with login
    this.login = function (user, pw) {
        this.logout();

        browser.ignoreSynchronization = true;   // this seems to improve false positives a little when doing non-angular pages
        //browser.debugger();
        browser.driver.get(browser.baseUrl)
        browser.driver.findElement(by.name('username')).sendKeys(user);
        browser.driver.findElement(by.name('password')).sendKeys(pw);
        browser.driver.findElement(by.name('submit')).click();

        console.log("Login complete");

        browser.ignoreSynchronization = false;  // set it back for angular pages
        // note that in login functions, there is no angular and no wfmFrame so need to use browser.xxx methods
        //browser.driver.get( browser.baseUrl );

        //browser.driver.wait(function() {
        //    browser.driver.findElements(by.id('username'))
        //        .then(function(elements) {
        //            browser.driver.findElement(by.id('username')).sendKeys(user);
        //            browser.driver.findElement(by.id('password')).sendKeys(pw);
        //            browser.driver.findElement(by.id('submit')).click();
        //            this.username = user;
        //            console.log( "logged in as " + this.username );
        //    });
        //}, 1000, 'Failed to find element after 1 second');

        //browser.driver.wait( until.elementLocated(By.id('username')), 5 * 1000)
        //    .then(function(elm) {
        //        browser.driver.findElement(by.id('username')).sendKeys(user);
        //        browser.driver.findElement(by.id('password')).sendKeys(pw);
        //        browser.driver.findElement(by.id('submit')).click();
        //        this.username = user;
        //        console.log( "logged in as " + this.username );
        //});
    };

    // logs in as regular user
    //  this is a null op until we can figure out the timing problem
    //  with login
    this.loginAsRegularUser = function () {
        this.login(
            browser.params.login.mpf.username,
            browser.params.login.mpf.password );
    };

    // logs in as regular user
    //  this is a null op until we can figure out the timing problem
    //  with login
    this.loginAsAdminUser = function () {
        this.login(
            browser.params.login.admin.username,
            browser.params.login.admin.password );
    };

    // log out
    //  this is a null op until we can figure out the timing problem
    //  with login
    this.logout = function () {
        browser.driver.get( browser.baseUrl + 'login?logout' ).then( function() {
            this.username = null;
        });
    };

    // ----- Helper functions -----

    // returns a promise containing the value of the evaluated expression at the scope of elem
    // to use the value, use a .then( function( v ) {} ) where v is the value
    // or directly with a .toBe(xxx)
    this.evaluateAtScope = function (elem, exp) {
        return elem.evaluate(exp);
    };

    // performs a hover on top of elem, returning a promise
    // usage:
    //  var elem = element( by.css( ... ) );
    //  utils.hoverOver( elem )
    //      .then( function() ...
    this.hoverOver = function (elem) {
        return browser.actions().mouseMove(elem).perform();
    };

    //var selector = by.id("idofitem");
    this.selectDropdownByText = function (selector, desired_text) {
        console.log("selectDropdownByText: selector:"+selector +" desired_test:"+desired_text);
        element(selector).click();
        browser.driver.sleep(1000);
        element(selector).all(by.css('.option')).filter(function (elem) {
            return elem.getText().then(function (text) {
                return text === desired_text;
            });
        }).first().click();
        console.log("done select");
    }

};
module.exports = ProtractorUtils;

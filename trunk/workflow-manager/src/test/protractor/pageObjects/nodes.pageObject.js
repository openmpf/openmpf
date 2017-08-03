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

var NodesPage = function () {

    // ----- Constants -----
    this.URL = browser.baseUrl + '#/adminNodes';

    // ----- Page Objects -----
    this.pageName = "Node Configuration";
    this.serviceId = -1;

    //this.nodeHostList = element.all(by.css('.node-host'));

    this.getNodeList = function () {
        var ele = '.node-panels';
        console.log("getNodeList:"+ele);
        return $$(ele);//element.all(by.css());
        //return element.all(by.css('.node-panels'));
    };

    this.getNumNodesAvailable = function () {
        console.log("getNumNodesAvailable");
        this.getNodeList()
            .count()
            .then(function (num) {
                return num
            });
    };
    this.getAllServicesList = function () {
        var ele = '.node-service-list';
        console.log("getAllServicesList:"+ele);
        return $$(ele);//element.all(by.css());
    };

    this.getFirstServiceWithStatus = function (desired_status) {
        var ele = '.' + desired_status + ':not(.ng-hide) ';
        console.log("getFirstServiceWithStatus: "+ele);
        var items = $$(ele);//find those that are running
        return items.first().element(by.xpath('..'));//.element(by.xpath('..')).element(by.xpath('..'));//return parent
    };

    // ----- Buttons -----
    this.pressStopBtn = function () {
        var ele = "#" + this.serviceId + " .btn-service-stop";
        console.log("*clicking stopBtn: " + ele);
        $(ele).click();
    };
    this.pressStartBtn = function () {
        var ele = "#" + this.serviceId + " .btn-service-start";
        console.log("*clicking startBtn: " + ele);
        $(ele).click();
    };

    this.waitForDisabledChange = function(type){
        var ele = "#" + this.serviceId + " .btn-service-"+type;
        return browser.wait(function(){
            return $(ele).getAttribute('disabled').then(function(val){
                browser.sleep(1000);
                console.log("waiting for disabled:"+val+ " "+ele);
                return val;
            });
        },5000);
    }
};
module.exports = NodesPage;
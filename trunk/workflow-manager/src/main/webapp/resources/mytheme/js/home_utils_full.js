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

var HomeUtilsFull = new function () {     	
    
	//globals
	this.roleInfo = undefined;
	this.displayVersion = undefined;
	this.isAdmin = false;
	//this.timedout = false;
	//good solution until a service (singleton in AngularJS) can be built
	this.dashboardInitialized = false;
	
	this.sessionTimeout = function() {
		window.top.location.href = 'login?timeout';
	};
	
	this.sessionBootout = function() { 
		window.top.location.href = 'login?bootout';
	};
	
	// For handling errors, sometimes methods that throw exceptions return a ModelAndView object, other times
	// they return a JsonObject (this handles both)
	this.processResponse  = function(response, htmlDivName, isJsonObject) {
	    if (isJsonObject) {
	        $(htmlDivName).html(response.responseText); // this works to load error.jsp but loses url & exception
	    } else {
	        $(htmlDivName).html(response);
	    }
	};
	
	//annoying that after state changes there is nothing resetting the scroll position
	//this function does that and should be called on all state changes
	this.scrollToTop = function() {
		$("html, body").animate({ scrollTop: 0 }, "fast");
	};
}; //end of HomeUtilsFull

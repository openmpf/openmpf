/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

/***
 * This singleton class will handle common elements used by the javascript
 * 
 * Usage: Utils.debug("[<put your class or page here]<message>");
 * 		  Utils.debug("[<put your class or page here]<message>",<optional object>);
 * Can also do Utils.warn and Utils.error
 * 
 * to show debug you need to add to url ?debug=true        since there may be lots of debug messages
 * e.g. //http://localhost:8080/workflow-manager/?debug=true#/adminNodesAndProcesses
 */
var Utils = new function (){ //singleton for page 
	
	
	this.DEBUG_LEVEL = true;
	this.WARN_LEVEL = true;
	this.ERROR_LEVEL = true;
	
	this.debug = function(msg,obj){
		var param = this.getParameterByName("debug");//http://localhost:8080/workflow-manager/?debug=true#/adminNodes
		if(this.DEBUG_LEVEL && param){
			if(console)console.log(" DEBUG:"+msg,obj);
			$("#log_msgs").append("<br/>"+msg);
		}
	}
	this.warn = function(msg,obj){
		if(this.WARN_LEVEL){
			if(console)console.log("WARN:"+msg,obj);
			$("#log_msgs").append("<br/>"+msg);
		}
	}
	this.error = function(msg,obj){
		if(this.ERROR_LEVEL){
			if(console)console.log("ERROR:"+msg,obj);
			$("#log_msgs").append("<br/><span style='color:#F00;'>"+msg+"</span>");
		}
	}
	
	this.getParameterByName = function (name) {
	    name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
	    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
	        results = regex.exec(location.search);
	    return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g, " "));
	}
	
	//removes special character for handleSpecialCharsuery selectors
 	this.handleSpecialChars = function(id) {    	 
    	return id.replace(/[\W]/g,'_');
	}
 	this.time_format = 'h:mm:ss a';
    
 	this.getTime = function(){
    	return moment().format(Utils.time_format);
    }
 	
 	this.ajaxServiceCall = function(url,callback,error_funct){
 		Utils.debug("AJAX Call to: "+url);
 		error_funct = error_funct || function(jqXHR , textStatus, err){
 			Utils.error(err,jqXHR);
 		}
 		$.ajax({
    		  url: url,
    		  data: "",
    		  success: function(data,status,jqXHR){
    			  Utils.debug("AJAX  "+status,data);
    			  if(callback)callback(data,status,jqXHR);
    		  },
    		  error:error_funct,
    		  dataType: ''
    		});
 	}
}

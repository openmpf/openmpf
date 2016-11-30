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

'use strict';

/* Angular Filters */
var AppFilters = angular.module('WfmAngularSpringApp.filters', []);

AppFilters.filter('interpolate', ['version', function (version) {
	return function (text) {
		return String(text).replace(/\%VERSION\%/mg, version);
	}
}]);

//keep the endDate from displaying when not COMPLETE after a job re-submission
//handy when the table is manually refreshed 
AppFilters.filter('jobEndDate', function () {
	return function (endDate, job) {
		if(endDate && job.jobStatus != 'COMPLETE') {
			return null;
		} else {
			return endDate;
		}
	}
});

//keep the job progress val at 99% until it is complete or cancelled
AppFilters.filter('jobProgress', function () {
	return function (progressVal, job) {
		if(progressVal > 99 && !(job.jobStatus == 'COMPLETE' || job.jobStatus == 'CANCELLED')) {
			return 99;
		} else if (job.jobStatus == 'COMPLETE' || job.jobStatus == 'CANCELLED'){
			return 100;
		} else {
			//round to two decimal places
			return progressVal.toFixed(2);
		}
	}
});

//convert map to array to be able to use orderBy, which is only available for arrays in angular!
AppFilters.filter('object2Array', function () {
	return function(input) {
		var out = []; 
		for(var i in input){
			out.push(input[i]);
		}
		return out;
	}
});

//full file path to file name
AppFilters.filter('fullPathToName', function () {
	return function(input) {
		return input.split('/').pop();
	}
});


/**
 * AngularJS default filter with the following expression:
 * "person in people | filter: {name: $select.search, age: $select.search}"
 * performs a AND between 'name: $select.search' and 'age: $select.search'.
 * We want to perform a OR.
 */
App.filter('propsFilter', function() {
  return function(items, props) {
    var out = [];

    if (angular.isArray(items)) {
      items.forEach(function(item) {
        var itemMatches = false;

        var keys = Object.keys(props);
        for (var i = 0; i < keys.length; i++) {
          var prop = keys[i];
          var text = props[prop].toLowerCase();
          if (item[prop].toString().toLowerCase().indexOf(text) !== -1) {
            itemMatches = true;
            break;
          }
        }

        if (itemMatches) {
          out.push(item);
        }
      });
    } else {
      // Let the output be the input untouched
      out = items;
    }

    return out;
  }
});

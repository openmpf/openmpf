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

'use strict';
/* global $, angular */

var AboutCtrl = function($scope, $location, $interpolate, MetadataService) {

	$scope.text_detection_filepath = "resources/downloads/OalprLicensePlateTextDetection.tar.gz";

	var initView = function() {
		fetchMetadata();
		loadDependencyGroups();
		makeLinksOpenInNewTab();
	};
	
	var fetchMetadata = function() {
		MetadataService.getMetadata().then(function(data) {
			$scope.displayVersion = HomeUtilsFull.displayVersion;
		});
	};

	var loadDependencyGroups = function () {
		$scope.depGroups = angular.fromJson($('#dependency-data').text());
		$scope.renderCustomLicense = function (dep) {
		    makeLinksOpenInNewTab();
			if (!dep.customLicenseId) {
				return '';
			}

			var $customLicenseEl = $('#' + dep.customLicenseId);
			if ($customLicenseEl.size()) {
				var customLicenseHtml = $customLicenseEl.html();
				return $interpolate(customLicenseHtml)($scope);
			}
			else {
				window.alert("The dependency named \"" + dep.name + "\" references the license template with id: \"" +
					dep.customLicenseId + "\", but it does not exist.\n" +
					"You must add a custom license with an id of: " + dep.customLicenseId);
			}
		};
	};

	var makeLinksOpenInNewTab = function () {
		$('#about-page').find('a').attr('target', '_blank');
	};


	initView();
};
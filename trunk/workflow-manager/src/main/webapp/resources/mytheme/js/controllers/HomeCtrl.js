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

/**
 * HomeCtrl
 * @constructor
 */

var HomeCtrl = function($scope, $location, MetadataService, RoleService) {
	$scope.initView = function() { 
		$scope.fetchRoleInfo();
		$scope.fetchMetadata();
	};
	
	$scope.fetchMetadata = function() {
		MetadataService.getMetadata().then(function(data) {
			$scope.meta = data;
			$scope.meta.displayVersion = $scope.meta.version
			if ( data.gitBranch === "unknown" ) {
				$scope.meta.displayVersion += " (unofficial build)"
			}
			else if ( data.gitBranch === "develop" ) {
				$scope.meta.displayVersion += " ( " + data.gitBranch + "-" + data.buildNum + "-" + data.gitHash + " )"
			}
		});
	};
		
	$scope.fetchRoleInfo = function() {
		RoleService.getRoleInfo().then(function(roleInfo) {
			$scope.roleInfo = roleInfo;
			
			//TODO: think about putting the role info on $rootScope
			$scope.contentLoaded = true; //to try to prevent {{ }} from showing prior to dom load
		});
	};
	
	$scope.changeView = function(view) {
        $location.path(view); // path not hash
    };
	
	$scope.initView();
};
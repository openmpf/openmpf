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
 * AdminPropertySettingsCtrl
 * @constructor
 */
var AdminPropertySettingsCtrl = function($scope, $rootScope, $log, $confirm, $state,
										 PropertiesService, NotificationSvc, SystemNotices ) {
		
	//from the propertiesModel
	$scope.propertiesModifiedServerSide = false;
	$scope.errorMessage = "";
	$scope.readPropertiesMap = {};
	$scope.modifiedPropertiesMap = {};
	
	$scope.isAdmin = $rootScope.roleInfo.admin;
	$scope.contentLoaded = false;
	
	//to use as the model for table in the view to allow for keeping readPropertiesMap
	//to make a reset easy
	$scope.readPropertiesMapCopy = {};
	//store any modified props in the view
	$scope.unsavedPropertiesMap = {};
	//count of above
	$scope.unsavedPropertiesCount = 0;

	$scope.getReadPropertiesCopy = function() {
		//shallow copy - http://stackoverflow.com/questions/8206988/clone-copy-a-javascript-map-variable
		var newMap = {};
		for (var key in $scope.readPropertiesMap) {
			newMap[key] = $scope.readPropertiesMap[key];
		}
		return newMap;		
	};
	
	$scope.getPropertiesModel = function(update) {
		PropertiesService.getPropertiesModel().then(function(propertiesModel) {
			
			$scope.propertiesModifiedServerSide = propertiesModel.propertiesModified;
			$scope.errorMessage = propertiesModel.errorMessage;
			$scope.readPropertiesMap = propertiesModel.readPropertiesMap;
			$scope.modifiedPropertiesMap = propertiesModel.modifiedPropertiesMap;
			//$log.info("propertiesModifiedServerSide="+angular.toJson($scope.propertiesModifiedServerSide));

			//create the copy
			$scope.readPropertiesMapCopy = $scope.getReadPropertiesCopy();
			$scope.contentLoaded = true;
		});
	};

	$scope.valueChanged = function(key) {
		return ($scope.unsavedPropertiesMap && $scope.unsavedPropertiesMap.hasOwnProperty(key));
	};

	$scope.serverNeedsRestart = function(key) {
		return ($scope.modifiedPropertiesMap && $scope.modifiedPropertiesMap.hasOwnProperty(key));
	};


	$scope.resetProperty = function(key) {
		$scope.readPropertiesMapCopy[key] = $scope.readPropertiesMap[key];
		$scope.changed(key);
	};
	
	$scope.changed = function(key) {
		//the readPropertiesMapCopy is used as the model for the table in the view
		if($scope.readPropertiesMapCopy[key] != $scope.readPropertiesMap[key]) {
			//TODO: might want to save the original value if the value is empty
			$scope.unsavedPropertiesMap[key] = $scope.readPropertiesMapCopy[key];  
		} else {
			//if the value is empty we still want to show it as modified
			delete $scope.unsavedPropertiesMap[key];
		}
		$scope.getUnsavedPropertiesSize();
	};
	
	$scope.getUnsavedPropertiesSize = function() {
	    var size = 0, key;
	    for (key in $scope.unsavedPropertiesMap) {
	        if ($scope.unsavedPropertiesMap.hasOwnProperty(key)) size++;
	    }
	    $scope.unsavedPropertiesCount = size;
	    return size;
	};
	
	$scope.resetAllProperties = function() {
		//TODO: could add a dialog to confirm
		//only modifies the properties that have not been saved (lightgreen)
		for (var key in $scope.unsavedPropertiesMap) {
			$scope.resetProperty(key);
		}
	};
	
	$scope.saveProperties = function() {
	   //convert the map to an array before sending to the server
	   var modifiedProperties = [];
	   for (var key in $scope.unsavedPropertiesMap) {
		   if ($scope.unsavedPropertiesMap.hasOwnProperty(key)) {
		       	modifiedProperties.push( {
		     		name: key, 
		    		value: $scope.unsavedPropertiesMap[key] 
			    });
	   	   }
	   }
	   
	   if(modifiedProperties.length > 0) {
		   //TODO: could make sure the service checks for admin credentials
		   PropertiesService.save(modifiedProperties).then(function(response) {
			   //response is
			   /*boolean saveSuccess,
			   String errorMessage*/
			   
			   if(response && response.saveSuccess) {
				   //clear any modified properties
				   $scope.unsavedPropertiesMap = {};
				   $scope.getUnsavedPropertiesSize();
				   //get the updated model
				   //TODO: might want to think of a method to clear the existing model
				   $scope.getPropertiesModel();
				   NotificationSvc.success('Properties have been saved!'); 
			   } else {
				   NotificationSvc.error(response.errorMessage);
			   }			  
		   });
	   } else {
		   NotificationSvc.error('No properties have been modified!');
	   }
	};
	
	$scope.getPropertiesModel();


	///////////////////////////////////////////////
	// event handlers (listed alphabetically by event name)
	///////////////////////////////////////////////


	//$scope.$on('$destroy', function () {
	//	$log.info("$scope.$on('$destroy'...)")
	//});

	$scope.confirmed = false;	// need to remember if we've asked the user the confirm, or else we'll get in loop

	$scope.$on('$stateChangeStart', function( event, toState ) {
		// toState holds a map that contains the page the user clicked on
		if ( !$scope.confirmed ) {
			if ($scope.getUnsavedPropertiesSize() > 0) {
				//var answer = confirm();
				//if (!answer) {
				event.preventDefault();	// stop the router right here, need to do this because of $confirm is asynchronous
				//}
				var myToState = toState;
				$confirm({
					title: "Unsaved changes",
					text: "You have modified MPF properties, but have not saved them to the server.  If you continue to another page, you will lose the changes you have made.  Are you sure you want to leave this page?",
					ok: "Yes", cancel: "No" })
					.then(
						function () {
							//$log.info("OK" + angular.toJson(myToState));
							$scope.confirmed = true;
							$state.go(myToState.name);
							//SystemNotices.warn( "You have modified values on the Property Settings page, but have not saved them to the server.",
							//	"adminPropertySettingsPage", {	route:"/admin/propertySettings" } );
						},
						function () {
							$log.info("Cancel");
						}
					);
			}
		}
	});

};
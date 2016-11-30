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

/* globals angular, _ */

(function () {

'use strict';	
	
/**
 * AngularJS controller for the component  registration page
 */
angular.module('WfmAngularSpringApp.controller.AdminComponentRegistrationCtrl', [
    'WfmAngularSpringApp.services'
])
.controller('AdminComponentRegistrationCtrl',
['$scope', 'Components', 'NotificationSvc',
function ($scope, Components, NotificationSvc) {

	$scope.components = Components.query();

	var statesEnum = Components.statesEnum;
	
	
	var refreshComponents = function () {
		$scope.components = Components.query();
	};

	var setComponentPackageState = function (packageName, newState) {
		var component = _.findWhere($scope.components, {packageFileName: packageName});
		if (component) {
			component.componentState = newState;
		}
	};

	var registerComponentPackage = function (packageName) {
		setComponentPackageState(packageName, statesEnum.REGISTERING);
		NotificationSvc.info('The registration of component package ' + packageName + ' has started!');
		Components.register(packageName)
			.$promise
			.then(function () {
				NotificationSvc.success('The component package ' + packageName + ' has been registered!');
			})
			.catch(function (errorResponse) {
				NotificationSvc.error(errorResponse.data.message);
			})
			.finally(refreshComponents);
	};


	
	$scope.registerComponent = function (component) {
		registerComponentPackage(component.packageFileName);
	};


	$scope.removeComponent = function (component) {
		component.componentState = statesEnum.REMOVING;
		Components.remove(component)
			.then(function () {
				if (component.componentName) {
					NotificationSvc.info('The ' + component.componentName + ' component has been removed');
				}
				else {
					NotificationSvc.info('The ' + component.packageFileName + ' component package has been removed.');
				}
				removeLocally(component);
			})
			.catch(function (errorResponse) {
				NotificationSvc.error(errorResponse.data.message);
				refreshComponents();
			});
	};

	
	var removeLocally = function (component) {
		var index = $scope.components.indexOf(component);
		if (index > -1) {
			$scope.components.splice(index, 1);
		}
	};
	
	

	$scope.canUploadPackage = function (file, done) {
		var duplicateComponent = _.findWhere($scope.components, {packageFileName: file.name});
		if (duplicateComponent) {
			var errorMsg = 'Cannot register component: \"' + file.name +
				'\": A component with the same file name has already been uploaded.';
			done({message: errorMsg, rejectedLocally: true});
		}
		else {
			done();
		}
	};

	var isInState = function (component) {
		return _.chain(arguments)
			.rest()
			.some(function (state) {
				return component.componentState === state;
			})
			.value();
	};

	$scope.canRegister = function (component) {
		return isInState(component, statesEnum.UPLOADED, statesEnum.REGISTER_ERROR);
	};
	
	$scope.isRegistering = function (component) {
		return isInState(component, statesEnum.REGISTERING, statesEnum.UPLOADING);
	};

	$scope.canRemove = function (component) {
		return isInState(component, statesEnum.UPLOADED, statesEnum.UPLOAD_ERROR,
				statesEnum.REGISTERED, statesEnum.REGISTER_ERROR);
	};
	
	$scope.isRemoving = function (component) {
		return isInState(component, statesEnum.REMOVING);
	};
	

	$scope.stateToText = function (state) {
		switch (state) {
			case statesEnum.UPLOADING:
				return 'Uploading';
			case statesEnum.UPLOADED:
				return 'Uploaded';
			case statesEnum.UPLOAD_ERROR:
				return 'Upload Error';
			case statesEnum.REGISTERING:
				return 'Registering';
			case statesEnum.REGISTERED:
				return 'Registered';
			case statesEnum.REGISTER_ERROR:
				return 'Registration Error';
			case statesEnum.REMOVING:
				return 'Removing';
			default:
				return state.toLowerCase();
		}
	};


	$scope.$on('mpf.component.dropzone.sending', function (evt, file) {
		$scope.components.push({
			packageFileName: file.name,
			componentState: statesEnum.UPLOADING,
			dateUploaded: new Date()
		});
	});
	
	$scope.$on('mpf.component.dropzone.success', function (evt, file) {
		registerComponentPackage(file.name);
	});

	$scope.$on('mpf.component.dropzone.error', function (evt, file, errorInfo) {
		NotificationSvc.error(errorInfo.message);
		if (!errorInfo.rejectedLocally) {
			refreshComponents();
		}
	});

}]);

})();
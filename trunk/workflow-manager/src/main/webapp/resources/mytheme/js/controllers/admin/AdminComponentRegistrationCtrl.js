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
angular.module('mpf.wfm.controller.AdminComponentRegistrationCtrl', [
    'mpf.wfm.services',
	'ui.bootstrap'
])
.controller('AdminComponentRegistrationCtrl',
['$scope', 'Components', 'NotificationSvc', '$uibModal',
function ($scope, Components, NotificationSvc, $uibModal) {

	$scope.components = Components.query();

	var statesEnum = Components.statesEnum;
	
	
	var refreshComponents = function () {
		Components.query()
			.$promise
			.then(function (components) {
				$scope.components = components;
			});
	};


	var registerComponentPackage = function (packageName) {
		var component = _.findWhere($scope.components, {packageFileName: packageName});
		NotificationSvc.info('The registration of component package ' + packageName + ' has started!');
		component.componentState = statesEnum.REGISTERING;

		component.$register()
			.then(function () {
				NotificationSvc.success('The component package ' + packageName + ' has been registered!');
			})
			.catch(function (errorResponse) {
				handleRegistrationError(component, errorResponse);
			});
	};


	
	$scope.registerComponent = function (component) {
		registerComponentPackage(component.packageFileName);
	};


	$scope.removeComponent = function (component) {
		component.componentState = statesEnum.REMOVING;
		Components.remove(component)
			.then(function () {
				NotificationSvc.info('The ' + component.packageFileName + ' component package has been removed');
				removeLocally(component);
			})
			.catch(function (errorResponse) {
				handleRegistrationError(component, errorResponse);
			});
	};


	$scope.reRegisterClicked = function (component) {
		var originalState = component.componentState;
		component.componentState = statesEnum.RE_REGISTERING;
		Components.getReRegisterOrder(component.packageFileName)
			.$promise
			.then(function (componentRegistrationOrder) {
				if (componentRegistrationOrder.length === 1) {
					reRegisterComponents(componentRegistrationOrder);
				}
				else {
					confirmReRegistration(component, componentRegistrationOrder, originalState);
				}
			})
			.catch(function (errorResponse) {
				NotificationSvc.error(errorResponse.data.message);
				component.componentState = originalState;
				// Save for when we allow re-registration with errors.
				// confirmReRegistration(component, null, originalState);
			});
	};

	var confirmReRegistration = function (targetComponent, registrationOrder, originalState) {
		var confirmClickedString = 'confirm clicked';
		$uibModal.open({
			templateUrl: 'reRegisterModal.html',
			windowTopClass: 'component-registration-modal',
			controller: [
				'$scope',
				function ($scope) {
					$scope.targetComponent = targetComponent;
					$scope.registrationOrder = registrationOrder;

					$scope.isNotTargetComponent = function (value) {
						return value !== targetComponent.packageFileName;
					};

					$scope.onReRegisterAllClick = function () {
						reRegisterComponents(registrationOrder);
						$scope.$close(confirmClickedString);
					};
					$scope.onReRegOnlyTargetClick = function () {
						reRegisterComponents([targetComponent.packageFileName]);
						$scope.$close(confirmClickedString);
					};

					$scope.$on('modal.closing', function (evt, description) {
						if (description !== confirmClickedString) {
							targetComponent.componentState = originalState;
						}
					});
				}]
		});
	};


	var reRegisterComponents = function (componentReRegOrder) {
		var componentIndex = _.indexBy($scope.components, 'packageFileName');

		componentReRegOrder.forEach(function (packageName) {
			componentIndex[packageName].componentState = statesEnum.RE_REGISTERING;
		});

		var promise;
		componentReRegOrder.forEach(function (packageName) {
			var component = componentIndex[packageName];
			if (promise) {
				promise = promise.then(function () {
					return createReRegPromise(component);
				});
			}
			else {
				promise = createReRegPromise(component);
			}
		});
	};

	var createReRegPromise = function (component) {
		return component.$reRegister()
			.then(function () {
				NotificationSvc.info('The ' + component.componentName + ' component has been re-registered');
			})
			.catch(function (errorResponse) {
				handleRegistrationError(component, errorResponse);
			});
	};


	var handleRegistrationError = function (component, errorResponse) {
		NotificationSvc.error(errorResponse.data.message);
		component.componentState = statesEnum.REGISTER_ERROR;
		refreshComponents();
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
		return isInState(component, statesEnum.UPLOADED, statesEnum.REGISTER_ERROR, statesEnum.DEPLOYED) &&
			!isReRegisteringAny();
	};

	$scope.isRegistering = function (component) {
		return isInState(component, statesEnum.REGISTERING, statesEnum.UPLOADING);
	};

	$scope.canRemove = function (component) {
		return isInState(component, statesEnum.UPLOADED, statesEnum.UPLOAD_ERROR, statesEnum.REGISTERED,
				statesEnum.REGISTER_ERROR, statesEnum.DEPLOYED) && !isReRegisteringAny();
	};
	
	$scope.isRemoving = function (component) {
		return isInState(component, statesEnum.REMOVING);
	};

	$scope.isReRegistering = function (component) {
		return isInState(component, statesEnum.RE_REGISTERING);
	};

	var isReRegisteringAny = function () {
		return _.some($scope.components, {componentState: statesEnum.RE_REGISTERING});
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
			case statesEnum.RE_REGISTERING:
				return 'Re-registering';
			case statesEnum.DEPLOYED:
				return 'Deployed';
			default:
				return state.toLowerCase();
		}
	};


	$scope.$on('mpf.component.dropzone.sending', function (evt, file) {
		$scope.components.push(Components.newComponent({
			packageFileName: file.name,
			componentState: statesEnum.UPLOADING,
			dateUploaded: new Date()
		}));
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
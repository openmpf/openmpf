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
 * AdminDashboardCtrl
 */
var AdminDashboardCtrl = function($scope, $log, $http, ServerSidePush, NodeManagerService, ClientState) {
	
	// panel state css "enums"
	var eOkState = "panel status-ok-panel";	// this state differs from the "good" state in that it does not specify desirability (e.g., the number of nodes)  
	var eGoodState = "panel status-good-panel";
	var eWarningState = "panel status-warn-panel";
	var eErrorState = "panel status-error-panel";
	
	///////////////////////////////////////////////
	// widget models
	///////////////////////////////////////////////

	$scope.widgetSystemHealth = {
			icon: "fa fa-check fa-4x",
			css : eGoodState };
	
	$scope.widgetNodes = { hostnames : [] };
	
	$scope.widgetServices = { 
		services : [],
		numRunning : 0,
		css : eOkState };
	
	///////////////////////////////////////////////
	// widget "calculators"
	///////////////////////////////////////////////
	
	// collects system health data and presents it to UI
	$scope.calcSystemHealth = function() {
		if ( ClientState.isConnectionActive() ) {
			$scope.widgetSystemHealth.css = eGoodState;
			$scope.widgetSystemHealth.icon = "fa fa-check fa-4x"
		}
		else {
			$scope.widgetSystemHealth.css = eErrorState;
			$scope.widgetSystemHealth.icon = "fa fa-times-circle fa-4x"
		}
	};
	
	$scope.calNodesWidget = function() {
		NodeManagerService.getNodeManagerHostnames().then(
				function(payload) {
					$scope.widgetNodes.hostnames = payload;
				},
				function(error) {
					$scope.widgetNodes.hostnames = [];
				});
	};
	
	$scope.calcServicesWidget = function() {
		NodeManagerService.getServices().then(
				function(payload) {
//					console.log("payload==>"+JSON.stringify(payload.nodeModels));
					$scope.widgetServices.services = payload.nodeModels;
					var num = $scope.widgetServices.services.length;
			        if ( num <= 0 )
			        {
			            $scope.widgetServices.css = eErrorState;
			        }
			        else
			        {
				        for ( var i = 0; i < $scope.widgetServices.services.length; i++ ) {
				            if ( $scope.widgetServices.services[i].lastKnownState !== "Running" ) {
				            	$scope.widgetServices.css = eWarningState;
				            	num--;
				            }
				        }
			            if ( num === $scope.widgetServices.services.length ) // we've gone through all of them, and they're all running
			            {
			            	$scope.widgetServices.css = eGoodState;
			            }
				        $scope.widgetServices.numRunning = num;
			        }
				},
				function(error) {
					$scope.widgetServices = { services : [], numRunning : 0, css : eErrorState };
				});
	};
	
	///////////////////////////////////////////////
	// controller initializer
	///////////////////////////////////////////////

	$scope.init = function() {
		$scope.calcSystemHealth();
		$scope.calNodesWidget();
		$scope.calcServicesWidget();
	};

	///////////////////////////////////////////////
	// event handlers
	///////////////////////////////////////////////

	$scope.$on('SSPC_ATMOSPHERE', function(event, msg ) { 
//		console.log("SSPC_ATMOSPHERE message (received in dashboard):  " + JSON.stringify(msg,2,null));
		$scope.calcSystemHealth();
	});

	$scope.$on('SSPC_HEARTBEAT', function(event, msg ) { 
//		console.log( "SSPC_HEARTBEAT: " + JSON.stringify(msg) );
		$scope.calcSystemHealth();
//		$scope.$apply();	// used to need to call this because event handlers happen outside of the Angular, and this causes Angular to update internally
	});

	$scope.$on('CS_CONNECTION_STATE_CHANGED', function(event, args) {
				$scope.calcSystemHealth();
	});

	$scope.$on('SSPC_NODE', function(event, msg ) {
//		console.log( "SSPC_NODE: " + JSON.stringify(msg) );
		if ( msg.event==='OnNodeConfigurationChanged' ) {
			// TODO: P038: this event is not sent from server yet
			$scope.calNodesWidget();
		}
	});
	
	$scope.$on('SSPC_SERVICE', function(event, msg ) { 
//		console.log( "SSPC_SERVICE: " + JSON.stringify(msg) );
		// all events affect this services widget
		$scope.calcServicesWidget();
	});

	$scope.$on('$viewContentLoaded', function() {
		//$log.debug( "onViewContentLoaded()" );
	});

	$scope.$on('$destroy', function () {
		//$log.debug("onDestroy()");
	});


	$scope.init();
};
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


//'ngAnimate',
var App = angular.module('mpf.wfm', [
	'ui.router',
	'ui.bootstrap',
	'ui.select',
	'ui.grid',
	'ngSanitize',
	'ngMessages',
	'ngResource',
	'as.sortable',
	'angular-confirm',
	'mpf.wfm.controller.ServerMediaCtrl',
	'mpf.wfm.controller.AdminComponentRegistrationCtrl',
	'mpf.wfm.pipeline2',
	'mpf.wfm.filters',
	'mpf.wfm.services',
	'mpf.wfm.directives',
	'mpf.wfm.property.settings'
]);

// Declare app level module which depends on filters, and services
App.config(['$stateProvider', '$urlRouterProvider','$httpProvider' ,function ($stateProvider, $urlRouterProvider,$httpProvider) {
	  // For any unmatched url, redirect to /jobs
	  //$urlRouterProvider.otherwise("/home");
	  $urlRouterProvider.otherwise("/jobs");
	  
	  $stateProvider.state('/home', {
		  url: '/home',
		  templateUrl: 'home/layout.html',
		  controller: HomeCtrl
	  });

	$stateProvider.state('/about', {
		url: '/about',
		templateUrl: 'about/layout.html',
		controller: AboutCtrl
	});

	  $stateProvider.state('/server_media', {
		  url: '/server_media',
		  templateUrl: 'server_media/layout.html',
		  controller: 'ServerMediaCtrl'
	  });

	  $stateProvider.state('/pipelines', {
		  url: '/pipelines',
		  templateUrl: 'pipelines/layout',
		  controller: PipelinesCtrl
	  });

	  $stateProvider.state('/pipelines2', {
		  url: '/pipelines2',
		  templateUrl: 'pipelines2/layout.html',
          controller: 'Pipelines2Ctrl',
		  controllerAs: 'pipes2'
	  });

	  $stateProvider.state('/jobs', {
		  url: '/jobs',
		  templateUrl: 'jobs/layout.html',
		  controller: JobsCtrl
	  });

	  $stateProvider.state('/adminNodes', {
		  url: '/adminNodes',
		  templateUrl: 'admin/nodes/layout',
		  controller: AdminNodesCtrl
	  });

	  $stateProvider.state('/admin/propertySettings', {
		  url: '/admin/propertySettings',
		  templateUrl: 'admin/property_settings/layout',
		  controller: 'AdminPropertySettingsCtrl'
	  });
	  
	  $stateProvider.state('/admin/componentRegistration', {
		  url: '/admin/componentRegistration',
		  templateUrl: 'admin/component_registration/layout',
		  controller: 'AdminComponentRegistrationCtrl'
	  });

	  $stateProvider.state('/adminLogs', {
		  url: '/adminLogs',
		  templateUrl: 'admin/log/layout' ,
		  controller: AdminLogsCtrl
	  });

	  $stateProvider.state('/adminStatistics', {
		  url: '/adminStatistics',
		  templateUrl: 'admin/stats/layout',
		  controller: AdminStatsCtrl
	  });

      $stateProvider.state('/adminWebServices', {
          url: '/adminWebServices',
          templateUrl: 'adminWebServices/layout.html' //,
          // controller: //TODO: need a controller
      });

	$httpProvider.defaults.headers.common["X-Requested-With"] = 'XMLHttpRequest';//need to add explicitly but can cause problems with CORS
    $httpProvider.interceptors.push('httpInterceptor');//services.js
}]);

//$log.debug should not work without this, but other levels should
App.config(function($logProvider){
	$logProvider.debugEnabled(true);
});

// interpolation decorator to help debug {{}}
//	code from http://odetocode.com/blogs/scott/archive/2014/05/27/debugging-angularjs-data-binding.aspx
//App.config(function($provide){
//    $provide.decorator("$interpolate", function($delegate){
// 
//        var interpolateWrap = function(){
//            var interpolationFn = $delegate.apply(this, arguments);
//            if(interpolationFn) {
//                return interpolationFnWrap(interpolationFn, arguments);
//            }
//        };
// 
//        var interpolationFnWrap = function(interpolationFn, interpolationArgs){
//            return function(){
//                var result = interpolationFn.apply(this, arguments);
//                var log = result ? console.log : console.warn;
//                log.call(console, "interpolation of  " + interpolationArgs[0].trim(), 
//                                  ":", result.trim());
//                return result;
//            };
//        };
// 
//        angular.extend(interpolateWrap, $delegate);
//        return interpolateWrap;
// 
//    });
//});

//run startup code to redirect to the start page if this is an admin logic
App.run( function( $rootScope, $state, $log, $interval, RoleService, MetadataService, ServerSidePush, SystemStatus,ClientState ,NodeService) {
	$log.debug('WfmAngularSpringApp starting.');


	// panel state css "enums"
	var eOkState = "";
	var eGoodState = "bg-success";
	var eWarningState = "bg-warning";
	var eErrorState = "bg-danger";

	///////////////////////////////////////////////
	// widget models
	///////////////////////////////////////////////

	$rootScope.widgetSystemHealth = {
		icon: "fa fa-check",// fa-4x",
		css : eGoodState };

	$rootScope.widgetNodes = { hostnames : [] };

	$rootScope.widgetServices = {
		services : [],
		numRunning : 0,
		css : eOkState };

	/* this is the data structure that the system-notice directive uses to store system notices */
	$rootScope.systemNotices = [];

	/* this keeps track of the last time this client received a message from the server or successfully sent a request to the server
	 * currently only used by Atmosphere, but should be extended (via decorators: http://solutionoptimist.com/2013/10/07/enhance-angularjs-logging-using-decorators/)
	 * to any server-side exchange.
	 */
	$rootScope.lastServerExchangeTimestamp = moment();	// initialize to current time because we must have just gotten this file

	// collects system health data and presents it to UI
	$rootScope.calcSystemHealth = function() {
		if ( ClientState.isConnectionActive() ) {
			$rootScope.widgetSystemHealth.css = eGoodState;
			$rootScope.widgetSystemHealth.icon = "fa fa-check"
		}
		else {
			$rootScope.widgetSystemHealth.css = eErrorState;
			$rootScope.widgetSystemHealth.icon = "fa fa-times-circle"
		}
	};

	$rootScope.calNodesWidget = function() {
		NodeService.getNodeManagerHostnames().then(
			function(payload) {
				$rootScope.widgetNodes.hostnames = payload;
			},
			function(error) {
				$rootScope.widgetNodes.hostnames = [];
			});
	};

	$rootScope.calcServicesWidget = function() {
		NodeService.getServices().then(
			function(payload) {
//					console.log("payload==>"+JSON.stringify(payload.nodeModels));
				$rootScope.widgetServices.services = payload.nodeModels;
				var num = $rootScope.widgetServices.services.length;
				if ( num <= 0 )
				{
					$rootScope.widgetServices.css = eErrorState;
				}
				else
				{
					for ( var i = 0; i < $rootScope.widgetServices.services.length; i++ ) {
						if ( $rootScope.widgetServices.services[i].lastKnownState !== "Running" ) {
							$rootScope.widgetServices.css = eWarningState;
							num--;
						}
					}
					if ( num === $rootScope.widgetServices.services.length ) // we've gone through all of them, and they're all running
					{
						$rootScope.widgetServices.css = eGoodState;
					}
					$rootScope.widgetServices.numRunning = num;
				}
			},
			function(error) {
				$rootScope.widgetServices = { services : [], numRunning : 0, css : eErrorState };
			});
	};

	RoleService.getRoleInfo().then(function(roleInfo) {
		//TODO: extra work - can set the isAdmin field in HomeUtils
		HomeUtilsFull.isAdmin = roleInfo.admin;
		HomeUtilsFull.roleInfo = roleInfo;
		
		//also attach to rootScope, HomeUtilsFull will hopefully not be needed in the future
		$rootScope.roleInfo = roleInfo;
		
		//only direct to the jobs on first admin login - not on hard refresh with an active session
		if(roleInfo['admin'] && roleInfo['firstLogin']) {
			$state.go('/jobs');
		}
	});
	
	MetadataService.getMetadata().then(function(data) {
		HomeUtilsFull.displayVersion = data.version;
		if ( data['gitBranch'] === "unknown" ) {
			HomeUtilsFull.displayVersion += " (unofficial build)";
		}
		else if ( data['gitBranch'] === "develop" ) {
			HomeUtilsFull.displayVersion += " ( " + data['gitBranch'] + "-" + data['buildNum'] + "-" + data['gitHash'] + " )";
		}
	});


	///////////////////////////////////////////////
	// event handlers
	///////////////////////////////////////////////

	$rootScope.$on('SSPC_ATMOSPHERE', function(event, msg ) {
		console.log("SSPC_ATMOSPHERE message (received in dashboard):  " + JSON.stringify(msg,2,null));
		$rootScope.calcSystemHealth();
	});

	$rootScope.$on('SSPC_HEARTBEAT', function(event, msg ) {
		//console.log( "SSPC_HEARTBEAT: " + JSON.stringify(msg) );
		$rootScope.calcSystemHealth();
	});

	$rootScope.$on('CS_CONNECTION_STATE_CHANGED', function(event, args) {
		console.log("CS_CONNECTION_STATE_CHANGED message (received in dashboard)");
		$rootScope.calcSystemHealth();
	});

	$rootScope.$on('SSPC_NODE', function(event, msg ) {
		console.log( "SSPC_NODE: " + JSON.stringify(msg) );
		if ( msg.event==='OnNodeConfigurationChanged' ) {
			// TODO: P038: this event is not sent from server yet
			$rootScope.calNodesWidget();
		}
	});

	$rootScope.$on('SSPC_SERVICE', function(event, msg ) {
//		console.log( "SSPC_SERVICE: " + JSON.stringify(msg) );
		// all events affect this services widget
		$rootScope.calcServicesWidget();
	});


	/* get initial System Messages */
	$rootScope.$watch('$viewContentLoaded', function(){
		//$log.debug("app.run: about to show all system messages");
		SystemStatus.showAllSystemMessages();
	});

	//check every 2 seconds to see if the user should be booted out
	//using get metadata as the polling target
	// replaced $timeout since it interferes with protractor, and this is equivalent
	//	however, this should be replaced by something more elegant, perhaps with server side push
	$interval( function() {
		MetadataService.getMetadata(true)
	}, 2000);

	//init
	$rootScope.calcSystemHealth();
	$rootScope.calNodesWidget();
	$rootScope.calcServicesWidget();

} );

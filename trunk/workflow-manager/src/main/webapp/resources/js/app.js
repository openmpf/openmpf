/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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
	'ngSanitize',
	'ngMessages',
	'ngResource',
	'angular-confirm',
	'mpf.wfm.controller.ServerMediaCtrl',
	'mpf.wfm.controller.AdminComponentRegistrationCtrl',
	'mpf.wfm.controller.JobsCtrl',
	'mpf.wfm.controller.MarkupCtrl',
	'mpf.wfm.pipeline2',
	'mpf.wfm.filters',
	'mpf.wfm.services',
	'mpf.wfm.directives',
	'mpf.wfm.property.settings'
]);


(() => {
	// Get the CSRF configuration added to the page index.jsp.
	const header = $('#_csrf_header').attr('content');
	const formParam = $('#_csrf_parameterName').attr('content')
	const token = $('#_csrf').attr('content');

	App.constant('csrf', {
		headers: existing => {
			const result = existing || {};
			result[header] = token;
			return result;
		},
		formParam,
		token
	});
})();


// Declare app level module which depends on filters, and services
App.config(['$stateProvider', '$urlRouterProvider', function ($stateProvider, $urlRouterProvider) {
	  // For any unmatched url, redirect to /jobs
	  $urlRouterProvider.otherwise("/jobs");

	  // Matches URLs with no fragment. After a user initally logs in, the URL will not have a
	  // fragment.
	  $urlRouterProvider.when('', () => {
		  // If a user tried to go to a specific page, but wasn't logged in, login_view.jsp will
		  // store the URL fragment in sessionStorage.
		  const loginFragment = sessionStorage.getItem('loginFragment');
		  if (loginFragment) {
			  sessionStorage.removeItem('loginFragment');
			  return loginFragment.substring(1);
		  }
		  else {
			  return '/jobs';
		  }
	  });


	  var getTemplateUrl = function(name) {
		  return 'resources/layouts/' + name + '.html';
	  };

	  $stateProvider.state('/about', {
		  url: '/about',
		  templateUrl: getTemplateUrl('about'),
		  controller: AboutCtrl,
		  resolve: {
            depResponse: function($http) {
                return $http.get("resources/json/dependencies.json");
            }
		  }
	  });

	  $stateProvider.state('/server_media', {
		  url: '/server_media',
		  templateUrl: getTemplateUrl('server_media'),
		  controller: 'ServerMediaCtrl'
	  });

	  $stateProvider.state('/pipelines', {
		  url: '/pipelines',
		  templateUrl: getTemplateUrl('pipelines'),
		  controller: PipelinesCtrl
	  });

	  $stateProvider.state('/pipelines2', {
		  url: '/pipelines2',
		  templateUrl: getTemplateUrl('pipelines2'),
          controller: 'Pipelines2Ctrl',
      });

	  $stateProvider.state('jobs', {
		  url: '/jobs',
		  templateUrl: getTemplateUrl('jobs'),
		  controller: 'JobsCtrl'
	  });
	  $stateProvider.state({
		name: 'jobs.page',
		url: '/{page}/{pageLen}/{orderDirection}/{orderCol}/{search}'
	  })


	  $stateProvider.state('/adminNodes', {
		  url: '/adminNodes',
		  templateUrl: getTemplateUrl('admin/nodes'),
		  controller: AdminNodesCtrl,
		  resolve: {
			  checkDocker: ['MetadataService', '$state', function (MetadataService, $state) {
				  return MetadataService.getMetadata()
					  .then(function (metadata) {
						  if (!metadata.dockerEnabled) {
						      return;
						  }
						  if (!$state.current.name) {
							  // Only send user to /jobs when they aren't currently on one of our pages.
							  $state.go("/jobs");
						  }
						  // Throw error to prevent user from navigating to this page.
						  throw new Error("The adminNodes page is disabled in Docker deployments.");
					  });
			  }] // checkDocker
		  }
	  });

	  $stateProvider.state('/admin/propertySettings', {
		  url: '/admin/propertySettings',
		  templateUrl: getTemplateUrl('admin/property_settings'),
		  controller: 'AdminPropertySettingsCtrl'
	  });

	  $stateProvider.state('/admin/componentRegistration', {
		  url: '/admin/componentRegistration',
		  templateUrl: getTemplateUrl('admin/component_registration'),
		  controller: 'AdminComponentRegistrationCtrl',
		  resolve: {
			  roleInfo: ['RoleService', function (r) { return r.getRoleInfo(); }],
			  metadata: ['MetadataService', function (m) { return  m.getMetadata(); }]
		  }
	  });

	  $stateProvider.state('/adminLogs', {
		  url: '/adminLogs',
		  templateUrl: getTemplateUrl('admin/logs'),
		  controller: AdminLogsCtrl
	  });

	  $stateProvider.state('/adminStatistics', {
		  url: '/adminStatistics',
		  templateUrl: getTemplateUrl('admin/stats'),
		  controller: AdminStatsCtrl
	  });
}]);


App.config(['$httpProvider', function($httpProvider) {
	//need to add explicitly but can cause problems with CORS
	$httpProvider.defaults.headers.common["X-Requested-With"] = 'XMLHttpRequest';
	$httpProvider.interceptors.push('httpInterceptor');//services.js
}]);

//$log.debug should not work without this, but other levels should
App.config(function($logProvider){
	$logProvider.debugEnabled(true);
});


App.run([
'JobStatusNotifier', 'ServerSidePush',
(JobStatusNotifier, ServerSidePush) => {
	JobStatusNotifier.beginWatching();
	ServerSidePush.init()
}]);

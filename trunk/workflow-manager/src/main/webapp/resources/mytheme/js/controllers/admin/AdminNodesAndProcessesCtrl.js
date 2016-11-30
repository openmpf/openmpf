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
 * AdminNodesAndProcessesCtrl
 * @constructor
 */
var AdminNodesAndProcessesCtrl = function($scope,$rootScope,$log,$http,NodeManagerService,NodesAndProcessService,uiGridConstants) {

	$scope.isAdmin = $rootScope.roleInfo.admin;
	$scope.data = null;	// this serves 2 purposes:
						// 1:  holds data that comes back from the service
						// 2:  when it's null, it forces the ng-if in the html to remove the DOM, and when it's set, it forces the DOM to
						//		be rebuilt.  This is the only way I could find (after spending a lot of time) to get ui-grid
						//		to show all the rows without a scroller
						// (based on solution on https://github.com/angular-ui/ui-grid/issues/1735 by hernanortiz84)

    $scope.initView = function() {
		$scope.tableOptions = {
			//data:  $scope.data,	// set in updateData
			enableColumnMenus: false,
			//minRowsToShow: $scope.data.length,	// set in updateData
			enableHorizontalScrollbar: 0,
			enableVerticalScrollbar: 0,
			columnDefs: [
				{ name: 'Service Name', field: 'name', minWidth: 298,
					sort: { direction: uiGridConstants.ASC, priority: 0 } },
				{ name: 'rank', maxWidth: 50 },
				{ name: 'state', field: 'lastKnownState', minWidth: 80,
					cellClass: function (grid, row) {
						return row.entity.lastKnownState === "Running" ? "process-running" : "process-stopped";
					}},
				{ name: 'unlaunchable?', field: 'unlaunchable', minWidth: 55, maxWidth: 121 },
				{ name: 'restart', field: 'restartCount', maxWidth: 75 }
			]
		}

		angular.forEach( $scope.tableOptions.columnDefs, function( item ) {
			item.headerTooltip = item.name;
			item.cellTooltip = item.name;
		});

		// add admin stuff
		if ( $scope.isAdmin ) {
			$scope.tableOptions.columnDefs.push(
				{ name: 'actions', maxWidth: 150, minWidth: 150, enableSorting: false,
					cellTemplate:'' +
					'<button ng-class="grid.appScope.getButtonClass(row)" ng-click="grid.appScope.startService(row)" ng-disabled="grid.appScope.isRunning(row)">Start</button>' +
					'<button ng-class="grid.appScope.getButtonClass(row)" ng-click="grid.appScope.shutDownService(row)" ng-disabled="!grid.appScope.isRunning(row)">Stop</button>' +
					'<button ng-class="grid.appScope.getButtonClass(row)" ng-click="grid.appScope.restartService(row)">Restart</button>'
				}
			)
			//$scope.tableOptions.minRowsToShow = 20;
		}

		// Actions

		$scope.getButtonClass = function( row ) {
			return "btn btn-default btn-xs";
		};

		$scope.isRunning = function( row ) {
			return ( row.entity.lastKnownState === "Running" );
		};

		$scope.startService = function( row ) {
			var name = row.entity.name;
			NodesAndProcessService.startService(Utils.handleSpecialChars(name),name,$scope.updateData);
		};

		$scope.shutDownService = function( row ){
			var name = row.entity.name;
			NodesAndProcessService.shutdownService(Utils.handleSpecialChars(name),name,$scope.updateData);
		};

		$scope.restartService = function( row ) {
			var name = row.entity.name;
			NodesAndProcessService.restartService(Utils.handleSpecialChars(name),name,$scope.updateData);
		};

		// get the data and draw the table
		$scope.updateData();

    };

	$scope.updateData = function( rebuild ){
		if ( rebuild === true ) {
			$scope.data = null;	// reset, and also for ng-if in html to remove DOM element so it can be rebuilt later
		}
		NodeManagerService.getServices().then( function( data ) {
			//$log.debug("NodeManager updateData",data);
			if(data && data.nodeModels){ //should be array

				// clean up nodeModels for user see NodeManagerConstants Unknown, Configured, Launching, Running, ShuttingDown, ShuttingDownNoRestart, Inactive, InactiveNoStart, Delete, DeleteInactive
				angular.forEach(data.nodeModels, function(ele) {
					if (ele.lastKnownState.startsWith("Inactive") || ele.lastKnownState.startsWith("InactiveNoStart")) {
						ele.lastKnownState = "Stopped";
					}else if (ele.lastKnownState.startsWith("ShuttingDown") || ele.lastKnownState.startsWith("ShuttingDownNoRestart")) {
						ele.lastKnownState = "Stopping";
					}
				});
				// assign to $scoope.data for ui-grid to pick up
				$scope.data = data.nodeModels;
				$scope.tableOptions.data = $scope.data;
				$scope.tableOptions.minRowsToShow = $scope.data.length;
			}else{
				$log.debug("No data",data);
			}
		});
	};


	///////////////////////////////////////////////
	// event handlers (listed alphabetically by event name)
	///////////////////////////////////////////////


	$scope.$on('SSPC_SERVICE', function( event, msg ) {
		//$log.debug( "SSPC_SERVICE (in nodes and processes page): " + JSON.stringify(msg) );
		$scope.updateData();
	});


	//////////////////////////////////////////////////////////////
	// initialize the controller
	//////////////////////////////////////////////////////////////

	$scope.initView();
};
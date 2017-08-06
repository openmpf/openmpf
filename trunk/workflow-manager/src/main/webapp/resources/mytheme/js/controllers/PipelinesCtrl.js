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

/**
 * PipelinesCtrl
 * @constructor
 */
var PipelinesCtrl = function($scope,$http, $timeout, NotificationSvc) {

	$scope.selectedAction;
	$scope.selectedTask;
	$scope.actionsToAdd = [];
	$scope.tasksToAdd = [];
	//use like this: modifiedAlgValuesMap[propertyName] = value
	$scope.modifiedAlgValuesMap = {};

	$scope.getPipelinesModel = function() {
		$http.get('pipelines/model').success(function(pipelinesModel){
			$scope.algorithms = pipelinesModel.algorithms;
			$scope.actions = pipelinesModel.actions;
			$scope.tasks = pipelinesModel.tasks;
			$scope.pipelines = pipelinesModel.pipelines;
		});
	};

	$scope.getAlgModel = function() {
		//reset the modifiedAlgValuesMap!
		$scope.modifiedAlgValuesMap = {};
		if($scope.selectedAlgorithm) {
			$http({
				url: 'pipelines/algorithm-properties', 
				method: "GET",
				params: {algName: $scope.selectedAlgorithm}
			}).success(function(algModel){
				$scope.algModel = algModel;
			});
		}
	};

	$scope.createAction = function() {		
		if($scope.selectedAlgorithm && $scope.modifiedAlgValuesMap &&
				$scope.modalName && $scope.modalDescription) {

			var dataObj = {
					algorithmName: $scope.selectedAlgorithm,
					actionName: $scope.modalName, 
					actionDescription: $scope.modalDescription,
					properties: JSON.stringify($scope.modifiedAlgValuesMap)
			};
			var res = $http.post('pipelines/create-action', dataObj);
			res.success(function(responseTuple, status, headers, config) {
				//should be true/null on success
				if(responseTuple.first) {
					var successMsg = 'Successfully added the action ' + $scope.modalName;
					
					$scope.modalName = "";
					$scope.modalDescription = "";
					$scope.modalType = "";
					//reload model
					$scope.getPipelinesModel();

					$scope.selectedAlgorithm = "";
					$scope.modifiedAlgValuesMap = {};
					$scope.showAddAction = false;
					
					//temporary jquery solution to dismiss modal
					$('#addModal').modal('toggle');
					
				    $timeout(function() {
				    	NotificationSvc.success(successMsg);
				    }, 350);
				} else {
					alert('failure: ' + responseTuple.second);
				}
			});
			res.error(function(data, status, headers, config) {
				alert( "failure message: " + JSON.stringify({data: data}));
			});

		} else {
			alert('missing fields');
		}
	};

	$scope.createTaskOrPipeline = function() {
		if($scope.modalName && $scope.modalDescription) {
			var modelToSend = {
					type : $scope.modalType, 
					name : $scope.modalName, 
					description : $scope.modalDescription,
					itemsToAdd : ($scope.modalType === 'task') ? $scope.actionsToAdd : $scope.tasksToAdd 
			};
	
			var res = $http.post('pipelines/add-task-or-pipeline', modelToSend);
			res.success(function(responseTuple, status, headers, config) {
				if(responseTuple.first) {
					var successMsg = 'Successfully added the ' + $scope.modalType+ ' ' + $scope.modalName;
												
					$scope.modalName = "";
					$scope.modalDescription = "";
					//reload model
					$scope.getPipelinesModel();
					if($scope.modalType === 'task') { 
						$scope.actionsToAdd = [];
						$scope.showAddTask = false;
					} else { /*pipeline*/
						$scope.tasksToAdd = [];
						$scope.showAddPipeline = false;
					}
					$scope.modalType = "";
					//temporary jquery solution to dismiss modal
					$('#addModal').modal('toggle');
					
				    $timeout(function() {
				    	NotificationSvc.success(successMsg);
				    }, 350);
				} else {
					alert('failure: ' + responseTuple.second);
				}
			});
			res.error(function(data, status, headers, config) {
				alert( "failure message: " + JSON.stringify({data: data}));
			});
		} else {
			alert('missing fields');
		}
	};	
	
	$scope.create = function() {
		if($scope.modalType == 'action') {
			$scope.createAction();  
		} else {
			$scope.createTaskOrPipeline();
		}
	}

	$scope.removeAction = function(index) {
		console.log('index: ' + index);
		$scope.actionsToAdd.splice(index,1);
	};

	$scope.removeTask = function(index) {
		console.log('index: ' + index);
		$scope.tasksToAdd.splice(index,1);
	};	
	
	$scope.getPipelinesModel();
};
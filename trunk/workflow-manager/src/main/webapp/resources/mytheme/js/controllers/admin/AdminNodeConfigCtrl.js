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
 * AdminNodeConfigCtrl
 * @constructor
 */
var AdminNodeConfigCtrl = function( $scope, $log, $filter, $confirm, RoleService, ServicesCatalogService, NodeConfigService ) {
    // set this to true to enable some debugging features: generated JSON display
    $scope.isDebugging = false;

    $scope.isAdmin = false;     // set in init()
    $scope.catalogReceived = false;
    $scope.nodeConfigsReceived = false;
    $scope.nodeHostnamesReceived = false;

    $scope.nodeHostnames = [];
    $scope.catalog =[];
    $scope.nodes = [];    // the nodes' configurations

    ///////////////////////////////////////////////
    // Initializations
    ///////////////////////////////////////////////


    $scope.init = function() {
        //$log.debug('$scope.init()');
        RoleService.getRoleInfo().then( function(roleInfo) {
            $scope.isAdmin = roleInfo.admin;
            $log.debug("$scope.isAdmin="+$scope.isAdmin);
            var refresh = true;
            ServicesCatalogService.getServicesCatalog( refresh ).then( function( data ) {
                //$log.debug(angular.toJson(data));
                $scope.catalog = data;
                $scope.catalogReceived = true;
            });
            NodeConfigService.getAllNodesHostnames( false, $scope.isDebugging ).then( function( data ) {
                $scope.nodeHostnames = data;
                $scope.nodeHostnamesReceived = true;
            });

            $scope.getConfigs();
        });
    };


    ////////////////////////////////////////////////
    // Helper functions
    ////////////////////////////////////////////////


    $scope.getConfigurableHostnames = function() {
        var fullList = $scope.nodeHostnames;
        var configList = [];
        var out = [];

        angular.forEach( $scope.nodes, function( item ) {
            configList.push( item.host )
        });
        angular.forEach( fullList, function( item ) {
            if ( configList.indexOf( item ) < 0 ) {
                out.push( item )
            }
        });
        return out;
    };


    ///////////////////////////////////////////////
    // Operations
    ///////////////////////////////////////////////


    $scope.addNode = function() {
        $confirm( { hostnames: $scope.getConfigurableHostnames() }, {templateUrl: 'add-new-node-dialog.html'})
            .then(function( selectedHostname ) {
                if ( selectedHostname ) {
                    var node = {"host": selectedHostname, "services": []};
                    $scope.nodes.push(node);
                }
            });
    };

    $scope.getConfigs = function()  {
        var refreshConfig = true;
        NodeConfigService.getNodeConfigs( refreshConfig ).then( function( data ) {
            $scope.nodes = data;
            $scope.nodeConfigsReceived = true;
            $scope.catalog = $filter('orderBy')($scope.catalog,'serviceName');
        });
    };


    /** returns the number of user input errors on the page
     *  from http://stackoverflow.com/a/26087220 and http://jsfiddle.net/3dendec9/
     * @param form
     * @returns {number}
     */
    $scope.pageErrors = 0;  // should use this "cached version" instead of numberoferrors() in html
    $scope.numberoferrors = function( form ){
        var count = 0;
        var errors = form.$error;
        angular.forEach(errors, function(val){ if(angular.isArray(val)) {  count += val.length; }  });
        $scope.pageErrors = count;
        return count || 0;
    };


    $scope.saveConfigs = function() {
        // clean out zero instance services so they are deleted
        angular.forEach( $scope.nodes, function( val, idx ) {
            // need to traverse through the list in reverse or else item indices will get mixed up and not all items will be deleted
            var list = $scope.nodes[idx].services;
            for ( var i = val.services.length - 1; i >= 0; i-- ) {
                var service = val.services[i];
                if ( service.serviceCount === 0 || !service.serviceCount ) {
                    list.splice(i, 1);
                    $log.debug("removing " + service.serviceName );
                }
            }
        });

        // save nodes
        var json = angular.toJson( $scope.nodes );
//        $log.debug(' json='+json);
        NodeConfigService.saveNodeConfigs(json).then(
            function onSuccess(response) {                            
            	var savedSuccessfully = false;
                var mpfResponse = response.data;
            	if(mpfResponse && mpfResponse.hasOwnProperty("responseCode") &&
            			mpfResponse.hasOwnProperty("message")) {			
        			if(mpfResponse.responseCode != 0) {				
        				alert("Error completing saving the node config with response: " + mpfResponse.message);        				
        			} else {
        				savedSuccessfully = true;
        			}        					
        		} else {
        			alert("Error getting a response from saving the node config.");        			
        		}              
            	
            	if(savedSuccessfully) {
                    //TODO: should consider clearing the logs page out when a new node config is loaded
            		//$log.debug(' successfully saved: ' + response.status );
            	}            	
            },
            function onError(response) {
                //$log.debug(' error when saving: ' + response.status );
            	alert("Error getting a successful response from saving the node config.");
            }
        )
    };

    $scope.$watch('nodes', function () {
        //$log.warn("nodes changed... about to reorder them");
        angular.forEach( $scope.nodes, function ( cart, ind ) {
            cart.services = $filter('orderBy')( cart.services, "serviceName" );
        });
    }, true );

    ///////////////////////////////////////////////
    // event handlers (listed alphabetically by event name)
    ///////////////////////////////////////////////


    $scope.$on('$destroy', function () {
        //$log.debug("$scope.$on('$destroy'...)")
    });

    angular.element(document).ready(function () {
        // this is the closest thing to jquery's $(document).ready
        //$log.debug("angular.element(document).ready()");
    });

    $scope.$on('UI_WINDOW_RESIZE', function(event, obj ) {
		$log.debug("UI_WINDOW_RESIZE message (received in AdminNodeConfigCtrl):  " + angular.toJson(obj));
        //TODO: P038: should not hard code this
        if ( obj.width > 1010 ) {
            $("#nodes-area").css("width","70%");
            $log.debug("changed to 70%");
        }
        else {
            $("#nodes-area").css("width","58%");
            $log.debug("changed to 58%");
        }
    });


    //////////////////////////////////////////////////////////////
    // initialize the controller
    //////////////////////////////////////////////////////////////

    $scope.init();
};
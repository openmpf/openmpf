/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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
 * AdminNodesCtrl
 * @constructor
 */
var AdminNodesCtrl = function ($scope, $log, $filter, $http, $timeout, $confirm, RoleService, ServicesCatalogService, NodeService) {
    $scope.isAdmin = false;     // set in init()
    $scope.serviceCatalog = [];
    $scope.addServiceSelected = null;
    $scope.nodes = []; // the existing nodes and services currently running
    $scope.counters = {};
    $scope.btns_disabled = false;
    var service_actions_counter = 0;
    var default_cursor = document.body.style.cursor;
    var configurations = [];    // the nodes' configurations

    // use a timer to prevent the user from performing any more actions until the current action completes or times out
    var waitTimeout = null;
    var waitTimeoutDelay = 30000; //30 sec

    var open_services={};

    //// Operations ////
    var init = function () {
        service_actions_counter = 0;
        RoleService.getRoleInfo().then(function (roleInfo) {
            $scope.isAdmin = roleInfo.admin;
            $log.debug("$scope.isAdmin=" + $scope.isAdmin);

            ServicesCatalogService.getServicesCatalog(true).then(function (data) {
                $log.debug("serviceCatalog", data);//angular.toJson(data)
                $scope.serviceCatalog = $filter('orderBy')(data, 'serviceName');
            });

            getConfigs();

            updateServices();
        });
    };

    //get services from the server and build the model for the ui
    var updateServices = function () {
        $scope.btns_disabled = true;

        NodeService.getNodeConfigs().then(function (configs) {
            NodeService.getServices().then(function (data) {

                // update stored configurations
                configurations = configs;

                //there may be some hosts with 0 services, need to add those hosts
                angular.forEach(configs, function (config) {
                    var found = false;
                    for (var i = 0; i < $scope.nodes.length; i++) {
                        var existing_host_data = $scope.nodes[i];
                        if (existing_host_data.name == config.host) {
                            $scope.nodes[i].online = config.online;
                            $scope.nodes[i].autoConfigured = config.autoConfigured;
                            $scope.nodes[i].updated = true;
                            found = true;
                        }
                    }
                    //create new node
                    if (!found) {
                         $scope.nodes.push({name: config.host, core: config.coreNode, online: config.online, autoConfigured: config.autoConfigured, serviceGroups: [], updated: true});
                    }
                });

                //$log.debug("NodeManager updateServices results ", data);
                if (data && data.nodeModels) { //should be array {"name":"localhost.localdomain:SphinxSpeechDetection:1","rank":1,"lastKnownState":"Running","unlaunchable":false,"kind":"generic","serviceCount":1,"restartCount":0}

                    $scope.counters = {};//counters for the headers

                    angular.forEach(data.nodeModels, function (ele) {
                        var info = ele.name.split(":");
                        var full_host_name = info[0];
                        var service_name = info[1];//service name
                        ele.id = parseInt(info[2]);
                        ele.updated = true;

                        if (!$scope.counters.hasOwnProperty(full_host_name)) {
                            $scope.counters[full_host_name] = {count: 0, stopped: 0, errors: 0};
                        }
                        $scope.counters[full_host_name].count++;

                        //states  Unknown, Configured, Launching, Running, ShuttingDown, ShuttingDownNoRestart, Inactive, InactiveNoStart, Delete, DeleteInactive
                        ele.isRunning = false;

                        if (ele.lastKnownState.startsWith("Running")) {
                            ele.isRunning = true;
                        } else if (ele.unlaunchable) {
                            $scope.counters[full_host_name].errors++;
                        }
                        if (ele.lastKnownState.startsWith("ShuttingDown") || ele.lastKnownState.startsWith("Inactive") || ele.lastKnownState.startsWith("Configured") || ele.lastKnownState.startsWith("Launching")) {
                            $scope.counters[full_host_name].stopped++;
                        }

                        //create or update the model without recreating so the ui can render appropriately
                        //convert to arrays for ng-repeats   {[name:"",serviceGroups:[{name:"",serviceList:[]}]]}
                        var host_data = null;
                        angular.forEach($scope.nodes, function (existing_host_data) {
                            if (existing_host_data.name == full_host_name) {
                                host_data = existing_host_data;
                            }
                        });
                        //create new node
                        if (host_data == null) {
                            host_data = {name: full_host_name, serviceGroups: []};
                            $scope.nodes.push(host_data);
                        }
                        host_data.updated = true;

                        //update the service info
                        var service_group_info = null;
                        angular.forEach(host_data.serviceGroups, function (service_group_data) {
                            if (service_group_data.name == service_name) {
                                service_group_info = service_group_data;
                            }
                        });
                        if (service_group_info == null) {
                            service_group_info = {name: service_name, serviceList: []};
                            host_data.serviceGroups.push(service_group_info);
                        }
                        service_group_info.updated = true;

                        //update the service
                        var service_info = null;
                        var index1 = -1;
                        angular.forEach(service_group_info.serviceList, function (service_data, idx) {
                            if (service_data.name == ele.name) {
                                service_info = ele;
                                index1 = idx;
                            }
                        });

                        if (service_info == null) {
                            service_group_info.serviceList.push(ele);
                        } else {
                            service_group_info.serviceList[index1] = ele;
                        }
                    });

                    //remove the old data from the existing model if it was not updated
                    for (var i = $scope.nodes.length - 1; i >= 0; i--) {
                        var host_data = $scope.nodes[i];
                        if (host_data.updated) {
                            for (var j = host_data.serviceGroups.length - 1; j >= 0; j--) {
                                var service_group_data = host_data.serviceGroups[j];
                                if (service_group_data.updated) {
                                    for (var k = service_group_data.serviceList.length - 1; k >= 0; k--) {
                                        var service_data = service_group_data.serviceList[k];
                                        if (service_data.updated) {
                                            delete service_data.updated;
                                        } else {
                                            service_group_data.serviceList.splice(k, 1);
                                        }
                                    }
                                    delete service_group_data.updated;
                                } else {
                                    host_data.serviceGroups.splice(j, 1);
                                }
                            }
                            delete host_data.updated;
                        } else {
                            $scope.nodes.splice(i, 1);
                        }
                    }

                    //if we're not waiting for any service actions to complete, then change the cursor & enable buttons
                    if (service_actions_counter < 0) service_actions_counter = 0;
                    if (service_actions_counter <= 0) {
                        document.body.style.cursor = default_cursor;
                        $scope.btns_disabled = false;
                    }
                    //update any open dropdowns
                    for(var host1 in open_services){
                        var ct = open_services[host1];
                        $scope.addServiceBtnChange(host1,ct);
                    }
                } else {
                    $log.debug("No data", data);
                }
            });
        });
    };

    //debounce the updateServices() call so that it will only be invoked once after receiving multiple
    //node and service event broadcasts in a row
    var lazyUpdateServices = _.debounce(updateServices, 1000);

    // Actions
    $scope.nodeServiceInfo = function (nodeServiceList) {
        var errors = 0;
        var stopped = 0;
        angular.forEach(nodeServiceList, function (node) {
            if (node.unlaunchable && !node.isRunning) errors++;
            if (!node.isRunning) stopped++;
        });
        return {errors: errors, stopped: stopped};
    };

    $scope.editServices = function (host, idx) {
        $log.debug("editServices", host);
        var service = $("#add_service_" + idx).val();
        var count = $("#add_service_count_" + idx).val();

        for (var j = 0; j < configurations.length; j++) {
            var config_host = configurations[j];
            if (host == config_host.host) {
                //see if its already in the config
                var found = false;
                for (var i = 0; i < config_host.services.length; i++) {
                    var host_service = config_host.services[i];
                    if (host_service.serviceName == service) {
                        configurations[j].services[i].serviceCount = parseInt(count);
                        configurations[j].autoConfigured = false;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    var services = $filter('filter')($scope.serviceCatalog, {serviceName: service});
                    services[0].serviceCount = parseInt(count);
                    $.merge(config_host.services, services);
                    config_host.autoConfigured = false;
                }
            }
        }
        saveConfigs();
    };

    $scope.removeAllNodeServices = function (host, nodename) {
        $log.debug("removeAllNodeServices", host + ":" + nodename);
        for (var j = 0; j < configurations.length; j++) {
            var config_host = configurations[j];
            if (host == config_host.host) {
                for (var i = 0; i < config_host.services.length; i++) {
                    var host_service = config_host.services[i];
                    if (host_service.serviceName == nodename) {
                        configurations[j].services[i].serviceCount = 0;
                        configurations[j].autoConfigured = false;
                        break;
                    }
                }
            }
        }
        saveConfigs();
    };

    $scope.removeService = function (service) {
        $log.debug("removeService", service);
        var info = service.name.split(":");
        var host_name = info[0];
        var service_name = info[1];
        for (var j = 0; j < configurations.length; j++) {
            var config_host = configurations[j];
            if (host_name == config_host.host) {
                for (var i = 0; i < config_host.services.length; i++) {
                    var host_service = config_host.services[i];
                    if (host_service.serviceName == service_name) {
                        configurations[j].services[i].serviceCount--;
                        configurations[j].autoConfigured = false;
                        break;
                    }
                }
            }
        }
        saveConfigs();
    };

    $scope.addNode = function () {
        NodeService.getAllNodeHostnames().then(function (data) {
            $confirm({hostnames: getConfigurableHostnames(data)}, {templateUrl: 'add-node-dialog.html'})
                .then(function (selectedHostname) {
                    if (selectedHostname) {
                        var services = [];
                        if ($("#add_node_all_services").prop("checked")) services = angular.copy($scope.serviceCatalog);
                        var node = {"host": selectedHostname, "services": services};
                        configurations.push(node);
                        saveConfigs();
                    }
                });
        });
    };

    $scope.addAllServices = function (hostname) {
        var services = angular.copy($scope.serviceCatalog);
        var host = $filter('filter')(configurations, {host: hostname})[0];
        angular.forEach(services, function (service) {
            var exist = $filter('filter')(host.services, {serviceName: service.serviceName})[0];
            if (exist) {
                exist.serviceCount += 1;
            } else {
                service.serviceCount = 1;
                host.services.push(service);
            }
        });
        host.autoConfigured = false;
        $log.debug("configurations", configurations);
        saveConfigs();
    };

    $scope.removeNode = function (host) {
        $log.debug("removeNode", host);
        for (var j = configurations.length - 1; j >= 0; j--) {
            if (host == configurations[j].host) {
                configurations.splice(j, 1);
            }
        }
        saveConfigs();
    };

    var getConfigurableHostnames = function (nodeHostnames) {
        var configList = [];
        var out = [];

        angular.forEach(configurations, function (item) {
            configList.push(item.host)
        });
        angular.forEach(nodeHostnames, function (item) {
            if (configList.indexOf(item) < 0) {
                out.push(item)
            }
        });
        return out;
    };

    var getConfigs = function () {
        NodeService.getNodeConfigs().then(function (data) {
            $log.debug("getConfigs", data);
            configurations = data;
        });
    };

    var saveConfigs = function () {
        $log.debug('saveConfigs', configurations);
        startAction();
        var json = angular.toJson(configurations);
        NodeService.saveNodeConfigs(json).then(
            function onSuccess(response) {
                var savedSuccessfully = false;
                var resp = response.data;
                if (resp && resp.hasOwnProperty("responseCode") &&
                    resp.hasOwnProperty("message")) {
                    if (resp.responseCode != 0) {
                        alert("Error completing saving the node config with response: " + resp.message);
                    } else {
                        savedSuccessfully = true;
                    }
                } else {
                    alert("Error getting a response from saving the node config.");
                }

                if (savedSuccessfully) {
                    $log.debug(' successfully saved: ' + response.status);
                    service_actions_counter--;
                    updateServices();
                }
            },
            function onError(response) {
                alert("Error getting a successful response from saving the node config.");
            }
        )
    };

    var startAction = function () {
        document.body.style.cursor = 'wait';
        service_actions_counter++;
        $scope.btns_disabled = true;
        restartWaitTimer();
    };

    $scope.startService = function (service) {
        $log.debug("startService", service);
        if (!service.isRunning) {
            startAction();
            NodeService.startService(service.name);
        }
    };

    $scope.shutDownService = function (service) {
        $log.debug("shutDownService", service);
        if (service.isRunning) {
            startAction();
            NodeService.shutdownService(service.name);
        }
    };

    $scope.restartService = function (service) {
        $log.debug("restartService", service);
        if (service.isRunning) {
            startAction();
            service_actions_counter++;//need to add another counter for 2nd action
            NodeService.restartService(service.name);
        }
    };

    $scope.startAllNodeServices = function (services) {
        $log.debug("startAllNodeServices", services);
        angular.forEach(services, function (service) {
            $scope.startService(service);
        });
    };

    $scope.shutDownAllNodeServices = function (services) {
        $log.debug("shutDownAllNodeServices", services);
        angular.forEach(services, function (service) {
            $scope.shutDownService(service);
        });
    };

    $scope.restartAllNodeServices = function (services) {
        $log.debug("restartAllNodeServices", services);
        angular.forEach(services, function (service) {
            $scope.restartService(service);
        });
    };

    $scope.startAllHostServices = function (serviceGroups) {
        $log.debug("startAllHostServices", serviceGroups);
        angular.forEach(serviceGroups, function (group) {
            angular.forEach(group.serviceList, function (service) {
                $scope.startService(service);
            });
        });
    };

    $scope.shutDownAllHostServices = function (serviceGroups) {
        $log.debug("shutDownAllHostServices", serviceGroups);
        angular.forEach(serviceGroups, function (group) {
            angular.forEach(group.serviceList, function (service) {
                $scope.shutDownService(service);
            });
        });
    };

    $scope.restartAllHostServices = function (serviceGroups) {
        $log.debug("restartAllHostServices", serviceGroups);
        angular.forEach(serviceGroups, function (group) {
            angular.forEach(group.serviceList, function (service) {
                $scope.restartService(service);
            });
        });
    };

    $scope.toggleChevron = function ($event) {
        $($event.currentTarget)
            .find("i.indicator")
            .toggleClass('glyphicon-chevron-right glyphicon-chevron-down');
    };

    var restartWaitTimer = function () {
        stopWaitTimer();
        waitTimeout = $timeout(waitTimerExpired, waitTimeoutDelay);
    };

    var stopWaitTimer = function () {
        if (waitTimeout != null) {
            $timeout.cancel(waitTimeout);
            waitTimeout = null;
        }
    };

    var waitTimerExpired = function () {
        $scope.btns_disabled = false;
        service_actions_counter = 0;
        document.body.style.cursor = default_cursor;
    };

    //// event handlers (listed alphabetically by event name)  ////

    //on a node service change status, update the model
    $scope.$on('SSPC_SERVICE', function (event, msg) {
        $log.debug("SSPC_SERVICE (in nodes and processes page): " + JSON.stringify(msg));
        service_actions_counter--;
        lazyUpdateServices();
    });

    //on a node change status (offline to online, and vice versa), update the model
    $scope.$on('SSPC_NODE', function (event, msg) {
        $log.debug("SSPC_NODE (in nodes and processes page): " + JSON.stringify(msg));
        lazyUpdateServices();
    });

    $scope.$on('$destroy', function () {
        stopWaitTimer();
    });

    $scope.addServiceBtnClick = function (hostname,index) {
        if(open_services[hostname]){
            delete open_services[hostname];
            return;
        } else{
            open_services[hostname]=index;
        }
        var config = $filter('filter')(configurations, {host: hostname});
        if ($scope.serviceCatalog && $scope.serviceCatalog[0]) {
            var val = $scope.serviceCatalog[0].serviceName;
            $("#add_service_"+index).val(val);
            $scope.addServiceBtnChange(hostname,index);
        }
    };

    $scope.addServiceBtnChange = function (hostname,index) {
        var config = $filter('filter')(configurations, {host: hostname});
        if (config && config[0]) {
            var selected = $("#add_service_"+index).val();
            var service = $filter('filter')(config[0].services, {serviceName: selected});
            if (service && service[0] && service[0].serviceCount > 0) {
                var count = service[0].serviceCount;
                $("#add_service_count_"+index).val(count);
            } else {
                $("#add_service_count_"+index).val(1);
            }
        }
    };

    //// initialize the controller ////
    init();
};

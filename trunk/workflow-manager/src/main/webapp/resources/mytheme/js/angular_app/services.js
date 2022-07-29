/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

/* Angular Services */
var AppServices = angular.module('mpf.wfm.services', ['ngResource']);


AppServices.factory('MetadataService', [
    '$http', 'ClientState',
    function ($http, ClientState) {
        var getMetadataNoCache = function () {
            return $http.get('info')
                .then(function (response) {
                    ClientState.setConnectionState(ClientState.ConnectionState.CONNECTED_SERVER);
                    return response.data;
                })
                .catch(function () {
                    ClientState.setConnectionState(ClientState.ConnectionState.DISCONNECTED_SERVER);
                });
        };

        var cachedPromise = getMetadataNoCache();

        return {
            getMetadata: function () {
                return cachedPromise;
            },
            getMetadataNoCache: getMetadataNoCache
        };
    }
]);


AppServices.factory('RoleService', [
    '$http',
    function ($http) {
        var roleInfoPromise = $http.get('user/role-info')
            .then(function (response) {
                return response.data;
            });

        return {
            getRoleInfo: function () {
                return roleInfoPromise;
            }
        }
    }
]);


AppServices.service('PipelinesService', function ($http) {
    this.getAvailablePipelines = function () {
        // $http returns a promise, which has a then function, which also returns a promise
        var promise = $http.get('pipelines').then(function (response) {
            // The then function here is an opportunity to modify the response
            //console.log('available_pipelines: ', response);
            // The return value gets picked up by the then in the controller.
            return response.data;
        });
        // Return the promise to the controller
        return promise;
    };
});

AppServices.config(['$resourceProvider', function ($resourceProvider) {
    // Don't strip trailing slashes from calculated URLs
    $resourceProvider.defaults.stripTrailingSlashes = false;
}]);


AppServices.factory('Components',
    ['$resource',
        function ($resource) {
            var componentsResource = $resource('components/:packageFileName/', { packageFileName: '@packageFileName'}, {
                register: {
                    method: 'POST',
                    url: 'components/:packageFileName/register',
                    params: {
                        packageFileName: '@packageFileName'
                    }

                },
                delete: {
                    method: 'DELETE',
                    url: 'components/:componentName/',
                    params: {
                        componentName: '@componentName'
                    }
                },
                removePackage: {
                    method: 'DELETE',
                    url: 'components/packages/:packageFileName/',
                    params: {
                        packageFileName: '@packageFileName'
                    }
                },
                reRegister: {
                    method: 'POST',
                    url: 'components/:packageFileName/reRegister',
                    params: {
	                    packageFileName: '@packageFileName'
                    }
                }
            });

            return {
            	newComponent: function (obj) {
                    return new componentsResource(obj);
                },
                query: function () {
                    return componentsResource.query();
                },
                register: function (packageFileName) {
                    return componentsResource.register({packageFileName: packageFileName});
                },
                remove: function (component) {
                    if (component.componentName) {
                        return component.$delete();
                    }
                    else {
                        return component.$removePackage();
                    }
                },
                getReRegisterOrder: function (packageFileName) {
            		return componentsResource.getReRegisterOrder({packageFileName: packageFileName});
                },
                statesEnum: {
                    UPLOADING: 'UPLOADING',
                    UPLOADED: 'UPLOADED',
                    UPLOAD_ERROR: 'UPLOAD_ERROR',
                    REGISTERING: 'REGISTERING',
                    REGISTERED: 'REGISTERED',
                    REGISTER_ERROR: 'REGISTER_ERROR',
                    REMOVING: 'REMOVING',
                    RE_REGISTERING: 'RE_REGISTERING',
                    DEPLOYED: 'DEPLOYED'
                }
            };
        }]
);

AppServices.service('MediaService', function ($http) {

    this.getMaxFileUploadCnt = function () {
        var promise = $http.get('upload/max-file-upload-cnt').then(function (response) {
            return response.data;
        });
        return promise;
    };

    this.getAllDirectories = function (useUploadRoot, useCache) {
        var useUploadRoot = useUploadRoot ? useUploadRoot : false;
        var useCache = (useCache !== undefined) ? useCache : true;
        var promise = $http({
            url: 'server/get-all-directories',
            method: 'GET',
            params: {'useUploadRoot': useUploadRoot, 'useCache': useCache}
        }).then(function (response) {
            return response.data;
        });
        return promise;
    };
    this.getAllFiles = function (fullPath, useCache) {
        var useCache = (useCache !== undefined) ? useCache : true;
        var promise = $http({
            url: 'server/get-all-files',
            method: 'GET',
            params: {'fullPath': fullPath, 'useCache': useCache}
        }).then(function (response) {
            return response.data;
        });
        return promise;
    };

    this.createDirectory = function (serverpath) {
        var path = serverpath;
        var promise = $http({
            url: 'media/create-remote-media-directory',
            method: "POST",
            params: {'serverpath': path}
        }).then(
            function (response) {
                return response.data;
            },
            function (httpError) {
                throw httpError.data.error;
            });
        return promise;
    };

    this.createJobFromMedia = function (jobCreationRequest) {
        var promise = $http({
            url: 'jobs',
            method: "POST",
            data: jobCreationRequest
        }).then(function (response) {
            return response.data;
        });
        return promise;
    };
});

AppServices.service('JobPriorityService', function ($http) {
    this.getDefaultJobPriority = function () {
        //need transformResponse because an int as JSON is returned
        var promise = $http({
            url: 'properties/job-priority',
            method: 'GET',
            transformResponse: [function (data) {
                return data;
            }]
        });
        return promise;
    };
});

AppServices.service('JobsService', function ($http) {

    this.getJobsList = function (isSession) {
        var promise = $http({
            url: 'jobs',
            method: "GET",
            params: {'useSession': isSession}
        }).then(function (response) {
            return response.data;
        });
        return promise;
    };

    this.resolveUrlWithId = function (url, id) {
        if (typeof id !== "undefined") {
            url = url + "/" + id;
        }
        return url;
    }

    this.getJob = function (id, isSession) {
        if (isSession === undefined) {
            isSession = false;
        }

        // jobs
        var url = this.resolveUrlWithId('jobs', id);

        var promise = $http({
            url: url,
            method: "GET",
            params: {'useSession': isSession}
        }).then(function (response) {
            //returns one job info object
            return response.data;
        });
        return promise;
    };

    this.resubmitJob = function (jobId, jobPriority) {
        // jobs/{id}/resubmit
        var url = 'jobs/' + jobId + '/resubmit';

        var promise = $http({
            url: url,
            method: "POST",
            params: {'jobPriority': jobPriority}
        }).then(function (response) {
            return response.data;
        });
        return promise;
    };

    this.cancelJob = function (jobId) {
        // jobs/{id}/cancel
        var promise = $http({
            url: 'jobs/' + jobId + '/cancel',
            method: "POST"
        }).then(function (response) {
            return response.data;
        });
        return promise;
    };

    this.getStats = function () {
        var promise = $http({
            url: 'jobs/stats',
            method: "GET"
        }).then(function (response) {
            //there is only one job, but it will still be returned in array
            //... because it is using the same jobs method
            return response.data;
        });
        return promise;
    };

    this.getOutputObject = function (jobId) {
        var promise = $http({
            url: 'jobs/output-object?id='+jobId,
            method: "GET"
        }).then(function (response) {
            return response.data;
        });
        return promise;
    };

    this.tiesDbRepost = function (jobId) {
        return $http.post('jobs/tiesdbrepost', [jobId]);
    };
});

AppServices.service('NodeService', function ($http, $timeout, $log,$filter) {

    //reload the data via ajax
    this.refreshData = function () {
        var promise = $http.get("nodes/info").then(function (response) {
            return response.data;
        });
        return promise;
    };

    //http saves json to server
    //@param json - the data to be saved in JSON format
    this.saveNodeConfigs= function(json){
        var promise = $http.post("nodes/config", json);
        return promise;
    };

    //http gets the configuration of services for all nodes
    this.getNodeConfigs= function(){
        var promise = $http.get("nodes/config").then(function (response) {
            return response.data;
        });
        return promise;
    };

    // http gets the hostnames of all nodes
    this.getAllNodeHostnames= function (type = "all") {
        var promise = $http.get("nodes/all?type=" + type).then(function (response) {
            var nodesList = [];
            angular.forEach(response.data, function (obj) {
                // deduplicate entries: CORE_MPF_NODES may specify the master node host twice
                // because the configure script requires you to enter the hostname for the parent node
                // to have a nodemanager
                if (nodesList.indexOf(obj) == -1) {
                    nodesList.push(obj);
                }
            });

            // sort entries
            nodesList = $filter('orderBy')(nodesList);
                return nodesList;
            });
        return promise;
    };

    /** async call returns array of NodeManager hostnames */
    this.getNodeManagerHostnames = function () {
        var promise = $http({
            url: 'nodes/hosts',
            method: "GET"
        }).then(
            function (response) {
                //console.log("getNodeManagerHosts->"+JSON.stringify(response.data));
                return Object.keys(response.data);
            },
            function (httpError) {
                throw httpError.status + " : " + httpError.data;
            });
        return promise;
    };

    /** async (private) call returns hostname of master node */
    var getMasterNodeHostnamePromise;	// lazy cache initialization
    this.getMasterNodeHostname = function () {
        if (!getMasterNodeHostnamePromise) {
            getMasterNodeHostnamePromise = $http({
                url: 'nodes/master-node',
                method: "GET"
            }).then(
                function (response) {
                    //console.log("getNodeManagerHosts->"+JSON.stringify(response.data));
                    return response.data['master-node'];
                },
                function (httpError) {
                    throw httpError.status + " : " + httpError.data;
                });
        }
        return getMasterNodeHostnamePromise;
    };

    /** async call returns list of all services running in the system */
    this.getServices = function () {
        var promise = $http({
            url: 'nodes/info',
            method: "GET"
        }).then(
            function (response) {
                //console.log("getNodeManagerInfo->"+JSON.stringify(response.data));
                return response.data;
            },
            function (httpError) {
                throw httpError.status + " : " + httpError.data;
            });
        return promise;
    };

    // service/start and service/stop return MpfResponse
    /* {
     "message": "string",
     "responseCode": 0 (success) or 1 (error)
     } */
    //errorMessage will be populated if saveNodeConfigSuccess is not true
    this.checkMpfResponse = function (mpfResponse) {
        if (mpfResponse && mpfResponse.hasOwnProperty("responseCode") &&
            mpfResponse.hasOwnProperty("message")) {
            if (mpfResponse.responseCode != 0) {
                alert("Error completing service status change request with message: " +
                    mpfResponse.message);
                return false;
            }
            return true;
        } else {
            alert("Error getting a response from changing the service status.");
            return false;
        }
    };

    //ajax call to start a service
    this.startService = function (servicename) {
        var promise = $http.post("nodes/services/" + servicename + "/start");
        return promise;
    };

    //ajax call to shutdown a service
    this.shutdownService = function ( servicename) {
        var promise = $http.post("nodes/services/" + servicename + "/stop");
        return promise;
    };

    //ajax call to restart a service
    this.restartService = function (servicename) {
        var self = this;
        var promise = $http.post("nodes/services/" + servicename + "/stop").then(function (response) {
            self.startService(servicename);
        });
        return promise;
    };

});

AppServices.factory('ServerSidePush',
    ['$rootScope', '$log', 'Components', 'JobsService', 'NotificationSvc', 'TimeoutSvc', 'SystemNotices', 'SystemStatus', 'ClientState',
        function ($rootScope, $log, Components, JobsService, NotificationSvc, TimeoutSvc, SystemNotices, SystemStatus, ClientState) {
            var request;
            var serviceInstance = null;
            var lastSystemHealthTimestamp = null;	// timestamp from SSPC_HEARTBEAT/SSPC_ATMOSPHERE messages

            var ServerSidePushService = {};

            // initializes service; should only be called once by the service upon first instantiation
            ServerSidePushService.init = function () {
                if (!serviceInstance) {
                    serviceInstance = ServerSidePushService.connect();
                }
            };

            ServerSidePushService.connect = function () {
                var baseUrl = location.href;
                baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf('workflow-manager/'));

                request = {
                    url: baseUrl + 'workflow-manager/websocket',
                    contentType: "application/json",
                    logLevel: 'debug',
                    transport: 'websocket',
                    trackMessageLength: true,
                    reconnectInterval: 1000, // retry a broken connection after n milliseconds
                    maxRequest: 5,	// try reconnection after network/server outage n times
                    // so recoonectInterval * maxRequest = max outage expectations
                    // 1000 * 5 is estimate for intermittent LAN/wifi networks, but not good enough for mobile
                    // for server outages, we want the client to stop trying after this estimate
                    // if the server returns before the reconnect times out, the user will be
                    //	be sent to login page
                    fallbackTransport: 'long-polling'
                };	// default; possible values are: polling, long-polling, streaming, jsonp, sse and websocket

                request.onOpen = function (response) {
                    ClientState.setConnectionState(ClientState.ConnectionState.CONNECTED_SERVER);
                    lastSystemHealthTimestamp = moment();
                    $log.info('Atmosphere connected using ' + response.transport + " @ " + lastSystemHealthTimestamp.format());
                    var json = {};
                    json.channel = 'SSPC_ATMOSPHERE';
                    json.event = 'OnServerConnectionOpen'
                    json.timestamp = lastSystemHealthTimestamp;
                    json.content = response;
                    $rootScope.$broadcast('SSPC_ATMOSPHERE', json);
                };

                request.onMessage = function (response) {
                    var json;
                    var message = response.responseBody;
                    $rootScope.lastServerExchangeTimestamp = moment();
                    if (message === "X") {	// heartbeat message
                        // create a heartbeat json structure to fit with rest of messages
                        json = {};
                        json.channel = 'SSPC_HEARTBEAT';
                        json.timestamp = new Date();
                        json.content = {};
                    }
                    else	// normal wfm/Atmosphere message
                    {
                        try {
                            json = jQuery.parseJSON(message);
                            // this only parses the "header" portion of the message, the content is still a string
                        } catch (e) {
                            console.log('Atmosphere:  This doesn\'t look like JSON: ', message);
                            return;
                        }
                    }
                    if (json.event !== 'OnServerConnectionLost')	// don't update server-side-push activity if it is locally generated
                    {
                        lastSystemHealthTimestamp = moment(json.timestamp);
                    }
                    switch (json.channel) {
                        case 'SSPC_ATMOSPHERE':
                            $rootScope.$broadcast('SSPC_ATMOSPHERE', json);
//    	    		console.log("SSPC_ATMOSPHERE message received: " + JSON.stringify(json,2,null));
                            break;
                        case 'SSPC_HEARTBEAT':
                            $rootScope.$broadcast('SSPC_HEARTBEAT', json);
                            break;
                        case 'SSPC_JOBSTATUS':
                            // still broadcast for cancellations
                            $rootScope.$broadcast('SSPC_JOBSTATUS', json);

                            if (json) {
                                var msg = json.content;
                                if (msg && msg.id != -1 && msg.progress != -1) {

                                    // if in terminal state
                                    if (msg.jobStatus == 'COMPLETE' ||
                                        msg.jobStatus == 'COMPLETE_WITH_ERRORS' ||
                                        msg.jobStatus == 'COMPLETE_WITH_WARNINGS' ||
                                        msg.jobStatus == 'ERROR' ||
                                        msg.jobStatus == 'UNKNOWN') {

                                        // ensure job is part of session to avoid flooding the UI with notifications
                                        JobsService.getJob(msg.id, true).then(function (job) {
                                            if (job) {
                                                if (msg.jobStatus == 'COMPLETE') {
                                                    console.log('job complete for id: ' + msg.id);
                                                    NotificationSvc.success('Job ' + msg.id + ' is now complete!');
                                                } else if (msg.jobStatus == 'COMPLETE_WITH_ERRORS') {
                                                    console.log('job complete (with errors) for id: ' + msg.id);
                                                    NotificationSvc.error('Job ' + msg.id + ' is now complete (with errors).');
                                                } else if (msg.jobStatus == 'COMPLETE_WITH_WARNINGS') {
                                                    console.log('job complete (with warnings) for id: ' + msg.id);
                                                    NotificationSvc.warning('Job ' + msg.id + ' is now complete (with warnings).');
                                                } else if (msg.jobStatus == 'ERROR') {
                                                    console.log('job ' + msg.id + ' is in a critical error state');
                                                    NotificationSvc.error('Job ' + msg.id + ' is in a critical error state. Check the Workflow Manager log for details.');
                                                } else if (msg.jobStatus == 'UNKNOWN') {
                                                    console.log('job ' + msg.id + ' is in an unknown state');
                                                    NotificationSvc.info('Job ' + msg.id + ' is in an unknown state. Check the Workflow Manager log for details.');
                                                }
                                            }
                                        });
                                    }
                                }
                            }

                            break;
                        case 'SSPC_NODE':
                            $rootScope.$broadcast('SSPC_NODE', json);
                            //console.log("SSPC_NODE message received: " + JSON.stringify(json,2,null));
                            break;
                        case 'SSPC_SERVICE':
                            $rootScope.$broadcast('SSPC_SERVICE', json);
                            //console.log("SSPC_SERVICE message received: " + JSON.stringify(json,2,null));
                            break;
                        case 'SSPC_SESSION':
                            $rootScope.$broadcast('SSPC_SESSION', json);
                            //console.log("SSPC_SESSION message received: " + JSON.stringify(json,2,null));
                            TimeoutSvc.warn(json);
                            break;
                        case 'SSPC_SYSTEMMESSAGE':
                            $rootScope.$broadcast('SSPC_SYSTEMMESSAGE', json);
                            //console.log("SSPC_SYSTEMMESSAGE message received: " + JSON.stringify(json,2,null));
                            SystemStatus.showAllSystemMessages();
                            break;
                        case 'SSPC_PROPERTIES_CHANGED':
                            $rootScope.$broadcast('SSPC_PROPERTIES_CHANGED');
                            break;
                        case 'SSPC_CALLBACK_STATUS':
                            $rootScope.$broadcast('SSPC_CALLBACK_STATUS', json);
                            break;
                        default:
                            console.log("Message received on unknonwn SSPC (Atmosphere server-side push) channel: " + JSON.stringify(json, 2, null));
                            break;
                    }
                };

                request.onClose = function (event) {
                    if (ClientState.isConnectionActive()) {
                        // create a onClose json structure to fit with rest of messages
                        var json = {};
                        json.channel = 'SSPC_ATMOSPHERE';
                        json.event = 'OnServerConnectionLost';
                        json.timestamp = new Date();
                        json.content = event;
                        $rootScope.$broadcast('SSPC_ATMOSPHERE', json);
                        ClientState.setConnectionState(ClientState.ConnectionState.DISCONNECTED_SERVER);
                        //    		$.atmosphere.unsubscribe(request);
                    }
                };

                /** event handler for error conditions, which according to the documentation are the following 2 cases
                 *    1.  reconnection retries reached max
                 *  2.  unexpected error
                 */
                request.onError = function (event) {
                    $log.error("ServerSidePush.onError:" + angular.toJson(event));
                    SystemNotices.remove("SYSTEM_NOTIFY_RECONNECTING");	// no longer trying to reconnect
                    ClientState.setConnectionState(ClientState.ConnectionState.DISCONNECTED_SERVER);
                };

                /*P038: todo: this is supposed to be called when a websocket reconnects, but I'm not able to test this since
                 * using Chrome's network throttling does not really turn off the network (and thus, this function is not triggered)
                 * and turning off CentOS's network does not affect localhost (and thus, this function is not triggered)
                 * so currently, I'm implementing it by using the polling /info call that is used for session management,
                 * which has the nice feature that we can tell a network problem (polling encounters errors) vs. a server
                 * problem (atmosphere attempts to reconnect)
                 */
                request.onReopen = function (event) {
                    console.log("ServerSidePush.onReopen:" + angular.toJson(event));
                    ClientState.setConnectionState(ClientState.ConnectionState.CONNECTED_SERVER);
                };

                /** called (counter-intuitively) when atmosphere ATTEMPTS to reconnect, and is NOT called
                 *  when the reconnect is successful. That event, is onReopen().
                 */
                request.onReconnect = function (event) {
                    $log.debug("ServerSidePush.onReconnect:" + angular.toJson(event));
                    ClientState.setConnectionState(ClientState.ConnectionState.DISCONNECTED_NETWORK);
                    //SystemNotices.standardMessage("SYSTEM_NOTIFY_RECONNECTING");
                };

                var socket = $.atmosphere;
                var subSocket = socket.subscribe(request);
            };

            // lazy initialize on first use
            ServerSidePushService.getRequest = function () {
                if (!serviceInstance) {
                    console.log("initializing SSP, should not be doing this at this point, but for some reason the service didn't initialize");
                    ServerSidePushService.init();
                }
                return request;
            };

            // gets formatted timestamp
            ServerSidePushService.getLastSystemHealthTimestamp = function () {
                if (lastSystemHealthTimestamp) {
                    return lastSystemHealthTimestamp.format("hh:mm:ss a");
                }
                else {
                    return "";
                }
            };

            // initializes service; should only be called once by the service upon first instantiation
            ServerSidePushService.init();

            return ServerSidePushService;
        }]);

AppServices.factory('TimerService', ['$interval', function ($interval) {

    var registrants = {},
        internalInterval = 1000;

    var start = function () {
        $interval(service.tick, internalInterval);
        service.tick();
    };

    var service = {
        register: function (id, tickHandler, interval, delay) {
            console.log("[TimerService] - register -" + id);
            registrants[id] = {
                tick: tickHandler,        // tick handler function.
                interval: interval,       // configured interval.
                delay: delay              // delay until first tick.
            };
        },

        unregister: function (id) {
            delete registrants[id];
            //console.log("[TimerService] - unregister -"+id);
        },

        tick: function () {
            angular.forEach(registrants, function (registrant) {
                // update the delay.
                registrant.delay -= internalInterval;

                if (registrant.delay <= 0) {
                    // time to tick!
                    registrant.tick();
                    //reset delay to configured interval
                    registrant.delay = registrant.interval;
                }
            });
        }
    };

    start();

    return service;
}]);



AppServices.service('LogService', function ($http) {
    //globals
    this.lastNodeSelection = undefined;
    this.lastLogSelection = undefined;
    this.lastLogLevelSelection = undefined;
    this.lastAllLogText = undefined;
    this.lastLogResponseInfo = undefined;

    //reload the data via ajax
    this.getLogsMap = function () {
        var promise = $http.get("adminLogsMap").then(function (response) {
            return response.data;
        });
        return promise;
    };

    this.getLogs = function (params) {
        var promise = $http({
            url: "adminLogsUpdate",
            method: "GET",
            params: params
        }).then(function (response) {
            return response.data;
        });
        return promise;
    }

});

AppServices.factory('httpInterceptor',
['$q', 'NotificationSvc',
function ($q, NotificationSvc) {
    return {
        requestError: function (response) {
            console.log("httpInterceptor requestError", response);
            return $q.reject(response);
        },
        // optional method
        responseError: function (response) {
            console.log("httpInterceptor responseError", response);
            // NOTE: FireFox version 42.0 (and possibly others) has a strange issue that results in processing the wrong
            // status error code. This only happens when accessing a remote server. It does not happen when accessing
            // localhost. Specifically, status 901 is processed as 65413, and status 902 is processed as 65414.
            // Seems related to integer rollover.
            if (response.status === 901 || response.status === 65413) {
                window.top.location.href = 'logout?reason=timeout';
            } else if (response.status === 902 || response.status === 65414) {
                window.top.location.href = 'logout?reason=bootout';
            }

            var respData = response.data;
            if (respData && respData.message && respData.uncaughtError) {
                NotificationSvc.error(respData.message);
            }
            return $q.reject(response);
        }
    };
}]);

/** service for working with the catalog of services used when configuration a node (e.g., in the admin node config page) */
AppServices.factory('ServicesCatalogService', function ($http, $log, $filter) {
    // constants
    var _serviceJsonTemplateUrl = "nodes/services";

    // cached promise objects containing data from the server
    var _catalogPromise;

    return {
        /** http gets the services catalog from the server
         * @param refetch - boolean to specify if this should drop the cached value and get the catalog afresh from the server
         * @returns {*}
         */
        getServicesCatalog: function (refetch) {
            if (refetch || !_catalogPromise) {
                _catalogPromise = $http.get(_serviceJsonTemplateUrl).then(function (response) {
                    //$log.debug('using $http to fetch : ', _serviceJsonTemplateUrl);
                    //$log.debug('  returned data=', response.data);

                    var inputCatalog = response.data;
                    var catalog = [];

                    // convert catalog object of key/value pairs into array of just values for easier access
                    for (var key in inputCatalog) {
                        catalog.push(inputCatalog[key]);
                    }
                    // sort entries
                    catalog = $filter('orderBy')(catalog, 'serviceName');

                    return catalog;
                });
            }
            // Return the promise to the controller
            return _catalogPromise;
        }
    }
});



AppServices.factory('NotificationSvc', [
    function () {

        // Wrap lines in divs so they show up in separate lines in the notification
        var wrapLinesInDiv = function (message) {
            var msgLines = message.split('\n');
            var msgDivList = _.map(msgLines, function (line) {
                return "<div>" + line + "</div>"
            });
            return msgDivList.join('');

        };

        var generateNotyAlert = function (type, message, layout) {
            var msgHtml = wrapLinesInDiv(message);
            noty({
                text: msgHtml,
                type: type,
                dismissQueue: true,
                layout: layout || "topRight",
                theme: 'defaultTheme',
                buttons: [
                    {
                        addClass: 'btn btn-primary',
                        text: 'Ok',
                        onClick: function (notification) {
                            notification.close();
                        }
                    },
                    {
                        addClass: 'btn btn-warning',
                        text: 'Close All',
                        onClick: function () {
                            $.noty.closeAll();
                        }
                    }
                ]
            });
        };

        return {
            error: function (message, layout) {
                generateNotyAlert("error", message, layout);
            },
            warning: function (message, layout) {
                generateNotyAlert("warning", message, layout);
            },
            success: function (message, layout) {
                generateNotyAlert("success", message, layout);
            },
            info: function (message, layout) {
                generateNotyAlert("information", message, layout);
            }
        }
    }
]);

AppServices.factory('TimeoutSvc', ['$confirm', '$rootScope', '$log', '$http',
    function ($confirm, $rootScope, $log, $http) {
        return {
            warn: function (json) {
                if (json.event === 'OnSessionAboutToTimeout') {
                    var timeLeftInSecs = json.content.timeLeftInSecs;
                    $rootScope.timeoutInfo = {
                        secs: timeLeftInSecs - 2,	// subtract 2 more seconds because it seems to always be 2 seconds slower
                        max: json.content.warningPeriod	// usually 60 seconds
                    };
                    //$log.info("showingTimeoutNotification=" + $rootScope.showingTimeoutNotification);
                    if ($rootScope.showingTimeoutNotification === false || $rootScope.showingTimeoutNotification === undefined) {
                        $rootScope.showingTimeoutNotification = true;
                        //$log.info("showing confirm dialog box");
                        $confirm(
                            {},
                            {
                                templateUrl: 'timeout_warning.html',
                                backdrop: 'static',
                                keyboard: false
                            })
                            .then(function (retval) { // user selected to logout now
                                    $http.get("timeout");
                                    $rootScope.showingTimeoutNotification = false;
                                },
                                function (retval) {	// user selected cancel
                                    $http.get("resetSession");
                                    $rootScope.showingTimeoutNotification = false;
                                });
                    }
                }
            }
        }
    }
]);

/** Manages System level notices, such as disconnected from server, notices from the server about its state, etc.
 *  Note: do not confuse this SystemNotices service, (which lets you add/remove system messages from the $rootScope.systemNotices queue)
 *        with the systemNotices directive (which is the <system-notices> tag that displays the contents of the $rootScope.systemNotices queue)
 *    a System Notice object has the following properties:
 *        type is one of 'info' (default), 'warning' or 'warn', 'error'
 *        icon is one of the glyphicon classes, e.g., "glyphicon-remove-circle", or null for the default
 *        title is a short phrase to summarize the message, or null or '' if none is required
 *        msg is the content of the notice; NOTE also that it is used as a unique message ID if one is not specified, so that the message will not
 *            appear multiple times when polling.  If you need to to use something else as a unqiue message ID, set msgID specifically
 *        msgID is the unique ID of the message; by default, this is the same as the message, but you can specify something else if
 *            the message content changes (e.g., a countdown timer)
 *        route is the ui-sref used by the router to jump to one of the workflow manager pages, or null if none is required
 *
 * Example use cases:
 *        SystemNotices.info( "This long info message only appears here for debugging and styling purposes.  Clicking on me will bring you to the property settings page.  Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
 *            "test", {	route:"/admin/propertySettings" } );
 *
 *        SystemNotices.error( "A rose by any other color would smell as sweet",
 *            null, {	icon: "glyphicon-paperclip",
 *					title: "Using different icon and badly quoting Shakespeare"} );
 *
 *        SystemNotices.warn( "Simplest use case" );
 * */
AppServices.factory('SystemNotices', ['$rootScope', '$log', 'ClientState',
    function ($rootScope, $log, ClientState) {

        var _add = function (type, msg, queue, options) {

            var opt = options;
            if (!opt) {
                opt = {}
            }

            if (!queue) {
                queue = "default";
            }
            opt.queue = queue;

            if (opt.msgID === undefined) {
                opt.msgID = msg;
            }

            opt.type = type;

            if (!opt.icon) {
                switch (opt.type) {
                    case 'error':
                        opt.icon = "glyphicon glyphicon-remove-sign";
                        break;
                    case 'warn':
                    case 'warning':
                        opt.icon = "glyphicon-exclamation-sign";
                        break;
                    case 'info':
                    default:
                        opt.icon = "glyphicon-info-sign";
                        opt.type = 'info'; // force the type to be info
                        break;
                }
            }
            opt.message = msg;
            if ($rootScope.systemNotices.length <= 0
                || $rootScope.systemNotices.find(function (e) {
                    return ( e.msgID === opt.msgID )
                }) === undefined) {
                $rootScope.systemNotices.push(opt);
                if (!$rootScope.$$phase) {
                    $rootScope.$apply();
                }
            }

            //$log.debug("$rootScope.systemNotices="+angular.toJson($rootScope.systemNotices));
        };

        var _standardMessage = function (msgID) {
            switch (msgID) {
                case 'SYSTEM_NOTIFY_CONNECTION_LOST':
                    _add('error', "Connection to the Workflow Manager was lost at "
                        + $rootScope.lastServerExchangeTimestamp.format("M/D/YYYY hh:mm:ss a")
                        + " due to network or server problems.",
                        "network",
                        {
                            msgID: "SYSTEM_NOTIFY_CONNECTION_LOST",
                            title: "Network Connection Lost"
                        });
                    break;
                case 'SYSTEM_NOTIFY_RECONNECTING':
                    _add('warning', "Attempting to reconnect to Workflow Manager...",
                        "network",
                        {
                            msgID: "SYSTEM_NOTIFY_RECONNECTING",
                        });
                    break;
            }
        };

        var _remove = function (msgID) {
            if ($rootScope.systemNotices && $rootScope.systemNotices.length > 0) {
                for (var i = 0; i < $rootScope.systemNotices.length; i++) {
                    var obj = $rootScope.systemNotices[i];
                    if (obj.msgID == msgID) {
                        $rootScope.systemNotices.splice(i, 1);
                        //$log.info("removed(" + msgID + ")");
                        break;
                    }
                }
            }
        };

        // ----- event handling -----

        $rootScope.$on('CS_CONNECTION_STATE_CHANGED', function (event, args) {
            switch (args.newState) {
                case ClientState.ConnectionState.DISCONNECTED_SERVER:
                    _standardMessage("SYSTEM_NOTIFY_CONNECTION_LOST");
                    break;
                case ClientState.ConnectionState.DISCONNECTED_NETWORK:
                    _standardMessage("SYSTEM_NOTIFY_RECONNECTING");
                    break;
                case ClientState.ConnectionState.CONNECTED_SERVER:
                default:
                    _remove("SYSTEM_NOTIFY_CONNECTION_LOST");
                    _remove("SYSTEM_NOTIFY_RECONNECTING");
                    break;
            }
        });

        return {
            add: function (type, msg, queue, options) {
                _add(type, msg, queue, options);
            },
            standardMessage: function (id) {
                _standardMessage(id);
            },
            error: function (message, queue, opt) {
                _add("error", message, queue, opt);
            },
            warn: function (message, queue, opt) {
                _add("warning", message, queue, opt);
            },
            info: function (message, queue, opt) {
                _add("info", message, queue, opt);
            },
            removeAll: function () {	//this function removes all messages, regardless of src
                for (var i = $rootScope.systemNotices.length; i > 0; i--) {
                    $rootScope.systemNotices.pop();
                }
            },
            removeAllFromQueue: function (queue) {
                if ($rootScope.systemNotices && $rootScope.systemNotices.length > 0) {
                    for (var i = 0; i < $rootScope.systemNotices.length; i++) {
                        var obj = $rootScope.systemNotices[i];
                        if (obj.queue == queue) {
                            $rootScope.systemNotices.splice(i, 1);
                            $log.info("removed(" + obj.msgID + ") from queue");
                            break;
                        }
                    }
                }
            },
            remove: function (msgID) {
                _remove(msgID);
            }
        };
    }
]);

// this has a single method, calculateSystemNotices(), that can be called to generate system notices
//	all actual work is done by the private methods
AppServices.factory('SystemStatus', ['$log', '$rootScope', '$http', 'SystemNotices', 'ClientState',
    function ($log, $rootScope, $http, SystemNotices, ClientState) {

        /* the list of system messages last retrieved */
        var _systemMessages;

        /** http gets all the system messages
         * @returns {*}
         */
        var _getAllSystemMessages = function () {
            _systemMessages = $http.get("system-message").then(function (response) {
                //$log.debug('getSystemMessages()');
                //$log.debug('  returned data=', response.data);
                return response.data;
            });
            // Return the promise to the controller
            return _systemMessages;
        };

        /** displays all system messages using SystemNotice
         * @returns {*}
         */
        var _showAllSystemMessages = function () {
            _getAllSystemMessages().then(function (data) {
                //$log.debug('showAllSystemMessages()');
                //$log.debug('  returned data=', data);
                SystemNotices.removeAll();
                //SystemNotices.removeAllFromQueue( "serverSystemMessage" );
                angular.forEach(data, function (value) {
                    var opt = {
                        'msgID': value.id
                    };
                    SystemNotices.add(value.severity, value.msg, "serverSystemMessage", opt);
                });
            });
        };

        // ----- event handling -----
        // tap into the CS_CONNECTION_STATE_CHANGED so that it always gets the most current system messages
        $rootScope.$on('CS_CONNECTION_STATE_CHANGED', function (event, args) {
            if (ClientState.isConnectionActive()) {	// if I'm now connected
                switch (args.previousState) {
                    case ClientState.ConnectionState.UNINITIALIZED:
                    case ClientState.ConnectionState.DISCONNECTED_SERVER:
                    case ClientState.ConnectionState.DISCONNECTED_NETWORK:
                        // if the server was previously disconnected, then retrieve all system messages
                        // because we may have missed one when we were disconnected
                        _showAllSystemMessages();
                        break;
                    default:
                        // previous state was already connected, so don't need to do anything
                        break;
                }
            }
        });

        return {
            showAllSystemMessages: function () {
                _showAllSystemMessages();
            }
        }
    }
]);


AppServices.service('ClientState', ['$log', '$rootScope', '$timeout',
    function ($log, $rootScope, $timeout) {

        /* constants for connection states */
        this.ConnectionState = {
            UNINITIALIZED: "Uninitialized",
            CONNECTED_SERVER: "Connected to server",
            DISCONNECTED_SERVER: "Disconnected from server",
            DISCONNECTED_NETWORK: "Disconnected from network"
        };

        /* state of current client connection to the server
         * values is one of the ConnectionState values defined below
         */
        var _connectionState = this.ConnectionState.UNINITIALIZED;

        /** gets current connection state
         */
        this.getConnectionState = function () {
            return _connectionState;
        };

        /** sets connection state and if it is different than previous value, broadcasts a CS_CONNECTION_STATE_CHANGED event
         * @param state the ConnectionState to set
         */
        this.setConnectionState = function (state) {
            if (_connectionState != state) {
                var delay = 0;	// by default we broadcast a connection state change immediately
                if (state === this.ConnectionState.DISCONNECTED_SERVER) {
                    // delay the showing of network out briefly so when user reloads a page, it does not show up and confuse the user
                    delay = 250;
                }
                $timeout(function () {
                    var prev = _connectionState;
                    _connectionState = state;
                    $rootScope.$broadcast('CS_CONNECTION_STATE_CHANGED', {
                        previousState: prev,
                        newState: _connectionState
                    });
                }, delay);
            }
        };

        /** helper function for all connected states */
        this.isConnectionActive = function () {
            return _connectionState === this.ConnectionState.CONNECTED_SERVER;
        }
    }
]);

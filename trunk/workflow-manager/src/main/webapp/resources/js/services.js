/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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
    '$http',
    $http => {
        const getMetadataNoCache = async () => (await $http.get('info')).data;

        const cachedPromise = getMetadataNoCache();

        return {
            getMetadata: () => cachedPromise,
            getMetadataNoCache
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


AppServices.service('JobStatusNotifier', [
'$rootScope', 'NotificationSvc',
($rootScope, NotificationSvc) => {

    const handleJobStatusChange = (event, msg) => {
        const {id, jobStatus, isSessionJob} = msg.content;
        if (!isSessionJob || !isTerminalState(jobStatus)) {
            return;
        }

        if (jobStatus == 'COMPLETE') {
            NotificationSvc.success(`Job ${id} is now complete!`);
        }
        else if (jobStatus == 'COMPLETE_WITH_ERRORS') {
            NotificationSvc.error(`Job ${id} is now complete (with errors).`);
        }
        else if (jobStatus == 'COMPLETE_WITH_WARNINGS') {
            NotificationSvc.warning(`Job ${id} is now complete (with warnings).`);
        }
        else if (jobStatus == 'ERROR') {
            NotificationSvc.error(`Job ${id} is in a critical error state. `
                    + 'Check the Workflow Manager log for details.');
        }
        else if (jobStatus == 'UNKNOWN') {
            NotificationSvc.info(`Job ${id} is in an unknown state. `
                    + 'Check the Workflow Manager log for details.');
        }
        else if (jobStatus == 'CANCELLED') {
            NotificationSvc.info(`Job cancellation of job ${id} is now complete.`);
        }
    }

    const isTerminalState = jobStatus => {
        switch (jobStatus) {
            case 'COMPLETE':
            case 'COMPLETE_WITH_ERRORS':
            case 'COMPLETE_WITH_WARNINGS':
            case 'ERROR':
            case 'UNKNOWN':
                return true;
            default:
                return false;
        }
    }

    return {
        beginWatching() {
            $rootScope.$on('SSPC_JOBSTATUS', handleJobStatusChange)
        }
    }
}
]);

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


AppServices.factory('ServerSidePush', [
'$rootScope', '$log', 'MetadataService', 'SystemNotices', 'ClientState',
($rootScope, $log, MetadataService, SystemNotices, ClientState) => {

    let initialized = false;

    const init = () => {
        if (initialized) {
            return;
        }
        initialized = true;

        let baseUrl = location.href;
        baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf('workflow-manager/'));

        let unsubscribed = false;

        $.atmosphere.subscribe({
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
            fallbackTransport: 'long-polling',
            onOpen(response) {
                ClientState.setConnectionState(ClientState.ConnectionState.CONNECTED_SERVER);
                ClientState.onServerActivity();
                $log.info('Atmosphere connected using ' + response.transport + " @ "
                        + moment().format());
            },
            onClose(event) {
                if (!ClientState.isConnectionActive()) {
                    return;
                }
                if (event.state == 'unsubscribe') {
                    unsubscribed = true;
                    return;
                }
                if (!unsubscribed) {
                    // Use MetadataService to determine if close was due to logging out or
                    // due to network issues.
                    MetadataService.getMetadataNoCache();
                }
            },
            // Called when reconnection retries reach max and other unexpected errors.
            onError(event) {
                $log.error("ServerSidePush.onError:" + angular.toJson(event));
                $rootScope.$apply(() => {
                    SystemNotices.remove("SYSTEM_NOTIFY_RECONNECTING");
                    ClientState.setConnectionState(
                        ClientState.ConnectionState.DISCONNECTED_SERVER);
                });
            },
            onReopen(event) {
                console.log("ServerSidePush.onReopen:" + angular.toJson(event));
                $rootScope.$apply(() => {
                    ClientState.setConnectionState(
                        ClientState.ConnectionState.CONNECTED_SERVER);
                });
            },
            // Called when Atmosphere begins a retry attempt. If the attempt is successful
            // onReopen, will be called.
            onReconnect(event) {
                $log.debug("ServerSidePush.onReconnect:" + angular.toJson(event));
                $rootScope.$apply(() => {
                    ClientState.setConnectionState(
                        ClientState.ConnectionState.DISCONNECTED_NETWORK);
                });
            },
            onMessage({responseBody: message}) {
                ClientState.onServerActivity();
                if (message === 'X') {
                    // heartbeat message
                    return;
                }

                try {
                    const json = jQuery.parseJSON(message);
                    // this only parses the "header" portion of the message, the content is still a string
                    $rootScope.$broadcast(json.channel, json);
                }
                catch (e) {
                    console.log('Atmosphere:  This doesn\'t look like JSON: ', message);
                }
            }
        });
    }

    return {
        init
    }
}
])


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
['$q', 'NotificationSvc', 'ClientState',
function ($q, NotificationSvc, ClientState) {
    return {
        requestError: function (response) {
            console.log("httpInterceptor requestError", response);
            return $q.reject(response);
        },
        // optional method
        responseError: function (response) {
            console.log("httpInterceptor responseError", response);
            if (response.status == -1) {
                ClientState.setConnectionState(ClientState.ConnectionState.DISCONNECTED_SERVER);
            }
            if (ClientState.getConnectionState() != ClientState.ConnectionState.LOGGING_OUT
                    && response.status == 401) {
                window.top.location.reload()
            }

            var respData = response.data;
            if (respData && respData.message && respData.uncaughtError) {
                NotificationSvc.error(respData.message);
            }
            return $q.reject(response);
        },
        response: resp => {
            ClientState.onServerActivity();
            return resp;
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


/** Manages System level notices, such as disconnected from server, notices from the server about its state, etc.
 *  Note: do not confuse this SystemNotices service, (which lets you add/remove system messages from the systemNotices array)
 *        with the systemNotices directive (which is the <system-notices> tag that displays the contents of the systemNotices array)
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
AppServices.factory('SystemNotices',
['$rootScope', '$http', 'ClientState',
($rootScope, $http, ClientState) => {
    const systemNotices = [];

    const _add = (type, msg, options) => {
        const opt = options || {};
        opt.msgID = opt.msgID ?? msg;
        opt.type = type;
        opt.message = msg;

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
        const msgAlreadyAdded = systemNotices.some(e => e.msgID == opt.msgID);
        if (!msgAlreadyAdded) {
            systemNotices.push(opt);
        }
    };

    const _standardMessage = msgID => {
        switch (msgID) {
            case 'SYSTEM_NOTIFY_CONNECTION_LOST':
                _add('error', "Connection to the Workflow Manager was lost at "
                    + ClientState.getLastServerExchange().format("M/D/YYYY hh:mm:ss a")
                    + " due to network or server problems.",
                    {
                        msgID: "SYSTEM_NOTIFY_CONNECTION_LOST",
                        title: "Network Connection Lost"
                    });
                break;
            case 'SYSTEM_NOTIFY_RECONNECTING':
                _add('warning', "Attempting to reconnect to Workflow Manager...",
                    {
                        msgID: "SYSTEM_NOTIFY_RECONNECTING",
                    });
                break;
        }
    };

    const _remove = msgID => {
        for (let i = 0; i < systemNotices.length; i++) {
            const obj = systemNotices[i];
            if (obj.msgID == msgID) {
                systemNotices.splice(i, 1);
                break;
            }
        }
    };


    const showAllSystemMessages = () => {
        $http.get('system-message').then(({data}) => {
            systemNotices.splice(0);
            for (let {id, severity, msg} of data) {
                _add(severity, msg, { msgID: id });
            }
        });
    };

    showAllSystemMessages();

    // ----- event handling -----

    $rootScope.$on('CS_CONNECTION_STATE_CHANGED', (event, args) => {
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
        if (!ClientState.isConnectionActive()) {
            return;
        }

        switch (args.previousState) {
            case ClientState.ConnectionState.UNINITIALIZED:
            case ClientState.ConnectionState.DISCONNECTED_SERVER:
            case ClientState.ConnectionState.DISCONNECTED_NETWORK:
                // if the server was previously disconnected, then retrieve all system messages
                // because we may have missed one when we were disconnected
                showAllSystemMessages();
        }
    });

    $rootScope.$on('SSPC_SYSTEMMESSAGE', showAllSystemMessages);

    return {
        add: function (type, msg, options) {
            _add(type, msg, options);
        },
        standardMessage: function (id) {
            _standardMessage(id);
        },
        error: function (message, opt) {
            _add("error", message, opt);
        },
        warn: function (message, opt) {
            _add("warning", message, opt);
        },
        info: function (message, opt) {
            _add("info", message, opt);
        },
        remove: function (msgID) {
            _remove(msgID);
        },
        get: () => systemNotices,
        showAllSystemMessages
    };
}]);


AppServices.factory('ClientState', [
    '$rootScope',
    $rootScope => {
        const ConnectionState = {
            UNINITIALIZED: "Uninitialized",
            CONNECTED_SERVER: "Connected to server",
            DISCONNECTED_SERVER: "Disconnected from server",
            DISCONNECTED_NETWORK: "Disconnected from network",
            LOGGING_OUT: "Logging out"
        };

        let connectionState = ConnectionState.UNINITIALIZED;

        const setConnectionState = newState => {
            if (connectionState != newState) {
                const previousState = connectionState;
                connectionState = newState;
                $rootScope.$broadcast('CS_CONNECTION_STATE_CHANGED', { previousState, newState });
            }
        }

        let lastServerExchange = moment();

        return {
            ConnectionState,
            setConnectionState,
            getConnectionState: () => connectionState,
            isConnectionActive: () => connectionState === ConnectionState.CONNECTED_SERVER,
            onServerActivity: () => lastServerExchange = moment(),
            getLastServerExchange: () => lastServerExchange
        }
    }
]);

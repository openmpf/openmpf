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

"use strict";

angular.module('mpf.wfm.controller.JobsCtrl', [
    'mpf.wfm.services',
    'ui.bootstrap'
])
.controller('JobsCtrl', [
'$scope', '$http', '$timeout', '$state', '$uibModal', 'JobsService', 'NotificationSvc', 'Poller',
($scope, $http, $timeout, $state, $uibModal, JobsService, NotificationSvc, Poller) => {


    $scope.$on('$stateChangeSuccess', () => {
        $scope.state = parseStateParams();
        updateJobs();
    });

    let lastJobRequestDuration = 1_000;
    let lastRequestSent = 0;
    let lastResponseReceived = 0;

    const updateJobs = () => {
        lastRequestSent++;
        const thisRequest = lastRequestSent;
        const requestBeginTime = Date.now();

        return $http.get('jobs-paged', {params: $scope.state})
            .then(({data: {jobs, recordsTotal, hasMorePages}}) => {
                lastJobRequestDuration = Date.now() - requestBeginTime;
                if (thisRequest > lastResponseReceived) {
                    lastResponseReceived = thisRequest;
                    Object.assign($scope, {jobs, recordsTotal, hasMorePages});
                }
            });
    }

    Object.assign($scope, {
        jobs: [],
        recordsTotal: -1,
        hasMorePages: false,
        poller: Poller.create(updateJobs),
        isLoading() {
            return lastRequestSent != lastResponseReceived;
        },
        sortColumnClicked(orderCol) {
            if ($scope.state.orderCol == orderCol) {
                if ($scope.state.orderDirection == 'desc') {
                    $scope.state.orderDirection = 'asc';
                }
                else {
                    $scope.state.orderDirection = 'desc';
                }
            }
            else {
                $scope.state.orderDirection = 'asc';
            }
            $scope.state.orderCol = orderCol;
            $scope.gotoPageNum(1);
        },
        pageSizeChanged() {
            $scope.gotoPageNum(1)
        },
        searchChanged() {
            $scope.gotoPageNum(1)
        },
        gotoPageNum(pageNum) {
            $scope.state.page = pageNum;
            $state.go('jobs.page', $scope.state);
        },
        getSortGlyph(orderCol) {
            if (orderCol != $scope.state.orderCol) {
                return 'glyphicon-sort';
            }
            else if ($scope.state.orderDirection == 'asc') {
                return 'glyphicon-arrow-up';
            }
            else {
                return 'glyphicon-arrow-down';
            }
        },
        getStatusLabel({jobStatus}) {
            if (jobStatus.includes('ERROR')) {
                return 'label-danger';
            }
            else if (jobStatus.includes('WARNING')) {
                return 'label-warning';
            }
            else if (jobStatus.includes("UNKNOWN")) {
                return "label-primary";
            }
            else {
                return 'label-default';
            }
        },
        formatProgress({jobProgress}) {
            return jobProgress.toFixed() + '%'
        },
        showTiesDbError(job) {
            openCallbackErrorModal(job.jobId, 'TiesDb', job.tiesDbStatus)
        },
        showCallbackError(job) {
            openCallbackErrorModal(job.jobId, 'Callback', job.callbackStatus)
        },
        getCallbackDisplayType:(callbackStatus) => {
            if (callbackStatus.startsWith('ERROR:')) {
                return 'ERROR';
            }
            else {
                return callbackStatus;
            }
        },
        canCancelJob(job) {
            return !job.terminal && job.jobStatus != 'CANCELLING' && !job.hasCallbacksInProgress;
        },
        cancelJob(job) {
            const initialStatus = job.jobStatus;
            job.jobStatus = 'CANCELLING'
            JobsService.cancelJob(job.jobId).then(resp => {
                if (resp.responseCode == 0) {
                    NotificationSvc.success(
                        `A job cancellation request for job ${job.jobId} has been sent.`);
                }
                else {
                    NotificationSvc.error(
                        `Error with cancellation request with message: ${resp.message}`);
                    job.jobStatus = initialStatus;
                }
            }).catch(() => {
                NotificationSvc.error('Failed to send a cancellation request');
                job.jobStatus = initialStatus;
            });
        },
        canResubmit(job) {
            return job.terminal && !job.hasCallbacksInProgress && job.jobStatus != 'RESUBMITTING';
        },
        resubmitJob(job) {
            const initialStatus = job.jobStatus;
            job.jobStatus = 'RESUBMITTING';
            JobsService.resubmitJob(job.jobId, job.jobPriority).then(resp => {
                if (resp.mpfResponse.responseCode == 0) {
                    NotificationSvc.success(`Job ${job.jobId} has been resubmitted!`);
                }
                else {
                    NotificationSvc.error(resp.mpfResponse.message);
                    job.jobStatus = initialStatus;
                }
            }).catch(() => {
                NotificationSvc.error('Failed to send a resubmit request');
                job.jobStatus = initialStatus;
            });
        },
        showMedia(job) {
            $uibModal.open({
                templateUrl: 'resources/layouts/markup.html',
                size: 'lg',
                controller: 'MarkupCtrl',
                resolve: { job }
            });
        }
    });


    const orderColumns = new Set([
        'id', 'pipeline', 'timeReceived', 'timeCompleted', 'status', 'tiesDbStatus',
        'callbackStatus', 'priority'
    ]);

    const parseStateParams = () => {
        let { orderDirection } = $state.params
        if (orderDirection != 'asc' && orderDirection != 'desc') {
            orderDirection = 'desc';
        }
        return {
            page: parseIntOrDefault($state.params.page, 1),
            pageLen: parseIntOrDefault($state.params.pageLen, 25),
            orderCol: orderColumns.has($state.params.orderCol) ? $state.params.orderCol : 'id',
            orderDirection,
            search: $state.params.search ?? ''
        }
    }



    const parseIntOrDefault = (str, defaultVal) => {
        if (str == null) {
            return defaultVal;
        }
        const intVal = parseInt(str);
        if (isNaN(intVal)) {
            return defaultVal;
        }
        else {
            return intVal;
        }
    }

    const openCallbackErrorModal = (jobId, errorType, errorDetails) => {
        $uibModal.open({
            templateUrl: 'error-details-modal.html',
            controller: ($scope) => {
                Object.assign($scope, {jobId, errorType, errorDetails});
            }
        });
    }

    $scope.$on('SSPC_JOBSTATUS', (event, {content: {id, jobStatus, progress, endDate}}) => {
        const job = $scope.jobs.find(j => j.jobId == id);
        if (job) {
            $scope.$apply(() => {
                Object.assign(job, {
                    jobProgress: progress,
                    jobStatus,
                    endDate,
                    outputFileExists: jobStatus.startsWith('COMPLETE'),
                    terminal: progress == 100
                });
            });
        }
        else if (progress == 0) {
            throttledUpdateJobs();
        }
        // The job is missing because it isn't on the page of results the user is currently
        // looking at, so no action is necessary.
    });


    $scope.$on('SSPC_CALLBACK_STATUS', (evt, {event: type, content: {jobId, status}}) => {
        const job = $scope.jobs.find(j => j.jobId == jobId);
        if (!job) {
            return;
        }
        $scope.$apply(() => {
            if (type == 'tiesDb') {
                job.tiesDbStatus = status;
            }
            else {
                job.callbackStatus = status;
            }
        });
    });

    const throttledUpdateJobs = (() => {
        let isCoolingOff = false;
        let requestWasThrottled = false;

        const exposedFn = () => {
            if (isCoolingOff) {
                requestWasThrottled = true;
            }
            else {
                isCoolingOff = true;
                updateJobs().then(beginTimeout);
            }
        }

        let timeoutPromise;
        const beginTimeout = () => {
            const throttleTime = Math.max(2 * lastJobRequestDuration, 2_000);
            timeoutPromise = $timeout(() => {
                timeoutPromise = null;
                if (requestWasThrottled) {
                    requestWasThrottled = false;
                    updateJobs().then(beginTimeout);
                }
                else {
                    isCoolingOff = false;
                }
            }, throttleTime);
        };

        $scope.$on('$destroy', () => {
            if (timeoutPromise) {
                $timeout.cancel(timeoutPromise);
            }
        })

        return exposedFn;
    })();

    $scope.$on('$destroy', () => $scope.poller.cancel());
}
]
).directive('mpfSortControl', [
() => {
    return {
        restrict: 'E',
        transclude: true,
        template: `
            <a ng-click="sortColumnClicked(col)" href class="sort-control">
                <ng-transclude></ng-transclude>
                <span class="glyphicon" ng-class="getSortGlyph(col)"></span>
            </a>
        `,
        scope: true,
        link: ($scope, $el, $attrs) => {
            $scope.col = $attrs.col;
        }
    };
}
]
).factory('Poller', [
'$rootScope', '$timeout', 'PropertiesSvc',
($rootScope, $timeout, PropertiesSvc) => {

    class Poller {
        lastUpdate = moment();
        broadcastEnabled = true;
        pollingInterval = -1;
        #promiseSupplier;
        #activeTimeout = null;
        #stopListeningPropsChanged
        #stopListeningJobStatus

        constructor(promiseSupplier) {
            this.#promiseSupplier = promiseSupplier;
            this.#updateConfig();
            this.#stopListeningPropsChanged = $rootScope.$on('SSPC_PROPERTIES_CHANGED', () => {
                this.#updateConfig();
            });
            this.#stopListeningJobStatus = $rootScope.$on('SSPC_JOBSTATUS', () => {
                if (!this.broadcastEnabled) {
                    // Received job broadcast even though job broadcast's were disabled when
                    // properties were last checked, so we need to re-check the state of the
                    // properties.
                    this.#updateConfig();
                }
            })
        }

        cancel() {
            this.#stopListeningPropsChanged();
            this.#stopListeningJobStatus();
            this.#cancelTimeout();
        }

        #updateConfig() {
            PropertiesSvc.get('web.broadcast.job.status.enabled')
                .$promise
                .then(({value}) => {
                    if (value.toLowerCase() === 'true') {
                        this.#handlePollingConfigChange(true, -1);
                    }
                    else {
                        this.#updatePollingInterval();
                    }
                });
        }

        #handlePollingConfigChange(broadcastEnabled, pollingInterval) {
            const pollingIntervalChanged = pollingInterval !== this.pollingInterval;
            this.broadcastEnabled = broadcastEnabled;
            this.pollingInterval = pollingInterval;

            if (this.broadcastEnabled || this.pollingInterval < 1) {
                this.#cancelTimeout();
                return;
            }

            if (pollingIntervalChanged) {
                this.#cancelTimeout();
                this.#scheduleNextPoll();
            }
        }

        #updatePollingInterval() {
            PropertiesSvc.get('web.job.polling.interval')
                .$promise
                .then(({value}) => {
                    const interval = +value;
                    if (isNaN(interval) || interval < 1) {
                        this.#handlePollingConfigChange(false, -1);
                    }
                    else {
                        this.#handlePollingConfigChange(false, interval);
                    }
                });
        }

        #scheduleNextPoll() {
            if (this.#activeTimeout) {
                // There is already a pending poll.
                return;
            }
            if (!this.broadcastEnabled && this.pollingInterval > 0) {
                this.#activeTimeout = $timeout(() => this.#pollingUpdate(), this.pollingInterval);
            }
        }

        #pollingUpdate() {
            this.#activeTimeout = null;
            try {
                this.#promiseSupplier()
                    .then(() => {
                        this.lastUpdate = moment();
                    })
                    .finally(() => this.#scheduleNextPoll());
            }
            catch (e) {
                console.log('caught error: %o', e);
                this.#scheduleNextPoll();
            }
        }

        #cancelTimeout() {
            if (this.#activeTimeout) {
                $timeout.cancel(this.#activeTimeout);
                this.#activeTimeout = null;
            }
        }
    }

    return {
        create: promiseSupplier => new Poller(promiseSupplier)
    }
}
])

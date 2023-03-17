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

/* globals angular, Dropzone */
'use strict';

/* Angular Directives */
var AppDirectives = angular.module('mpf.wfm.directives', []);


AppDirectives.directive('mpfNavbar',
['MetadataService', 'RoleService', 'ClientState',
function (MetadataService, RoleService, ClientState) {
    return {
        restrict: 'E',
        templateUrl: 'resources/layouts/directives/navbar.html',
        scope: {},
        link: function ($scope) {
            MetadataService.getMetadata().then(function (data) {
                $scope.version = data.version;
                $scope.dockerEnabled = data.dockerEnabled;
            });

            RoleService.getRoleInfo().then(function (roleInfo) {
                $scope.userName = roleInfo.userPrincipalName;
            });
            $scope.logout = () => {
                ClientState.setConnectionState(ClientState.ConnectionState.LOGGING_OUT);
            }
        }
    };
}
]);


AppDirectives.directive('pageInfo', function() {
    return {
        transclude:true,
        templateUrl: 'resources/layouts/directives/page_info.html'
    }
});

// directive that shows a UI with a pipeline selection drop-down menu with
//  typeahead, and a job priority drop-down menu
// Note that when specifying in html, the attributes are spelled selected-pipeline and selected-priority
//  while in Javascript, it is selectedPipeline, selectedPriority
// Note that if you do not need the pipeline (ironic, isn't it?) then just simply don't
//  define a selected-pipeline attribute.
AppDirectives.directive('pipelineSelection', function ($log, PipelinesService, JobPriorityService) {
    return {
        templateUrl: 'resources/layouts/directives/pipeline_selection.html',
        restrict: 'E',
        replace: false,
        link: function (scope, element, attrs) {
            // set defaults
            scope.helpPlacement = ( attrs.helpPlacement ) ? attrs.helpPlacement : 'right';
        },
        scope: {
            selectedPipeline: '=',  // the selected pipeline object (use .selected to get value)
            selectedPriority: '=',  // the selected priority object (use .selected to get value)
            helpPlacement: '@'      // the placement of help popovers
        },
        controller: function ($scope, $element, $attrs) {

            // ---------------------------------------------------------------
            // initializations
            // ---------------------------------------------------------------

            $scope.pipelinesCollection = [];
            PipelinesService.getAvailablePipelines().then(function (pipelinesList) {
                if (pipelinesList) {
                    // sort the list first
                    pipelinesList.sort(function (a, b) {
                        if (a.name.toUpperCase() > b.name.toUpperCase()) { return 1; }
                        if (a.name.toUpperCase() < b.name.toUpperCase()) { return -1; }
                        return 0;
                    });
                    $scope.pipelinesCollection = pipelinesList;
                } else {
                    $log.error("No pipelines retrieved!");
                }
            });

            $scope.jobPriorities = [{priority: '0', type: '(lowest)'},
                {priority: '1', type: '(low)'},
                {priority: '2', type: ''},
                {priority: '3', type: ''},
                {priority: '4', type: '(default)'},
                {priority: '5', type: ''},
                {priority: '6', type: ''},
                {priority: '7', type: '(high)'},
                {priority: '8', type: ''},
                {priority: '9', type: '(highest)'}];

            // set default job priority
            JobPriorityService.getDefaultJobPriority().then(function (defaultJobPriorityResponse) {
                var defaultJobPriority;
                if (defaultJobPriorityResponse && defaultJobPriorityResponse.data) {
                    defaultJobPriority = parseInt(defaultJobPriorityResponse.data);
                }

                if (defaultJobPriority && defaultJobPriority >= 0 && defaultJobPriority <= 9) {
                    for (var key in $scope.jobPriorities) {
                        if ($scope.jobPriorities.hasOwnProperty(key)
                            && key == defaultJobPriority) {
                            //update model which will update the dropdown
                            $scope.selectedPriority = {selected: $scope.jobPriorities[key]};
                            break;
                        }
                    }
                }
            })
        }
    }
});


// directive that shows an alert on the top of the UI for system messages such as need to restart server
//  Note that this is only used for the display of the notices, not to create/delete notices (done in the SystemNotices service),
//    and thus, it should really only be used once in index.html.
//    It is made into a directive to keep that page from getting too complex
AppDirectives.directive('systemNotices',
['SystemNotices', '$location',
(SystemNotices, $location) => {
    return {
        templateUrl: 'resources/layouts/directives/system_notice.html',
        restrict: 'E',
        scope: {},
        link: ($scope) => {
            $scope.showJump = jumpPath => jumpPath !== $location.path();
            $scope.systemNotices = SystemNotices.get();
        }
    }
}]);

/**
 * Shows a spinner and hides the html while waiting for something to load.
 * The directive determines whether or not something is loading by watching the
 * passed in variable. When the flag is true then the spinner is shown.
 * When the flag is false then the actual content will be shown.
 * Usage: <div mpf-is-loading="myIsLoadingFlag"> Don't show this until done loading </div>
 */
AppDirectives.directive('mpfIsLoading',
[
function () {
    return {
        restrict: 'A',
        transclude: true,
        templateUrl: 'resources/layouts/directives/is_loading.html',
        scope: {
            mpfIsLoading: '='
        }
    };
}
]);

/**
 * Prevents content from being shown unless the user is an admin.
 * A spinner is shown until the role info is retrieved.
 * If the user is not an admin an error message is shown instead of
 * the contents of the tag.
 * <mpf-admin-only>
 *     <div> Only admins should see this. </div>
 * </mpf-admin-only>
 */
AppDirectives.directive('mpfAdminOnly',
[
'RoleService',
function (RoleService) {
    return {
        restrict: 'EA',
        templateUrl: 'resources/layouts/directives/admin_only.html',
        transclude: true,
        scope: {
            //showWarningMessage: '@?'
        },
        link: function ($scope, element, attrs) {
            $scope.roleInfoLoaded = false;
            if ( attrs.hasOwnProperty('showWarningMessage') ) {
                $scope.showWarningMessage = true;
            }
            RoleService.getRoleInfo()
                .then(function (roleInfo) {
                    $scope.roleInfoLoaded = true;
                    $scope.isAdmin = roleInfo.admin;
                });
        }
    };
}]);



AppDirectives.directive('mpfComponentDropzone',
['csrfHeaders',
function (csrfHeaders) {
    return {
        restrict: 'E',
        templateUrl: 'resources/layouts/directives/component_dropzone.html',
        scope: {
            canUpload: '='
        },
        link: function ($scope, $el) {
            var dropzoneDiv = $el.find('.dropzone').get(0);
            var dropzone = new Dropzone(dropzoneDiv, {
                url: "components",
                headers: csrfHeaders(),
                autoProcessQueue: true,
                maxFiles: 1,
                addRemoveLinks:false,
                dictDefaultMessage: "",
                acceptedFiles:"application/gzip,application/x-gzip,.tar,.gz,.tar.gz",
                maxFilesize: 2048, //MB
                accept: $scope.canUpload
            });

            dropzone.on('error', function (file, errorInfo) {
                dropzone.removeAllFiles();
                $scope.$apply(function () {
                    $scope.$emit(eventNamed('error'), file, errorInfo);
                });
            });

            dropzone.on('success', function (file) {
                dropzone.removeAllFiles();
                $scope.$apply(function () {
                    $scope.$emit(eventNamed('success'), file);
                });
            });

            dropzone.on('sending', function (file) {
                $scope.$apply(function () {
                    $scope.$emit(eventNamed('sending'), file);
                });
            });

            var eventNamed = function (shortName) {
                return 'mpf.component.dropzone.' + shortName;
            };
        }
    };
}
]);

AppDirectives.directive('mpfInteger',
    [
        function () {
            return {
                restrict: 'A',
                require: 'ngModel',
                link: function ($scope, $el, $attrs, ngModelCtrl) {
                    ngModelCtrl.$validators.mpfInteger = function (modelValue) {
                        return ngModelCtrl.$isEmpty(modelValue) || Number.isInteger(modelValue);
                    };

                }
            };
        }
    ]);

AppDirectives.directive('uiSelectOpened', function() {
    return {
        restrict: 'A',
        require: 'uiSelect',
        link: function (scope, element, attrs, uiSelect) {
            uiSelect.open = true;
            uiSelect.active = true;
            uiSelect.close = function() { return false; };

            // Prior to adding this the first item in the select list would always be highlighted.
            uiSelect.isActive = function () { return false; };
        }
    };
});


AppDirectives.directive('mpfPagination', [
() => {
    return {
        restrict: 'E',
        templateUrl: 'resources/layouts/directives/pagination.html',
        scope: {
            currentPage: '=',
            pageLen: '=',
            recordsTotal: '=',
            hasMorePages: '=',
            isFiltered: '=',
            numShown: '=',
            goToPage: '&',
        },
        link($scope, $el) {
            $scope.$watchGroup([
                () => $scope.currentPage,
                () => $scope.pageLen,
                () => $scope.recordsTotal,
                () => $scope.hasMorePages,
                () => $scope.isFiltered,
                () => $scope.numShown,
            ], ([newPage]) => {
                $scope.pageModel.page = newPage;
                $scope.entryCountMsg = getEntryCountMsg();
                $scope.totalItems = getTotalItemsForPagination();
                updatePaginationDom();
            });


            let isFirstChange = true;
            const initialPage = $scope.currentPage;
            // uib-pagination uses transclusion so a dotted property name needs to be used
            // so that the page number is available in this scope.
            $scope.pageModel = {
                page: $scope.currentPage
            }
            $scope.changeHandler = () => {
                if (isFirstChange) {
                    // If the current page is not 1 when uib-pagination is initialized, it will
                    // attempt to set the page to 1.
                    isFirstChange = false;
                    if (initialPage != 1) {
                        $scope.pageModel.page = initialPage;
                        return
                    }
                }
                $scope.goToPage({$page: $scope.pageModel.page})
            }


            const getEntryCountMsg = () => {
                if ($scope.recordsTotal < 0) {
                    return '';
                }
                const startIdx = ($scope.currentPage - 1) * $scope.pageLen;
                const end = startIdx + $scope.numShown;
                const start = end > 0 ? startIdx + 1 : 0;
                if ($scope.isFiltered) {
                    return `Showing entries ${start} to ${end}`
                        + ` (filtered from ${$scope.recordsTotal} total entries)`;
                }
                else {
                    return `Showing ${start} to ${end} of ${$scope.recordsTotal} total entries`;
                }
            }

            const updatePaginationDom = () => {
                $scope.$applyAsync(() => {
                    // uib-pagination does not disable links with ellipses.
                    $el.find('.pagination-page')
                        .not('.pagination-prev, .pagination-next')
                        .removeClass('disabled')
                        .has('a:contains("...")')
                        .addClass('disabled');

                    $el.find('.pagination-next > a')
                        .text($scope.isFiltered ? 'More' : 'Next')
                });
            }

            const getTotalItemsForPagination = () => {
                if ($scope.isFiltered) {
                    const numUpToCurrentPage = $scope.currentPage * $scope.pageLen;
                    return $scope.hasMorePages
                        ? numUpToCurrentPage + 1
                        : numUpToCurrentPage;
                }
                else {
                    return $scope.recordsTotal;
                }
            }
        }
    }
}
])

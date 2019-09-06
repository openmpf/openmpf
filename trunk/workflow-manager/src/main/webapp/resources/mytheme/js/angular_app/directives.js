/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

AppDirectives.directive('appVersion', ['version', function (version) {
    return function (scope, elm, attrs) {
        elm.text(version);
    };
}]);

/** directive that senses when the browser window has been resized
 *  based on https://gist.github.com/chrisjordanme/8668864
 *  To use the directive, use it as an attribute to an HMTL element, (e.g., <div broadcast-resize-event>)
 *  and then in a controller, use $scope.$on('UI_WINDOW_RESIZE', function(event, msg ) {} to handle the event
 *
AppDirectives.directive('broadcastResizeEvent', function ($window, $rootScope) {
    return {
        link: function (scope, el, attrs) {
            angular.element($window).on('resize', function () {
                var window = {
                    height: $window.innerHeight,
                    width: $window.innerWidth
                };
                //console.log("in broadcastResizeEvent directive->window="+JSON.stringify(window));
                $rootScope.$broadcast('UI_WINDOW_RESIZE', window );
            });
        }
    };
});*/

// puts a help icon and when user hovers over it (or other trigger as defined by the trigger attribute), it shows
//  the help text as a popover
AppDirectives.directive('helpDescription', function() {
    return {
        templateUrl: 'help_description.html',
        restrict: 'EA',
        link: function (scope, element, attrs) {
            // set defaults
            scope.placement = ( attrs.placement ) ? attrs.placement : 'right';
            // ToDo: P038: can't figure out why setting this does not work
            //scope.trigger = ( attrs.trigger ) ? attrs.trigger : 'mouseenter';
        },
        scope: {
            helptext: '@',
            placement: '@'
            //trigger: '@'
        }
    }
});

AppDirectives.directive('pageInfo', function() {
    return {
        transclude:true,

        templateUrl: 'resources/js/angular_app/directive_templates/page_info.html'
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
        templateUrl: 'resources/js/angular_app/directive_templates/pipeline_selection.html',
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

// this directive enables a ui-grid to act like a normal table (instead of a viewport table)
//	it was taken from https://github.com/angular-ui/ui-grid/issues/2531 with the code from https://gist.github.com/AnirudhaGohokar/0b1a16af6b59e7a392cf
AppDirectives.directive('uigridShowAllRows', function( gridUtil ) {
    return {
        restrict: 'A',
        require: 'uiGrid',
        link: function($scope, $elm, $attrs, uiGridCtrl) {
            $scope.$watch($attrs.uiGrid + '.minRowsToShow', function(val) {
                var grid = uiGridCtrl.grid;

                // Initialize scrollbars (TODO: move to controller??)
                uiGridCtrl.scrollbars = [];

                // Figure out the new height
                var contentHeight = grid.options.minRowsToShow * grid.options.rowHeight;
                var headerHeight = grid.options.hideHeader ? 0 : grid.options.headerRowHeight;
                var footerHeight = grid.options.showFooter ? grid.options.footerRowHeight : 0;
                var columnFooterHeight = grid.options.showColumnFooter ? grid.options.columnFooterHeight : 0;
                var scrollbarHeight = grid.options.enableScrollbars ? gridUtil.getScrollbarWidth() : 0;
                var pagerHeight = grid.options.enablePagination ? gridUtil.elementHeight($elm.children(".ui-grid-pager-panel").height('')) : 0;

                var maxNumberOfFilters = 0;
                // Calculates the maximum number of filters in the columns
                angular.forEach(grid.options.columnDefs, function(col) {
                    if (col.hasOwnProperty('filter')) {
                        if (maxNumberOfFilters < 1) {
                            maxNumberOfFilters = 1;
                        }
                    }
                    else if (col.hasOwnProperty('filters')) {
                        if (maxNumberOfFilters < col.filters.length) {
                            maxNumberOfFilters = col.filters.length;
                        }
                    }
                });
                var filterHeight = maxNumberOfFilters * headerHeight;

                var filterTextBoxHeight=0;
                $("div[ui-grid-filter]").each(function(){
                    if($(this).height() > filterTextBoxHeight)filterTextBoxHeight=$(this).height();
                });

                var newHeight = headerHeight + contentHeight + footerHeight + columnFooterHeight + scrollbarHeight + filterHeight + pagerHeight + filterTextBoxHeight + 1;

                $elm.css('height', newHeight + 'px');

                grid.gridHeight = $scope.gridHeight = gridUtil.elementHeight($elm);

                // Run initial canvas refresh
                grid.refreshCanvas();
            });
        }
    };
});


// directive that shows an alert on the top of the UI for system messages such as need to restart server
//  Note that this is only used for the display of the notices, not to create/delete notices (done in the SystemNotices service),
//    and thus, it should really only be used once in index.html.
//    It is made into a directive to keep that page from getting too complex
AppDirectives.directive('systemNotices', function ( $log, $location ) {
    return {
        templateUrl: 'resources/js/angular_app/directive_templates/system_notice.html',
        restrict: 'E',
        scope: false,   // Uses parent scope!!!
        controller: function ($scope, $element, $attrs) {
            $scope.showJump = function( jumpPath ) {
                //$log.info("jumpPath="+jumpPath);
                //$log.info("$location.path()="+$location.path());
                return ( jumpPath!==$location.path() );
            }
        }
    }
});

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
        templateUrl: 'resources/js/angular_app/directive_templates/is_loading.html',
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
        templateUrl: 'resources/js/angular_app/directive_templates/admin_only.html',
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
[
function () {
    return {
        restrict: 'E',
        templateUrl: 'resources/js/angular_app/directive_templates/component_dropzone.html',
        scope: {
            canUpload: '='
        },
        link: function ($scope, $el) {
            var dropzoneDiv = $el.find('.dropzone').get(0);
            var dropzone = new Dropzone(dropzoneDiv, {
                url: "components",
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

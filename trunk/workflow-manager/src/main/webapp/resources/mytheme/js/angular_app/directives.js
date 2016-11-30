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

/* globals angular, Dropzone */
'use strict';

/* Angular Directives */
var AppDirectives = angular.module('WfmAngularSpringApp.directives', []);

AppDirectives.directive('appVersion', ['version', function (version) {
    return function (scope, elm, attrs) {
        elm.text(version);
    };
}]);

/** directive that senses when the browser window has been resized
 *  based on https://gist.github.com/chrisjordanme/8668864
 *  To use the directive, use it as an attribute to an HMTL element, (e.g., <div broadcast-resize-event>)
 *  and then in a controller, use $scope.$on('UI_WINDOW_RESIZE', function(event, msg ) {} to handle the event
 */
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
});

AppDirectives.directive('dndCart', function ($log,$filter,ServicesCatalogService) {
    return {
        templateUrl: 'cart.html',
        restrict: 'E',
        replace: false,
        link: function(scope, element, attrs) {
            if ( attrs["highlightDropArea"] !== undefined )	// in other words, if it is defined
            {
                var cart = angular.element(element[0].getElementsByClassName('cart'));
                cart
                    .bind( 'mouseenter', function($event) {
                        var element = $event.currentTarget;
                        element.style['border'] = '1px solid #ff9933';	// ToDo: P038:  hardcoded
                    })
                    .bind( 'mouseleave', function($event) {
                        var element = $event.currentTarget;
                        element.style['border'] = '1px solid black';
                    });
            }

            // set defaults //////////////////////////////////////////////////

            // ----- set default values for attrs, if they are not specified
            if ( !attrs.name ) {
                scope.name='Cart';
            }
            scope.minItems = ( attrs.minItems ) ? parseInt( attrs.minItems ) : 0;
            scope.maxItems = ( attrs.maxItems ) ? parseInt( attrs.maxItems ) : 99;

            // ----- set default, these properties will be overridden if attrs are set
            scope.option = {
                containment: attrs.containment,
                isDuplicate: function( item, listToCheckAgainst ) {	// needed for the patched version of ng-sortable for allowDuplicates to work correctly
                    // returns the first index in listToCheckAgainst that equals item, or -1 if not found
                    var index = listToCheckAgainst.findIndex(function(currentValue) {
                        return currentValue[attrs.listItemKey] === item[attrs.listItemKey];
                    });
                    if ( index >= 0 ) {
                        //$log.debug("  is a duplicate in list:  " + item[attrs.listItemKey] );
                        if ( !scope.allowDuplicates )
                        {
                            //$log.debug("    incrementing dest:  " + item[attrs.listItemKey] );
                            if ( listToCheckAgainst[index]['serviceCount'] < scope.maxItems ) {
                                listToCheckAgainst[index]['serviceCount'] += item['serviceCount'];
                            }
                        }
                        return true;
                    }
                    else{
                        //$log.debug("  is not a duplicate in list:  " + item[attrs.listItemKey] );
                        //if ( !listToCheckAgainst[index]['serviceCount'] ) {
                        //    listToCheckAgainst[index]['serviceCount'] = 0;
                        //}
                        return false;
                    }
                },
                //unbindDrag();
                allowDuplicates: false,	// works only with patch
                clone: false//,
                //dragStart: function( obj ) {// triggered on drag start.
                    //$log.error("dragStart(obj="+angular.toJson(obj));
                    //// obj.source.index is the index of the item that is being dragged in the source cart
                    //$log.error("  item value="+scope.list[obj.source.index].serviceCount);
                    //scope.forceCounterWithinRange( null, obj.source.index );
                    ////scope.$apply();   // don't use this because we're always in the middle of $apply
                //},
                //dragEnd: function( obj ) {// triggered on drag end.
                    //$log.error("dragEnd(obj="+angular.toJson(obj));
                    //// obj.dest.index is the index of the item that is being dragged in the dest cart
                    //$log.error("  item value="+scope.list[obj.dest.index].serviceCount);
                    //scope.forceCounterWithinRange( null, obj.dest.index );
                    ////scope.$apply();   // don't use this because we're always in the middle of $apply
                //}
            };

            // ----- update defaults with attributes, if any

            if ( attrs.allowDuplicates ) {
                scope.option.allowDuplicates = attrs.allowDuplicates;
            }
            if ( attrs.clone ) {
                scope.option.clone = attrs.clone;
            }
        },
        scope: {
            name: '@',
            containment: '@?',
            cartid: '@',
            list: '=',
            cartCollection: '=',	// the array of carts from which this is ng-repeated, needed for delete cart button to work; if not defined, delete button does not show
            cartIndex: '=',	// the index of this cart in the array of carts from which this is ng-repeated; or undefined if it is not defined
            listItemKey: '@',
            catalog: '=',
            width: '@?',
            showItemCount: "@",	// can take on one of the following:  "false" - don't show, "read-only" - don't let change instance values, "editable" - allow instance changes to the model
            // note that while this is defined at the cart level, it is passed to the dnd-cart-item level directly, and is used there, even though it's not defined
            // in its scope; setting it in its own scope in dnd-cart-item causes its value to not work
            maxItems: '@?',
            minItems: '@?',
            option: '=?',
            allowDuplicates: '@?',
            clone: '@?',
            pageErrors: '=?',
            highlightDropArea: "@?"
        },
        controller: function( $scope, $element, $attrs, $log /*, $transclude*/ ) {

            // ---------------------------------------------------------------
            // directive actions
            // ---------------------------------------------------------------


            $scope.getServiceColor = function( item ) {
                return ServicesCatalogService.getServiceColor( item['serviceName'] );
            };

            /** helper function to have an ID (also name attribute) for each item in a cat */
            $scope.getCartItemId = function( item ) {
                return $scope.name + "_" + item[$scope.listItemKey];
            };
            $scope.getItemId = function( item ) {
                return item[$scope.listItemKey];
            };
            $scope.getCartItemErrorId = function( item ) {
                return $scope.getCartItemId( item ) + '.' + $scope.listItemKey + '.$error';
            };


            $scope.addAllServicesToCart = function( cartIndex ) {
                //  todo:  P038:  this should really be in the controller since it is specialized business logic
                angular.copy( $scope.catalog, $scope.list );
            };

            $scope.removeCart = function() {
                //$log.warn("removeCart(): cartIndex = " + $scope.cartIndex + " name = "+$scope.name);
                if ( $scope.cartIndex >= 0 ) {
                    $scope.cartCollection.splice($scope.cartIndex, 1);
                    $log.debug("$scope.cartCollection="+$scope.cartCollection);
                }
            };

            /* -------------------- number spinner handlers --------------------------------- */

            /** verifies the value of event.currentTarget (the counter), and force it to min or max if out of range
             *      NOTE: no using in current version, using validation instead
             *      NOTE: that either event or index are valid specifiers, event is used when available from the UI
             *          whereas index is used for the model.  If both are specified, the UI is given preference
             *          because that is likely where the change started
             *      TODO:  P038:  might need to add a flag to specfiy which one takes precedence since there will
             *          be times when the model change should take precedence
             */
            $scope.forceCounterWithinRange = function( event, index ) {
                $log.debug("forceCounterWithinRange("+angular.toJson(event)+",index="+index+","+$scope.minItems+","+$scope.maxItems);
                var value = null;
                var item =  $scope.list[index];
                if ( event ) {
                    value = event.currentTarget.value;
                }
                else {
                    value = item['serviceCount'];
                }
                $log.debug("  value="+value);
                $log.debug("  type="+typeof(value) );

                // value can be used to find out about state of validation
                //  e.g., value = undefined:  max validation triggered
                //                NaN:  user dragged a service with a validation error to another node
                //                null:
                //                '':
                //set 0 case
                item['serviceCount'] = 0;
                //update if meets these
                if(value && !isNaN(value)) {
                    if (value < $scope.minItems ) {
                        item['serviceCount'] = parseInt( $scope.minItems );
                    }
                    else if ( value > $scope.maxItems ) {
                        item['serviceCount'] = parseInt( $scope.maxItems );
                    }
                }

                // put value back in UI
                if (event) {
                    // need the parseInt because otherwise, we might have "01" instead of "1"
                    event.currentTarget.value = parseInt( item['serviceCount'] );
                }
                $scope.logCartToConsole();
            };

            /* event handler for ng-keydown; this serves to only limit the keys that are recognized, and leaves business logic to the key up event */
            $scope.onInstanceCounterKeydown = function( evt, index ) {
                //$log.debug("onInstanceCounterKeydown( ... index="+index);
                var keyboardControlKeycodes = [ 8, 9, 13, 37, 38, 39, 40 ];    // keycodes for backspace, tab, carriage return (enter) and arrows
                if( keyboardControlKeycodes.indexOf( evt.which ) >= 0 ) { // to allow keyboard control keycodes
                    //$scope.forceCounterWithinRange( evt, index );
                }
                else if (evt.which < 48 || evt.which > 57)   // prevent anything but digits from being typed
                {
                    evt.preventDefault();
                }
            };

            /* event handler for ng-keyup */
            $scope.onInstanceCounterKeyup = function( event, index ) {
                $log.debug("onInstanceCounterKeyup");
                //$scope.forceCounterWithinRange( event, index );
            };

            /* event handler for ng-change (when value of input changes)
                NOTE:  event is passed in, but it appears to always be undefined, use index instead
             */
            $scope.onInstanceCounterChanged = function( event, index ) {
                $log.debug("onInstanceCounterChanged( event="+angular.toJson(event));
                $scope.forceCounterWithinRange( event, index );
            };

            /* event handler for ng-blur */
            $scope.onInstanceCounterExit = function( event, index ) {
                $log.debug("onInstanceCounterExit");
                $scope.forceCounterWithinRange( event, index );
            };

            /** debugging method, shows the content of the cart to the console */
            $scope.logCartToConsole = function() {
                //angular.forEach( $scope.list, function(v,k) {
                //    $log.warn( " " + k + " " + v['serviceName'] + " = " + v['serviceCount'] );
                //});
            }
        }
    };
});

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


// directive that shows a UI with a pipeline selection drop-down menu with
//  typeahead, and a job priority drop-down menu
// Note that when specifying in html, the attributes are spelled selected-pipeline and selected-priority
//  while in Javascript, it is selectedPipeline, selectedPriority
// Note that if you do not need the pipeline (ironic, isn't it?) then just simply don't
//  define a selected-pipeline attribute.
AppDirectives.directive('pipelineSelection', function ($log,PipelinesService,PropertiesService) {
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
                    pipelinesList.pipelines.pipeline = pipelinesList.pipelines.pipeline.sort( function(a,b) {
                        if (a.name.toUpperCase() > b.name.toUpperCase()) { return 1; }
                        if (a.name.toUpperCase() < b.name.toUpperCase()) { return -1; }
                        return 0;
                    });
                    angular.forEach(pipelinesList.pipelines.pipeline, function (value) {
                        this.push(value);
                    }, $scope.pipelinesCollection);
                    //$log.debug('$scope.pipelinesCollection length', angular.toJson($scope.pipelinesCollection));
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
            PropertiesService.getDefaultJobPriority().then(function (defaultJobPriorityResponse) {
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

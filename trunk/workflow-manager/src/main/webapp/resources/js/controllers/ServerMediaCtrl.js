/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

(function () {
    'use strict';

    /**
     * ServerMediaCtrl
     * @constructor
     */
    angular.module('mpf.wfm.controller.ServerMediaCtrl', []).controller('ServerMediaCtrl',
        ['$scope',  '$http', '$location', '$timeout', '$log', '$compile', 'MediaService', 'JobsService', 'NotificationSvc', 'csrf',
        function ($scope, $http, $location, $timeout, $log, $compile, MediaService, JobsService, NotificationSvc, csrf) {

            var fileTable = null;//bootstrap datatable
            var fileList = [];//current list of files for the selected folder
            var fileTree;
            var fileUploadURL = "uploadFile";
            var selectedFileTable = null;
            var selectedNode = {text: "None", nodeId: 0};
            var maxFileUploadCnt = 100;
            var waitModal = null; //message for waiting
            var removeAll = false;
            var directoryMap = {};
            var allowUpload = true;
            var successfulUploads = 0;
            var cancelledUploads = 0;
            var modalShow = false;
            var progressModal = null;
            var dropzone = null;
            var serverDirs = [];//the directory structure
            var treeDirs = [];
            $scope.selectedSkipTiesDbServer = false;
            $scope.selectedJobPriorityServer = {selected: {priority: '4', type: '(default)'}}; //default
            $scope.selectedPipelineServer = {};//selected pipeline
            $scope.urls = null;

            var filesToSubmit = [];//files going back to server
            $scope.filesToSubmitCount = filesToSubmit.length;
            var checkedSingleFileInModal = null;
            var checkedFilesInModal = [];

            var init = function (useCache, funct) {//reset
                $("#loading_url").hide();
                $("#fileListWrap").css('visibility','hidden');
                $("#directoryTreeview").html("Loading. Please wait...");
                $("#breadcrumb").html("");

                $scope.media_props = getNewMediaProps();
                $scope.selectedSkipTiesDbServer = false;
                $scope.selectedJobPriorityServer = {selected: {priority: '4', type: '(default)'}}; //default
                filesToSubmit = [];//files going back to server
                $scope.filesToSubmitCount = filesToSubmit.length;
                $scope.selectedPipelineServer = {};//selected pipeline
                fileList = [];//current list of files for the selected folder
                $scope.useUploadView = false;
                if (!$scope.$$phase) $scope.$apply();

                MediaService.getMaxFileUploadCnt().then(function (max) {

                    MediaService.getAllDirectories(false, useCache).then(function (dirs) {
                        serverDirs = [];
                        serverDirs.push(dirs);
                        treeDirs = serverDirs;
                        selectedNode = {text: "None", nodeId: 0};

                        // need to map all the directories
                        directoryMap = {};
                        traverseNode(dirs, function (anode) {
                            directoryMap[anode.fullPath] = {
                                "total": 0,
                                "selected": 0,
                                "checked": false,
                                "checkable": false
                            };
                        });

                        maxFileUploadCnt = max;
                        buildDropzone();

                        MediaService.getAllFiles(dirs.fullPath, useCache).then(function (nodeData) {
                            $.each(nodeData.data, function (idx, anode) {
                                // need to update counts
                                if (anode.directory) {
                                    directoryMap[anode.directory].total += 1;
                                }
                            });

                            renderTree();
                            if (funct) funct();
                            $("#fileListWrap").css('visibility','show');
                        });
                    });
                });

                waitModal = $("#waitModal").modal({show: false});
                progressModal = $("#progressModal").modal({show: false});
                $('#viewNodeModal').on('hidden.bs.modal', function () {
                    checkedFilesInModal = [];
                    renderFileList();
                });

                $("#newFolderModal").on('shown.bs.modal', function(event) {//focus on input
                    $(this).find('[autofocus]').focus();
                });

                $('#detailsModal').on('show.bs.modal', function (event) {
                    var target = $(event.relatedTarget);
                    var text = target.data('title');// Extract info from data-* attributes
                    var modal = $(this);
                    modal.find('.modal-body').html(text);
                })
            };

            //need to retrieve child files and folders on expansions if no children exist
            var treenodeExpanded = function (node) {
                selectedNode = node;
                $("#directoryTreeview").treeview('selectNode', [node.nodeId, {silent: true}]);
                $("#breadcrumb").html(node.fullPath);
                if (!$scope.$$phase) $scope.$apply();
                renderFileList();
            };

            $scope.refreshRequest = function () {//refresh button for updating directory tree
                init(false);
            };

            //pull data from the server
            var reloadTree = function (funct) {
                init(true, funct);
            };

            var renderTree = function () {
                fileTree = $('#directoryTreeview').treeview({
                    levels: 2,
                    selectedColor: "#fff",
                    onhoverColor: "orange",
                    borderColor: "red",
                    showBorder: false,
                    showTags: true,
                    data: treeDirs,
                    showCheckbox: true,

                    onNodeChecked: function (event, node) {
                        $log.debug("Adding files in directory", node);
                        waitModal.modal('show');
                        //get all the files from the server recursively
                        MediaService.getAllFiles(node.fullPath).then(function (nodeData) {
                            addFilesToSubmit(nodeData.data);

                            //ugly hack because we get all the files recursively for the top directory as one list, need to map them
                            var temp = [];
                            $.each(nodeData.data, function (idx, anode) {
                                if (temp.indexOf(anode.directory) == -1) {
                                    directoryMap[anode.directory] = {
                                        "selected": 0,
                                        "total": 0,
                                        "checked": false,
                                        "checkable": true
                                    };//reset the counters
                                    temp.push(anode.directory);
                                }
                                directoryMap[anode.directory].selected += 1;
                                directoryMap[anode.directory].total += 1;
                            });

                            //need to update the tree to check all of them
                            traverseNode(node, function (anode) {
                                directoryMap[anode.fullPath].checked = true;
                                directoryMap[anode.fullPath].checkable = true;
                                //if(directoryMap[anode.fullPath].total > 0) directoryMap[anode.fullPath].checked = true;
                            });
                            renderFileList();
                            $("#directoryTreeview").treeview('selectNode', node.nodeId);//select the node
                            updateTreeChecksRecurse($("#directoryTreeview").treeview('getNode', 0));
                            updateTreeChecks();
                            waitModal.modal('hide');
                            if (!$scope.$$phase) $scope.$apply();
                        });
                    },
                    onNodeUnchecked: function (event, node) {
                        $log.debug("Removing files in directory", node);
                        waitModal.modal('show');
                        //get all the files from the server
                        MediaService.getAllFiles(node.fullPath).then(function (nodeData) {
                            removeFilesToSubmit(nodeData.data);

                            //ugly hack because we get all the files recursively for the top directory as one list, need to unmap them
                            var temp = [];
                            $.each(nodeData.data, function (idx, anode) {
                                if (temp.indexOf(anode.directory) == -1) {
                                    directoryMap[anode.directory] = {
                                        "selected": 0,
                                        "total": 0,
                                        "checked": false,
                                        "checkable": false
                                    };//reset the counters
                                    temp.push(anode.directory);
                                }
                                directoryMap[anode.directory].total += 1;
                            });
                            //need to update the tree to check all of them
                            traverseNode(node, function (anode) {
                                directoryMap[anode.fullPath].checked = false;
                                directoryMap[anode.fullPath].checkable = false;
                            });
                            renderFileList();
                            $("#directoryTreeview").treeview('selectNode', node.nodeId);//select the node
                            updateTreeChecksRecurse($("#directoryTreeview").treeview('getNode', 0));
                            updateTreeChecks();
                            waitModal.modal('hide');
                            if (!$scope.$$phase) $scope.$apply();
                        });
                    },
                    onNodeExpanded: function (event, node) {
                        treenodeExpanded(node);
                    },
                    onNodeSelected: function (event, node) {
                        treenodeExpanded(node);
                    }
                });
                $('#directoryTreeview').treeview('selectNode', selectedNode.nodeId);
                $('#directoryTreeview').treeview('revealNode', [selectedNode.nodeId, {silent: true}]);

                //check nodes if necessary
                updateTreeChecks();

                if (!$scope.$$phase) $scope.$apply();
            };

            var updateTreeChecks = function () {
                traverseNode($('#directoryTreeview').treeview('getNode', 0), function (anode) {
                    if (directoryMap[anode.fullPath].checked) {
                        $("#directoryTreeview").treeview('checkNode', [anode.nodeId, {silent: true}]);
                    } else {
                        $("#directoryTreeview").treeview('uncheckNode', [anode.nodeId, {silent: true}]);
                    }
                });
                $("#directoryTreeview").treeview('selectNode', selectedNode.nodeId);//select the node
            };

            var traverseNode = function (node, funct) {
                funct(node);
                if (node.nodes != null) {
                    var children = node.nodes;
                    for (var i = 0; i < children.length; i++) {
                        traverseNode(children[i], funct);
                    }
                }
            };

            var updateDirectoryMap = function (node, amt) {
                directoryMap[node.fullPath].selected += amt; //update selected count
                directoryMap[node.fullPath].checkable = true;
                updateTreeChecksRecurse($("#directoryTreeview").treeview('getNode', 0));
                updateTreeChecks();
            };

            var updateTreeChecksRecurse = function (node) {
                var childrenFull = true;
                if (node.nodes != null) {
                    for (var i = 0; i < node.nodes.length; i++) {
                        if (!updateTreeChecksRecurse(node.nodes[i])) {
                            childrenFull = false;
                        }
                    }
                }

                if ((directoryMap[node.fullPath].total == directoryMap[node.fullPath].selected) && childrenFull) {//check the node
                    if (directoryMap[node.fullPath].checkable) {
                        directoryMap[node.fullPath].checked = true;
                        // force parent to be checkable to ensure that the full file tree branch is updated
                        if (node.parentId != undefined) {
                            var parentnode = $('#directoryTreeview').treeview('getNode', node.parentId);
                            directoryMap[parentnode.fullPath].checkable = true;
                        }
                    }
                    return true;
                }

                directoryMap[node.fullPath].checked = false;
                return false;
            };

            //datatable for the files in a selected directory
            var renderFileList = function () {
                if (fileTable != null) {
                    fileTable.clear();
                    fileTable.draw();
                } else {
                    fileTable = $('#file_list_server').DataTable({
                        destroy: true,
                        // data: fileList,
                        stateSave: false,
                        serverSide: true,
                        processing: false,
                        renderer: 'bootstrap',
                        scrollY: '70vh',
                        scrollCollapse: false,
                        language: {
                            emptyTable: "No files in folder"
                        },
                        lengthMenu: [[5, 10, 25, 50, 100], [5, 10, 25, 50, 100]],
                        pageLength: 25,//set default value
                        ordering: false,
                        ajax: {
                            url: "server/get-all-files-filtered",
                            type: "POST",
                            headers: csrf.headers(),
                            data: function (d) {//extra params
                                d.fullPath = selectedNode.fullPath;
                                d.search = d.search.value;//pull out because spring is a pain to pass params
                            },
                            dataSrc: function (json) {//function after ajax returns
                                directoryMap[selectedNode.fullPath].total = json.recordsTotal;//save info about the number of files
                                return json.data;
                            }
                        },
                        columns: [
                            {
                                "data": "null", "defaultContent": '', width: "5%",
                                render: function (data, type, obj) {
                                    if (type === 'display') {
                                        var checked = "";
                                        if (hasFilesToSubmit(obj)) {
                                            checked = "checked";
                                        }
                                        return '<input type="checkbox" class="node-check" ' + checked + '>';
                                    }
                                    return data;
                                }
                            },
                            {
                                data: "fullPath",  width: "10%",
                                render: function (data, type, obj) {
                                    return getImgTag(obj);
                                }
                            },
                            {
                                data: 'name', width: "80%",
                            }
                        ],
                        initComplete: function (settings, json) {
                            if (!$scope.$$phase) $scope.$apply();
                            $log.debug('DataTables has finished its initialization.');
                        }
                    });

                    $('#file_list_server').on('change', 'input.node-check', function (e) {
                        var idx = fileTable.row($(this).closest('tr')[0]).index();
                        var node = fileTable.rows(idx).data()[0];
                        if (this.checked) {
                            addFilesToSubmit([node]);
                            updateDirectoryMap(selectedNode, 1);
                        } else {
                            removeFilesToSubmit([node]);
                            updateDirectoryMap(selectedNode, -1);
                        }
                    });
                }
            };

            $scope.uploadURLBtn = function (e, dt, node, config) {
                $log.debug("Upload to URL btn");
                $('#uploadURLsForm').trigger("reset");
                $scope.urls = null;
                $(".localName").html("");
                $("#URLUploadModal").modal({show: true});
            };

            var renderSelectedFileList = function () {
                // sort is in place; make 'A' come before 'B'; make 'A' come before 'a'
                filesToSubmit.sort(function (a, b) {
                    return a.fullPath.localeCompare(b.fullPath, undefined, { caseFirst: "upper" } )
                });
                if (selectedFileTable != null) {
                    selectedFileTable.clear();
                    // selectedFileTable.rows.add(filesToSubmit);
                    selectedFileTable.draw();
                } else {
                    $scope.media_props_file_info = "Loading. Please wait...";
                    selectedFileTable = $('#selected_file_list').DataTable({
                        destroy: true,
                        // data: filesToSubmit,
                        stateSave: false,
                        scrollY: '50vh',
                        serverSide: true,
                        scrollCollapse: true,
                        fixedColumns: true, // set widths
                        autoWidth: false, // set widths
                        ajax: function (data, callback, settings) {
                            var files = filesToSubmit;
                            if (data.search.value) {
                                files = files.filter(function (el) {
                                    return el.fullPath.toLowerCase().includes(data.search.value.toLowerCase());
                                });
                            }
                            var json = {
                                data: files.slice(data.start, data.start+data.length),
                                recordsFiltered: files.length,
                                recordsTotal: files.length
                            };
                            callback(json);
                        },
                        columns: [
                            {
                                "data": "null", "defaultContent": '',width: "2%",
                                render: function (data, type, obj) {
                                    if (checkedFilesInModal.indexOf(obj) != -1) {
                                        return '<input type="checkbox" class="node-checked" ng-model="" checked>';
                                    } else {
                                        return '<input type="checkbox" class="node-checked" ng-model="" >';
                                    }
                                }
                            },
                            {
                                data: "fullPath",width: "5%",
                                render: function (data, type, obj) {
                                    return getImgTag(obj);
                                }
                            },
                            {data: "fullPath",width: "75%",},
                            {
                                data: "null",width: "5%",
                                render: function (data, type, obj) {
                                    var retval = '<button type="button" class="btn btn-primary btn-xs set-properties-btn" title="Set Media Properties"><span class="fa fa-list"></span></button> ';
                                    if (obj.properties) {
                                        return retval + '<span class="fa fa-check-circle properties-set-mark"></span> ';
                                    }
                                    return retval + '<span class="fa fa-check-circle properties-set-mark" style="display:none;"></span> ';
                                }
                            },
                            {
                                data: "fullPath",width: "5%",
                                render: function (data, type, obj) {
                                    return '<button type="button" class="btn btn-danger btn-xs remove-btn" title="Remove"><span class="glyphicon glyphicon-remove"></span></button>';
                                }
                            }
                        ],
                        ordering: false,
                        select: false,
                        dom: '<"top"Blf>rt<"bottom"<"dt_foot1"i><"dt_foot2"p>><"clear">',//https://datatables.net/reference/option/dom

                        buttons: [
                            {
                                text: '<span class="fa fa-list"></span> Set Properties For Checked',
                                className: 'media-propertied-dt-button',
                                action: function () {
                                    var nodes = getCheckedFilesInModal();
                                    if (nodes.length > 0) {
                                        clearMediaPropsUI();
                                        $("#viewMediaPropertiesModal").modal({show: true});
                                        $scope.media_props_file_info = "";
                                        $.each(nodes, function () {
                                            var node = $(this)[0];
                                            // TODO: Handle network resource issues when displaying a large number of imgTags
                                            $scope.media_props_file_info += /*getImgTag(node) + " " +*/ node.fullPath + "<br/>";
                                        });
                                    }
                                }
                            },
                            {
                                text: "<span class='glyphicon glyphicon-remove'></span> Remove Checked",
                                className: 'remove-dt-button',
                                action: function () {
                                    var nodes = getCheckedFilesInModal();
                                    if (nodes.length > 0) {
                                        $('#directoryTreeview').treeview('uncheckAll', {silent: true});
                                        removeFilesToSubmit(nodes);
                                        $("#checkAll").prop("checked", false);
                                        traverseNode($("#directoryTreeview").treeview('getNode', 0), function (anode) {
                                            directoryMap[anode.fullPath].selected = 0;//reset the selected counter
                                            $("#directoryTreeview").treeview('uncheckNode', [anode.nodeId, {silent: true}])
                                        });
                                        renderSelectedFileList();
                                    }
                                    updateModifiedFilesChecks();
                                }
                            }
                        ],
                        initComplete: function () {
                            $(".dt-buttons").prepend("<div  style='padding-right:10px;display:inline-block;'><input type='checkbox' id='checkAll''> Check All</div> ");
                            $("#checkAll").click(function () {//check all button
                                checkAllFiles($(this).prop('checked'));
                            });
                        }
                    });

                    $('#selected_file_list').on('click', '.node-checked', function () {
                        var idx = selectedFileTable.row($(this).closest('tr')[0]).index();
                        var node = selectedFileTable.rows(idx).data()[0];
                        if(!$(this).prop('checked')){
                            $("#checkAll").prop("checked", false);
                            checkedFilesInModal = checkedFilesInModal.filter(function(item) {
                                return item !== node; // remove from array
                            });
                        } else {
                            checkedFilesInModal.push(node);
                        }
                        if (filesToSubmit.length == checkedFilesInModal.length) {
                            $("#checkAll").prop("checked", true);
                        }
                    });

                    $('#selected_file_list').on('click', '.remove-btn', function () {
                        var data = selectedFileTable.row(this.parentNode).data();
                        removeFilesToSubmit([data]);
                        $("#checkAll").prop("checked", false);
                        updateDirectoryMap({"fullPath": data.directory}, -1);
                        updateModifiedFilesChecks();
                    });

                    $('#selected_file_list').on('click', '.set-properties-btn', function () {
                        var idx = selectedFileTable.row($(this).closest('tr')[0]).index();
                        var node = selectedFileTable.rows(idx).data()[0];
                        $scope.media_props = (typeof node.properties !== "undefined") ? $.extend(true, {}, node.properties) : getNewMediaProps();
                        // TODO: Handle network resource issues when displaying a large number of imgTags
                        $scope.media_props_file_info = /*getImgTag(node) + " " +*/ node.fullPath;
                        checkedSingleFileInModal = node;
                        $("#viewMediaPropertiesModal").modal({show: true});
                    });
                }
            };

            var clearMediaPropsUI = function(){
                $scope.media_props = getNewMediaProps();
                $scope.media_props_file_info = "Loading. Please wait...";
                checkedSingleFileInModal = null;
            }

            $scope.closeSelectedFileList = function () {
                $("#checkAll").prop("checked", false);
                checkAllFiles(false);
                clearMediaPropsUI();
            };

            //new blank media properties
            var getNewMediaProps = function () {
                return {
                    AUTO_ROTATE: false,
                    ROTATION: "0",
                    AUTO_FLIP: false,
                    HORIZONTAL_FLIP: false,
                    SEARCH_REGION_ENABLE_DETECTION: false,
                    SEARCH_REGION_TOP_LEFT_X_DETECTION: -1,
                    SEARCH_REGION_TOP_LEFT_Y_DETECTION: -1,
                    SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION: -1,
                    SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION: -1
                };
            };

            //mark every checkbox in the table checked or not
            var checkAllFiles = function (checked) {
                if (checked) {
                    checkedFilesInModal = filesToSubmit;
                } else {
                    checkedFilesInModal = [];
                }
                selectedFileTable.rows().every(function (rowIdx, tableLoop, rowLoop) {
                    $(this.node().cells[0].childNodes[0]).prop('checked', checked);//the checkbox
                });
            };

            //return a list of nodes that have the checkmark checked
            var getCheckedFilesInModal = function () {
                if (checkedSingleFileInModal != null) {
                    var retval = [];
                    retval.push(checkedSingleFileInModal);
                    return retval;
                }
                return checkedFilesInModal;
            };

            //go through each datatble row and show set mark if they are set
            var updateModifiedFilesChecks = function () {
                selectedFileTable.rows().every(function (rowIdx, tableLoop, rowLoop) {
                    if (typeof this.data().properties !== "undefined") {
                        $(this.node().cells[3]).find('.properties-set-mark').show();
                    } else {
                        $(this.node().cells[3]).find('.properties-set-mark').hide();
                    }
                });
            };

            $scope.setMediaProperties = function () {
                var checkedFilesInModal = getCheckedFilesInModal();
                //add to sending data
                for (var i = 0; i < filesToSubmit.length; i++) {
                    for (var j = 0; j < checkedFilesInModal.length; j++) {
                        if (filesToSubmit[i].fullPath == checkedFilesInModal[j].fullPath) {
                            filesToSubmit[i].properties = $.extend(true, {}, $scope.media_props);
                            break;
                        }
                    }
                }
                //clean up
                clearMediaPropsUI();
                updateModifiedFilesChecks();
            };

            $scope.clearMediaProperties = function () {
                clearMediaPropsUI();
                if (!$scope.$$phase) $scope.$apply();
                var checkedFilesInModal = getCheckedFilesInModal();
                for (var i = 0; i < filesToSubmit.length; i++) {
                    var node = filesToSubmit[i];
                    for (var j = 0; j < checkedFilesInModal.length; j++) {
                        if (node.fullPath == checkedFilesInModal[j].fullPath) {
                            delete filesToSubmit[i].properties;
                            break;
                        }
                    }
                }
                updateModifiedFilesChecks();
            };

            $scope.cancelMediaProperties = function () {
                clearMediaPropsUI();
            };

            $scope.setChecks = function () {
                if ($scope.media_props.AUTO_FLIP) {
                    $scope.media_props.HORIZONTAL_FLIP = false;
                }
                if ($scope.media_props.AUTO_ROTATE) {
                    $scope.media_props.ROTATION = "0";
                }
                if (!$scope.media_props.SEARCH_REGION_ENABLE_DETECTION) {
                    $scope.media_props.SEARCH_REGION_TOP_LEFT_X_DETECTION = -1;
                    $scope.media_props.SEARCH_REGION_TOP_LEFT_Y_DETECTION = -1;
                    $scope.media_props.SEARCH_REGION_BOTTOM_RIGHT_X_DETECTION = -1;
                    $scope.media_props.SEARCH_REGION_BOTTOM_RIGHT_Y_DETECTION = -1;
                }
            };

            var getImgTag = function (obj) {
                if (obj.contentType == null || obj.contentType.length == 0) return "";
                var t = obj.contentType.split("/")[0].toLowerCase();
                if (t == "image" && obj.contentType.indexOf("tif") == -1) {
                    var imgUrl = "server/node-image?" + $.param({nodeFullPath: obj.fullPath});
                    return $("<img>").addClass('img-rounded').addClass('media-thumb').attr('src', imgUrl).get(0).outerHTML;
                } else if (t == "audio") {
                    return "<span class='glyphicon glyphicon-music media-thumb'></span>";
                }
                else if (t == "video") {
                    return "<span class='glyphicon glyphicon-film media-thumb'></span>";
                }
                else {
                    return "<span class='glyphicon glyphicon-file media-thumb'></span>";
                }
            };

            var addFilesToSubmit = function (arr) {
                for (var i = 0; i < arr.length; i++) {
                    var idx = -1;
                    for (var j = 0; j < filesToSubmit.length; j++) {
                        var node = filesToSubmit[j];
                        if (node.fullPath == arr[i].fullPath) {
                            idx = j;
                            break;
                        }
                    }
                    if (idx < 0) {
                        filesToSubmit.push(arr[i]);
                    }
                }
                $scope.filesToSubmitCount = filesToSubmit.length;
                if (!$scope.$$phase) $scope.$apply();
                renderSelectedFileList();
            };

            var removeFilesToSubmit = function (arr) {
                for (var i = 0; i < arr.length; i++) {
                    filesToSubmit = filesToSubmit.filter(function(item) {
                        return item.fullPath !== arr[i].fullPath; // remove from array
                    });
                }
                $scope.filesToSubmitCount = filesToSubmit.length;
                if (!$scope.$$phase) $scope.$apply();
                renderSelectedFileList();
            };

            var hasFilesToSubmit = function (node) {
                for (var j = 0; j < filesToSubmit.length; j++) {
                    if (filesToSubmit[j].fullPath == node.fullPath) {
                        return true;
                    }
                }
                return false;
            };

            $scope.submitFiles = function () {
                if (!$scope.selectedPipelineServer.selected) {
                    NotificationSvc.error('Please select a pipeline!');
                    return false;
                }

                //build media uri array from filesToSubmit map
                var files = [];

                for (var i = 0; i < filesToSubmit.length; i++) {
                    var node = {mediaUri: filesToSubmit[i].uri,properties:{}};
                    if(typeof filesToSubmit[i].properties !== "undefined") {
                        node.properties = filesToSubmit[i].properties;
                        node.properties['ROTATION'] = parseInt(node.properties['ROTATION']);//convert to int
                    }
                    files.push(node);
                }

                if (files.length > 0) {
                    var jobCreationRequest = {
                        media: files,
                        pipelineName: $scope.selectedPipelineServer.selected,
                        priority: $scope.selectedJobPriorityServer.selected.priority,
                        jobProperties: {
                            SKIP_TIES_DB_CHECK: $scope.selectedSkipTiesDbServer.toString()
                        }
                    };

                    //finally submit the job
                    MediaService.createJobFromMedia(jobCreationRequest).then(function (jobCreationResponse) {
                        if (jobCreationResponse.mpfResponse.responseCode == 0) {
                            const idTokens = jobCreationResponse.jobId.split("-");
                            var internalJobId = idTokens[idTokens.length-1];
                            NotificationSvc.success('Job ' + internalJobId + ' created!');
                            $log.info('successful job creation - switch to jobs view');

                            $location.path('/jobs');//go to jobs view
                            if (!$scope.$$phase) $scope.$apply()
                        } else {
                            NotificationSvc.error(jobCreationResponse.mpfResponse.message);
                        }
                    });
                } else {
                    NotificationSvc.error('Please select some files!');
                    return false;
                }
            };

            var buildDropzone = function () {
                if (dropzone) dropzone.destroy();

                dropzone = new Dropzone("#fileManager",
                    {
                        url: fileUploadURL,
                        headers: csrf.headers(),
                        autoProcessQueue: true,
                        maxFiles: maxFileUploadCnt,
                        maxFilesize: 5000, //MB
                        addRemoveLinks: false,
                        previewsContainer: "#dropzone-preview",
                        clickable: ".fileinput-button",
                        init: function () {
                            var self = this;
                            successfulUploads = 0;
                            cancelledUploads = 0;

                            self.on("addedfile", function (file, xhr, formData) {//on each file
                                if (allowUpload) {
                                    if (!modalShow) {
                                        progressModal.modal('show');
                                        modalShow = true;
                                        $("#cancelUpload").show();
                                        $(".closeUpload").hide();
                                        $("#dropzone-preview").height($("#progressModal").height() - 280);
                                        $("#uploadTitle").html("Please Wait");
                                    }
                                } else {
                                    self.cancelUpload(file);
                                    cancelledUploads += 1;
                                }
                            });
                            self.on("sending", function (file, xhr, formData) {//on each file
                                if (allowUpload) {
                                    formData.append('desiredpath', selectedNode.fullPath);
                                } else {
                                    self.cancelUpload(file);
                                    cancelledUploads += 1;
                                }
                            });
                            self.on("success", function (file) {
                                successfulUploads += 1;
                            });
                            // Hide the total progress bar when nothing's uploading anymore
                            self.on("queuecomplete", function (progress) {
                                $log.debug("Queue Complete");
                                updateProgress();
                                $("#cancelUpload").hide();
                                $(".closeUpload").show();
                                $("#uploadTitle").html("Finished");
                                updateUploading();
                                allowUpload = true;
                            });
                            // Update the total progress bar
                            self.on("totaluploadprogress", function (uploadProgress, totalBytes, totalBytesSent) {//on each file, the progress it passes in isnt correct
                                if (uploadProgress) {
                                    updateProgress(uploadProgress, totalBytes, totalBytesSent);
                                }
                            });
                            self.on("maxfilesreached", function (file_list) {
                                $log.error("Max files reached");
                                if (allowUpload) NotificationSvc.error("Maximum Files (" + self.options.maxFiles + ") Reached ");
                                $scope.cancelUpload();
                            });
                            self.on("maxfilesexceeded", function (file_list) {
                                $log.error("maxfilesexceeded");
                                if (allowUpload) NotificationSvc.error("Maximum Files (" + self.options.maxFiles + ") Exceeded ");
                                $scope.cancelUpload();
                            });
                            self.on("error", function (file, resp) {
                                updateProgress();
                                $log.error(resp.message, file);
                                $(file.previewElement).find('.dz-error-message').text(resp.message);
                            });
                        }
                    }
                ); //end of dropzone creation
            }

            var updateUploading = function () {
                var uploading = "";
                angular.forEach(dropzone.getUploadingFiles(), function (file) {
                    uploading += file.name + " ";
                });
                if (uploading.length > 0)
                    $("#uploading").html("<strong>Uploading: </strong>" + uploading);
                else {
                    $("#uploading").html("");
                }
            }

            var updateProgress = function (uploadProgress, totalBytes, totalBytesSent) {
                var total = dropzone.files.length;
                var queue = dropzone.getQueuedFiles().length;
                var errorCount = dropzone.getFilesWithStatus(Dropzone.ERROR).length;
                var rejected = dropzone.getRejectedFiles().length + errorCount;
                updateUploading();

                $("#total-count").html(total);
                $("#queued-count").html(queue);
                $("#cancelled-count").html(cancelledUploads);
                if (cancelledUploads > 0) {
                    $("#cancelled-count").css("color", "red");
                } else {
                    $("#cancelled-count").css("color", "#000");
                }
                $("#rejected-count").html(rejected);
                if (rejected > 0) {
                    $("#rejected-count").css("color", "red");
                } else {
                    $("#rejected-count").css("color", "#000");
                }
                $("#success-count").html(successfulUploads);
                var cur = successfulUploads;//total - queue;
                var prog = Math.round(cur / total * 100);
                if (uploadProgress) prog = uploadProgress;
                if (prog && totalBytes && totalBytesSent) {
                    $("#total-progress .progress-bar").css("width", prog + "%");
                    $("#fileCount").html("<strong>Bytes:</strong> " + totalBytesSent + "/" + totalBytes);
                    $("#percent").html(Math.round(prog) + "%");
                }
            };

            $scope.closeUpload = function () {
                $log.debug("Close Upload");
                Dropzone.forElement("#fileManager").removeAllFiles(true);
                //reset the modal
                progressModal.modal('hide');
                progressModal.on('hidden.bs.modal', function () {
                    $("#total-progress .progress-bar").css("width", "0%");
                    $("#uploadTitle").html("Please Wait");
                    $("#fileCount").html("0/0");
                    $("#percent").html("0%");
                    $("#uploading").html("");
                    allowUpload = true;
                    modalShow = false;
                });
                dropzone.destroy();
                var oldnode = selectedNode.nodeId;
                reloadTree(function () {
                    $("#directoryTreeview").treeview('selectNode', oldnode);
                    $("#directoryTreeview").treeview('revealNode', [oldnode, {silent: true}]);
                });
            };

            $scope.cancelUpload = function () {
                $log.debug("Cancelled Upload - queued:" + dropzone.getQueuedFiles().length + " uploading:" + dropzone.getUploadingFiles().length);
                if (allowUpload) {
                    allowUpload = false;
                    $("#uploadTitle").html("Cancelling Upload");
                    angular.forEach(dropzone.getQueuedFiles(), function (file) {
                        dropzone.cancelUpload(file);
                        cancelledUploads += 1;
                        $log.debug("Cancelled Queued file: " + file.name);
                    });

                    $(".closeUpload").show();
                    $("#cancelUpload").hide();

                    updateUploading();
                    alert("Any remaining files in the upload queue have been canceled. Any completed files will need to be manually be removed from the file system. Any files currently being uploaded will continue to be uploaded and will need to be manually removed at a later time.");
                }
            };

            //create a new folder on the server then reload tree
            $scope.addFolder = function () {
                if ($scope.newfolder && $scope.newfolder.length > 0) {
                    var parent = selectedNode;
                    var path = selectedNode.fullPath + "/" + $scope.newfolder;
                    $log.debug("Adding new folder", path);
                    if (!$scope.$$phase) $scope.$apply();
                    MediaService.createDirectory(path).then(function (data) {
                        var folder = $scope.newfolder;
                        $scope.newfolder = "";
                        reloadTree(function () {
                            selectFolder(parent.nodeId, folder);
                            if (!$scope.$$phase) $scope.$apply();
                        });
                    }, function (e) {
                        console.log("error", e);
                        NotificationSvc.error("Cannot create folder '" + $scope.newfolder + "':" + e);
                        $scope.newfolder = "";
                        if (!$scope.$$phase) $scope.$apply();
                    });
                }
            };

            //find the folder by name
            var selectFolder = function (parentnodeid, name) {
                var parentnode = $('#directoryTreeview').treeview('getNode', parentnodeid);
                var children = parentnode['nodes'];
                for (var i = 0; i < children.length; i++) {
                    var node = children[i];
                    if (node.text == name) {
                        selectedNode = node;
                        while (parentnode.nodeId != 0) {
                            $('#directoryTreeview').treeview('expandNode', [parentnode.nodeId, {silent: true}]);//expand parent
                            parentnode = $('#directoryTreeview').treeview('getNode', parentnode.parentId);
                        }
                        $('#directoryTreeview').treeview('selectNode', node.nodeId);
                        $('#directoryTreeview').treeview('revealNode', [selectedNode.nodeId, {silent: true}]);
                        break;
                    }
                }
            };

            $scope.submitUploadURLsForm = function (event) {
                //if the url is empty - alert and return
                if ($scope.urls == null || !$scope.urls || !$scope.urls.trim()) {
                    NotificationSvc.error('Please add a URL first');
                    return;
                }
                var splitUrls = $scope.urls.split('\n');
                if (splitUrls) {
                    splitUrls = splitUrls.filter(function(item) {
                        return item.trim() != ""; // remove from array
                    });
                }

                //filter out duplicates
                var uniqueUrls = [];
                $.each(splitUrls, function(i, e) {
                    if ($.inArray(e, uniqueUrls) == -1) uniqueUrls.push(e);
                });

                if (uniqueUrls && uniqueUrls.length > 0) {
                    $("#loading_url").show();
                    $("#submitURLs").prop("disabled", true);

                    var params = {urls: uniqueUrls, desiredpath: selectedNode.fullPath};
                    $http({
                        url: 'saveURL',
                        method: "POST",
                        params: params
                    }).then(
                        function (response) {
                            console.log(response.data);
                            $("#loading_url").hide();
                            var fileResultsMap = response.data;
                            var successCnt = 0;
                            for (var key in fileResultsMap) {
                                if (fileResultsMap.hasOwnProperty(key)) {
                                    if (fileResultsMap[key].startsWith("successful write to")) {
                                        var filename = fileResultsMap[key].replace(/^.*[\\\/]/, '');
                                        ++successCnt;
                                        $(".localName").append("<p>Uploaded: " + key + " to " + filename+"</p>");
                                    } else {
                                        NotificationSvc.error('Error uploading: ' + key + ' ' + fileResultsMap[key]);
                                    }
                                }
                            }
                            if (successCnt == uniqueUrls.length) {
                                NotificationSvc.success(successCnt + " files uploaded.");
                            } else {
                                NotificationSvc.error('Error uploading files. Uploaded ' + successCnt + ' out of ' + uniqueUrls.length + ' files. Please check the Workflow Manager server status and logs.');
                                console.log('Error loading image:', status);
                            }

                            var nodeid = selectedNode.nodeId;
                            reloadTree(function () {
                                $("#directoryTreeview").treeview('selectNode', nodeid);
                                $("#directoryTreeview").treeview('revealNode', [nodeid, {silent: true}]);
                                $("#loading_url").hide();
                                $("#submitURLs").prop("disabled", false);
                            });
                        },
                        function (httpError) {
                            console.log(httpError.data.error);
                            NotificationSvc.error('Error sending the request to the server, please check the Workflow Manager server status and logs.');
                            console.log('Error loading image:', status);
                            console.log('Error message: ' + error);
                            $("#loading_url").hide();
                            $("#submitURLs").prop("disabled", false);
                        });
                }
            };

            init(true);//get this party started
        }]);
})();

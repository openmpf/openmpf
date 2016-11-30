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

(function () {
    'use strict';

    /**
     * ServerMediaCtrl
     * @constructor
     */
    angular.module('WfmAngularSpringApp.controller.ServerMediaCtrl', []).controller('ServerMediaCtrl', ['$scope', '$rootScope', '$http', '$location', '$timeout', '$log', '$compile', 'MediaService', 'JobsService', 'NotificationSvc',
        function ($scope, $rootScope, $http, $location, $timeout, $log, $compile, MediaService, JobsService, NotificationSvc) {

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
            $scope.disableBtns = true;
            var successfulUploads = 0;
            var cancelledUploads = 0;
            var modalShow = false;
            var progressModal = null;
            var dropzone = null;
            var serverDirs = [];//the directory structure
            var treeDirs = [];
            $scope.selectedJobPriorityServer = {selected: {priority: '4', type: '(default)'}}; //default
            var filesToSubmit = [];//files going back to server
            $scope.filesToSubmitCount = filesToSubmit.length;
            $scope.selectedPipelineServer = {};//selected pipeline
            $scope.urls = null;

            var init = function (funct) {//reset
                $("#loading_url").hide();
                $scope.selectedJobPriorityServer = {selected: {priority: '4', type: '(default)'}}; //default
                filesToSubmit = [];//files going back to server
                $scope.filesToSubmitCount = filesToSubmit.length;
                $scope.selectedPipelineServer = {};//selected pipeline
                fileList = [];//current list of files for the selected folder
                $scope.useUploadView = false;
                $scope.disableBtns = true;
                $("#directoryTreeview").html("Loading...");
                if (!$scope.$$phase) $scope.$apply();

                MediaService.getMaxFileUploadCnt().then(function (max) {
                    MediaService.getAllDirectories(false).then(function (dirs) {
                        serverDirs = [];
                        serverDirs.push(dirs);
                        treeDirs = serverDirs;
                        selectedNode = {text: "None", nodeId: 0};

                        //need to map all the nodes
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
                        renderTree();
                        if (funct) funct();
                    });
                });

                waitModal = $("#waitModal").modal({show: false});
                progressModal = $("#progressModal").modal({show: false});
                $('#viewNodeModal').on('hidden.bs.modal', function () {
                    renderFileList();
                });
            };

            //need to retrieve child files and folders on expansions if no children exist
            var treenodeExpanded = function (node) {
                selectedNode = node;
                $("#directoryTreeview").treeview('selectNode', [node.nodeId, {silent: true}]);
                $("#breadcrumb").html(node.fullPath);
                if (node.canUpload) {
                    $scope.disableBtns = false;
                } else {
                    $scope.disableBtns = true;
                }
                if (!$scope.$$phase) $scope.$apply();
                renderFileList();
            };

            $scope.refreshRequest = function () {//refresh button for updating directory tree
                init();
            };

            //pull data from the server
            var reloadTree = function (funct) {
                init(funct);
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
                        MediaService.getAllFiles(node.fullPath, true).then(function (nodeData) {
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
                        MediaService.getAllFiles(node.fullPath, true).then(function (nodeData) {
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

                $scope.disableBtns = true;

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
                updateTreeChecksRecurse($("#directoryTreeview").treeview('getNode', 0));
                updateTreeChecks();
            }

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
                    if (directoryMap[node.fullPath].checkable) directoryMap[node.fullPath].checked = true;
                    // if(directoryMap[node.fullPath].total > 0) directoryMap[node.fullPath].checked = true;
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
                        data: fileList,
                        stateSave: false,
                        serverSide: true,
                        processing: false,
                        scrollY:'45vh',
                        scrollCollapse: false,
                        lengthMenu: [[5, 10, 25, 50, 100], [5, 10, 25, 50, 100]],
                        //pageLength:5,//set default value
                        ordering: false,
                        ajax: {
                            url: "server/get-all-files-filtered",
                            type: "POST",
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
                                "data": "null", "defaultContent": '', width: "10%",
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
                                data: "fullPath", targets: [2], width: "10%",
                                render: function (data, type, obj) {
                                    return getImgTag(obj);
                                }
                            },
                            {
                                data: 'name'
                            }
                        ],
                        dom: '<"top"lf>rt<"bottom"<"dt_foot1"i><"dt_foot2"p>><"clear">',//https://datatables.net/reference/option/dom
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
                if (selectedFileTable != null) {
                    selectedFileTable.clear();
                    selectedFileTable.rows.add(filesToSubmit);
                    selectedFileTable.draw();
                } else {
                    selectedFileTable = $('#selected_file_list').DataTable({
                        destroy: true,
                        data: filesToSubmit,
                        scrollY: '30vh',
                        scrollCollapse: true,
                        columns: [
                            {
                                data: "fullPath", targets: [2],
                                render: function (data, type, obj) {
                                    return '<button type="button" class="btn btn-danger btn-xs removebtn"><span class="glyphicon glyphicon-remove"></span></button>';
                                }
                            },
                            {
                                data: "fullPath", targets: [2],
                                render: function (data, type, obj) {
                                    return getImgTag(obj);
                                }
                            },
                            {data: "fullPath"}
                        ],
                        ordering: false,
                        select: false,
                        dom: 'Blfrtip',//https://datatables.net/reference/option/dom
                        buttons: [
                            {
                                text: 'Remove All',
                                action: function () {
                                    var s = selectedFileTable.rows().select();
                                    var selected = s.data();// table.api().rows( { selected: true } );
                                    var arr = [];
                                    for (var i = 0; i < selected.length; i++) {
                                        arr.push(selected[i]);
                                    }
                                    $('#directoryTreeview').treeview('uncheckAll', {silent: true});
                                    removeFilesToSubmit(arr);
                                    traverseNode($("#directoryTreeview").treeview('getNode', 0), function (anode) {
                                        directoryMap[anode.fullPath].selected = 0;//reset the selected counter
                                        $("#directoryTreeview").treeview('uncheckNode', [anode.nodeId, {silent: true}])
                                    });
                                    renderSelectedFileList();
                                }
                            }
                        ]
                    });
                    $('#selected_file_list').on('click', '.removebtn', function () {
                        var data = selectedFileTable.row(this.parentNode).data();
                        removeFilesToSubmit([data]);
                        updateDirectoryMap({"fullPath": data.directory}, -1);
                    });
                }
            };

            var getImgTag = function (obj) {
                if (obj.contentType == null || obj.contentType.length == 0) return "";
                var t = obj.contentType.split("/")[0].toLowerCase();
                if (t == "image" && obj.contentType.indexOf("tif") == -1) {
                    var imgUrl = "server/node-image?" + $.param({nodeFullPath: obj.fullPath});
                    return $("<img>")
                        .addClass('img-rounded')
                        .addClass('media-thumb')
                        .attr('src', imgUrl)
                        .get(0)
                        .outerHTML;
                } else if (t == "audio") {
                    return "<span class='glyphicon glyphicon-music'></span>";
                }
                else if (t == "video") {
                    return "<span class='glyphicon glyphicon-film'></span>";
                }
                else {
                    return "<span class='glyphicon glyphicon-file'></span>";
                }
            }

            var addFilesToSubmit = function (arr) {
                for (var i = 0; i < arr.length; i++) {
                    var fullpath = arr[i].fullPath;
                    var idx = -1;
                    for (var j = 0; j < filesToSubmit.length; j++) {
                        var node = filesToSubmit[j];
                        if (node.fullPath == fullpath) {
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
                    var fullpath = arr[i].fullPath;
                    for (var j = 0; j < filesToSubmit.length; j++) {
                        var node = filesToSubmit[j];
                        if (node.fullPath == fullpath) {
                            filesToSubmit.splice(j, 1);
                            break;
                        }
                    }
                }
                $scope.filesToSubmitCount = filesToSubmit.length;
                if (!$scope.$$phase) $scope.$apply();
                renderSelectedFileList();
            };

            var hasFilesToSubmit = function (node) {
                for (var j = 0; j < filesToSubmit.length; j++) {
                    var subnode = filesToSubmit[j];
                    if (subnode.fullPath == node.fullPath) {
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
                var fileUris = [];

                for (var i = 0; i < filesToSubmit.length; i++) {
                    var node = {};
                    node.mediaUri = filesToSubmit[i].uri;
                    fileUris.push(node);
                }

                if (fileUris.length > 0) {
                    var jobCreationRequest = {
                        media: fileUris,
                        externalId: 'from_mpf_web_app', //hack to get jobs to session jobs
                        pipelineName: $scope.selectedPipelineServer.selected,
                        priority: $scope.selectedJobPriorityServer.selected.priority
                    };

                    //finally submit the job
                    MediaService.createJobFromMedia(jobCreationRequest).then(function (jobCreationResponse) {
                        //TODO: should check jobCreationResponse.success field
                        NotificationSvc.success('Job ' + jobCreationResponse.jobId + ' created!');

                        $log.info('successful job creation - switch to jobs view');

                        $location.path('/jobs');//go to jobs view
                        if (!$scope.$$phase) $scope.$apply()
                    });
                } else {
                    NotificationSvc.error('Please select some files!');
                    return false;
                }
            };

            var buildDropzone = function () {
                if (dropzone) dropzone.destroy();
                MediaService.getCustomUploadExtensions().then(function (customExtensions) {
                    var acceptedFiles = "video/*,image/*,audio/*";
                    if (customExtensions && customExtensions.length > 0) {
                        acceptedFiles = acceptedFiles + "," + customExtensions.join(',');
                    }

                    dropzone = new Dropzone("#fileManager",
                        {
                            url: fileUploadURL,
                            autoProcessQueue: true,
                            maxFiles: maxFileUploadCnt,
                            maxFilesize: 5000, //MB
                            addRemoveLinks: false,
                            acceptedFiles: acceptedFiles,
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
                                    var err = resp;
                                    if(resp.error){
                                        err = resp.error;
                                    }else if(resp == "You can't upload files of this type."){
                                        err += " Please add a whitelist."+file.type+" entry to the mediaType.properties file."
                                    }
                                    $log.error(err,file);
                                    $(file.previewElement).find('.dz-error-message').text(err);
                                });
                            }
                        }
                    ); //end of dropzone creation
                }); //end of MediaService.getCustomUploadExtensions()
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
                var rejected = dropzone.getRejectedFiles().length;
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
                    $scope.disableBtns = true;
                    if (!$scope.$$phase) $scope.$apply();
                    MediaService.createDirectory(path).then(function (data) {
                        var folder = $scope.newfolder;
                        $scope.newfolder = "";
                        reloadTree(function () {
                            selectFolder(parent.nodeId, folder);
                            $scope.disableBtns = false;
                            if (!$scope.$$phase) $scope.$apply();
                        });
                    }, function (e) {
                        console.log("error", e);
                        NotificationSvc.error("Cannot create folder '" + $scope.newfolder + "':" + e);
                        $scope.newfolder = "";
                        $scope.disableBtns = false;
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
                    //clean empty lines
                    for (var i = 0; i < splitUrls.length; i++) {
                        if (splitUrls[i].trim() == "") {
                            splitUrls.splice(i, 1);
                            i--;
                        }
                    }
                }

                var dataToSend = splitUrls;
                if (splitUrls && splitUrls.length > 0) {
                    $("#loading_url").show();
                    $("#submitURLs").prop("disabled", true);

                    var params = {urls:dataToSend,desiredpath:selectedNode.fullPath};
                    $http({
                        url: 'saveURL',
                        method: "POST",
                        params: params
                    }).then(
                        function (response) {
                            console.log( response.data);
                            $("#loading_url").hide();
                            var fileResultsMap = response.data;
                            var successCnt = 0;
                            for (var key in fileResultsMap) {
                                if (fileResultsMap.hasOwnProperty(key)) {
                                    if (fileResultsMap[key].startsWith("successful write to")) {
                                        ++successCnt;
                                        $(".localName").append("<p>Uploaded: " + key + "</p>");
                                    } else {
                                        NotificationSvc.error('Error uploading: ' + key + ' ' + fileResultsMap[key]);
                                    }
                                }
                            }
                            if (successCnt == splitUrls.length) {
                                NotificationSvc.success(successCnt + " files uploaded.");
                            } else {
                                NotificationSvc.error('Error uploading files. Uploaded ' + successCnt + ' out of ' + splitUrls.length + ' files. Please check the Workflow Manager server status and logs.');
                                console.log('Error loading image:', status);
                            }

                            var nodeid = selectedNode.nodeId;
                            reloadTree(function () {
                                $("#directoryTreeview").treeview('selectNode',nodeid );
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

            init();//get this party started
        }]);
})();

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
 * MarkupCtrl
 * @constructor
 */
var MarkupCtrl = function ($scope, $log, $http) {
    var table = null;
    var tableData = [];
    var resizeTimer;

    var init = function () {
        $(window).on('resize', function (e) {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function () {
                resizeViews();
            }, 250);
        });
        resizeViews();
    };

    var resizeViews = function () {
        var maxheight = $("body").height() - $("#navbarMain").height() - 20 - $("#header").height() - 100;
        $("#main").height(maxheight);
        renderTable();
    }

    var renderTable = function () {
        if (table != null) {
            table.clear();
            table.draw();
        } else {
            table = $('#markupsTable').DataTable({
                destroy: true,
                data: tableData,
                stateSave: false,
                serverSide: true,
                processing: true,
                scrollY: '47vh',
                scrollCollapse: false,
                lengthMenu: [[5, 10, 25, 50, 100], [5, 10, 25, 50, 100]],
                pageLength: 10,
                ordering: false,
                ajax: {
                    url: "markup/get-markup-results-filtered",
                    type: "POST",
                    data: function (d) {//extra params
                        d.search = d.search.value;//pull out because spring is a pain to pass params
                    }
                },
                columns: [//Id 	Job Id 	Action History 	Output Path 	Source Medium 	View 	Download
                    {data: "id", width: "5%"},
                    {data: "jobId", width: "5%"},
                    {data: "actionHistory", width: "20%"},
                    {data: "outputPath", width: "35%"},
                    {
                        data: "sourceMedium", width: "25%"
                    },
                    {
                        "data": "null", "defaultContent": '', width: "5%", orderable: false,
                        render: function (data, type, obj) {
                            var disabled = "disabled=disabled";
                            if (obj.image) disabled = "";
                            return '<button id="viewBtn' + obj.id + '" class="btn btn-mini btn-success preview" ' + disabled + ' ">View</button>';
                        }
                    },
                    {
                        "data": "null", "defaultContent": '', orderable: false, width: "5%",
                        render: function (data, type, obj) {
                            var disabled = "disabled=disabled";
                            if (obj.fileExists) disabled = "";
                            return '<a href="markup/download?id=' + obj.id + '" download="' + getMarkupText(obj) + '" class="btn btn-info btn-mini" role="button" ' + disabled + '>Download</a>';
                        }
                    }
                ],
                dom: '<"top"lBf>rt<"bottom"<"dt_foot1"i><"dt_foot2"p>><"clear">',//https://datatables.net/reference/option/dom
                buttons: [
                    {
                        text: 'Refresh',
                        className: 'btn btn-warning btn-refresh',
                        action: function () {
                            resizeViews();
                        }
                    }
                ],
                initComplete: function (settings, json) {
                    $log.debug("Table complete");
                }
            });
        }
        $('#markupsTable').on('draw.dt', function () {
            $(".preview").click(function () {//bind all the preview buttons
                var id = $(this).attr("id").replace("viewBtn", "");
                $scope.previewImage(id);
            });
            $('[data-toggle="popover"]').popover();
            $(".btn-refresh").removeClass("dt-button");
        });
    };

    var getMarkupText = function (markupResult) {
        var timeNow = new Date();
        var dateStr = (timeNow.getMonth() + 1) + "_" + timeNow.getUTCDate() + "_" + timeNow.getFullYear();
        return dateStr + '_jobId-' + markupResult.jobId + '_markupId-' + markupResult.id + '.' + markupResult.outputPath.split('.').pop();
    }

    //TODO: convert this to a bootstrap modal or boostrap ui modal in the future
    $scope.previewImage = function (id) {
        //Get the HTML Elements
        var imageDialog = $("#popupDialog");
        var imageTag = $('#popupDialogImg');

        //Set the image src
        imageTag.attr('src', 'markup/content?id=' + id); // imageResponse);

        //When the image has loaded, display the dialog
        imageTag.load(function () {
            $('#popupDialog').dialog({
                modal: false, //true,
                resizable: true, //false,
                draggable: true, //false,
                width: 'auto',
                title: 'Markup id: ' + id
            }).click(function () {
                $("#popupDialog").dialog('close');
            });
        });
    };

    init();
};
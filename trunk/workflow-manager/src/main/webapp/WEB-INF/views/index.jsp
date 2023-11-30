<!--
    NOTICE

    This software (or technical data) was produced for the U.S. Government
    under contract, and is subject to the Rights in Data-General Clause
    52.227-14, Alt. IV (DEC 2007).

    Copyright 2023 The MITRE Corporation. All Rights Reserved.
-->

<!--
    Copyright 2023 The MITRE Corporation

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

<!DOCTYPE html>
<html lang="en" ng-app="mpf.wfm">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <meta id="_csrf" name="_csrf" content="${_csrf.token}"/>
    <meta id="_csrf_header" name="_csrf_header" content="${_csrf.headerName}"/>
    <meta id="_csrf_parameterName" name="_csrf_parameterName" content="${_csrf.parameterName}"/>

    <title>Workflow Manager Web App</title>
    <link rel="icon" href="resources/img/favicon.ico"/>


    <!-- bootstrap treeview -->
    <link href="resources/css/bootstrap-treeview_dist.min.css" rel="stylesheet"/>

    <!-- dropzone -->
    <link href="resources/css/dropzone.css" rel="stylesheet"/>

    <!-- Admin UI -->
    <link href="resources/ui-plugins/datatables-plugins/integration/bootstrap/3/dataTables.bootstrap.css" rel="stylesheet"/>
    <link href="resources/ui-plugins/datatables-plugins/datatables-responsive/css/responsive.bootstrap-2-1-1.min.css" rel="stylesheet"/>
    <link href="resources/ui-plugins/datatables-plugins/select/select.dataTables.min.css" rel="stylesheet"/>
    <link href="resources/ui-plugins/datatables-plugins/buttons/buttons.dataTables.min.css" rel="stylesheet"/>
    <link href="resources/ui-plugins/font-awesome/css/font-awesome.min.css" rel="stylesheet"/>

    <!-- Bootstrap core CSS - last! -->
    <link href="resources/css/bootstrap.min.css" rel="stylesheet"/>
    <link href="resources/css/bootstrap-theme.min.css" rel="stylesheet"/>

    <!-- angular ui-select -->
    <link href="resources/css/select.min.css" rel="stylesheet"/>
    <!-- selectize -->
    <link href="resources/css/selectize.default.css" rel="stylesheet"/>
    <!-- local custom css -->
    <link href="resources/css/styles.css" rel="stylesheet"/>
    <link href="resources/js/pipelines2/pipelines2.css" rel="stylesheet"/>

</head>
<body>

<mpf-navbar></mpf-navbar>

 <!-- IMPORTANT: do not nest container/container-fluid -->
<div class="container-fluid">

   <div class="text-center">
        <system-notices id="systemNotices"></system-notices>
   </div>
    <!--angular view-->
    <!-- IMPORTANT: ui-router uses ui-view instead of ng-view -->
    <!-- ui-sref is also used with ui-router rather than href -->
     <ui-view></ui-view>

    <div id="error" ></div>

</div>

<!-- Bootstrap core JavaScript
================================================== -->
<!-- Placed at the end of the document so the pages load faster -->
<script src="resources/js/lib/jquery-1.11.0.min.js"></script>
<!-- left to quickly jump from min if having issues -->
<!--<script src="resources/js/jquery-1.11.1.js"></script>-->

<script src="resources/js/lib/underscore-min.js"></script>


<!-- bootstrap LOAD LAST and before jquery ui -->
<script src="resources/js/lib/bootstrap.min.js"></script>
<!-- load after bootsrap js -->

<!-- bootstrap treeview -->
<!-- needed to get the non minified version and modify the source to fix issues like the
tree rendering for each click... this would nearly lock the browser up with ~100 sub nodes-->
<script src="resources/js/lib/bootstrap-treeview_custom_non_minified.js"></script>

<!-- data tables -->
<script src='resources/ui-plugins/datatables/media/js/jquery.dataTables.min.js'></script>
<script src='resources/ui-plugins/datatables-plugins/integration/bootstrap/3/dataTables.bootstrap.min.js'></script>
<script src='resources/ui-plugins/datatables-plugins/select/dataTables.select.min.js'></script>
<script src='resources/ui-plugins/datatables-plugins/buttons/dataTables.buttons.min.js'></script>
<script src='resources/ui-plugins/datatables-plugins/dataTables.searchHighlight.min.js'></script>
<script src='resources/ui-plugins/datatables-plugins/datatables-responsive/js/dataTables.responsive.min.js'></script>
<script src='resources/ui-plugins/datatables-plugins/jquery.highlight.js'></script>
<script src='resources/ui-plugins/moment/moment.js'></script>

<!-- flotcharts -->
<script src="resources/ui-plugins/flot/jquery.flot.js"></script>
<script src="resources/ui-plugins/flot/jquery.flot.categories.min.js"></script>

<!-- dropzone -->
<script src="resources/ui-plugins/dropzone/dropzone.min.js"></script>

<!-- noty js -->
<script src="resources/js/lib/jquery.noty.packaged.mod_2_3_8.min.js"></script>

<!-- for atmosphere support -->
<script src="resources/js/lib/jquery.atmosphere.js"></script>

<script src="resources/ui-plugins/bootstrap3-dialog-master/dist/js/bootstrap-dialog.js"></script>

<!-- new angular additions -->
<script src="resources/js/lib/angular/angular.js"></script>
<script src="resources/js/lib/angular/angular-animate.js"></script>
<script src="resources/js/lib/angular/angular-messages.js"></script>
<script src="resources/js/lib/angular/angular-sanitize.js"></script>
<script src="resources/js/lib/angular/angular-resource.js"></script>
<script src="resources/js/lib/angular/angular-ui-router.min.js"></script>
<script src="resources/js/lib/angular/ui-bootstrap-tpls-1.1.1.min.js"></script>

<!-- angular ui-select -->
<script src="resources/js/lib/select.min.js"></script>
<!--<script src="resources/js/lib/select.js"></script>-->

<!-- angular confirm dialog -->
<script src="resources/js/lib/angular-confirm.js"></script>

<!-- angular app -->
<script src="resources/js/app.js"></script>

<script src="resources/js/lib/dropzone_custom.js"></script>

<!-- angular modules -->
<script src="resources/js/filters.js"></script>
<script src="resources/js/directives.js"></script>
<script src="resources/js/services.js"></script>
<!-- angular controllers -->
<script src="resources/js/controllers/AboutCtrl.js"></script>
<script src="resources/js/controllers/JobsCtrl.js"></script>
<script src="resources/js/controllers/MarkupCtrl.js"></script>
<script src="resources/js/controllers/ServerMediaCtrl.js"></script>
<!-- admin -->
<script src="resources/js/controllers/admin/AdminComponentRegistrationCtrl.js"></script>
<script src="resources/js/controllers/admin/AdminPropertySettingsCtrl.js"></script>
<script src="resources/js/controllers/admin/AdminStatsCtrl.js"></script>
<script src="resources/js/controllers/admin/AdminNodesCtrl.js"></script>
<script src="resources/js/controllers/admin/AdminLogsCtrl.js"></script>

<!-- ----- Components ----- -->
	<!-- Pipelines (original version before Pipelines2 -->
	<script src="resources/js/controllers/PipelinesCtrl.js"></script>  <!-- todo: remove when pipelines2 is done -->
<!--Pipelines2-->
	<script src="resources/js/pipelines2/AlgorithmService.js"></script>
	<script src="resources/js/pipelines2/ActionService.js"></script>
	<script src="resources/js/pipelines2/TaskService.js"></script>
    <script src="resources/js/pipelines2/pipeline-directives.js"></script>
    <script src="resources/js/pipelines2/Pipelines2Ctrl.js"></script>
    <script src="resources/js/pipelines2/actionProperties.js"></script>
</body>
</html>

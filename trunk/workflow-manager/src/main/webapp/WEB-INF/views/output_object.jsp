<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>

<%--
    NOTICE

    This software (or technical data) was produced for the U.S. Government
    under contract, and is subject to the Rights in Data-General Clause
    52.227-14, Alt. IV (DEC 2007).

    Copyright 2016 The MITRE Corporation. All Rights Reserved.
--%>

<%--
    Copyright 2016 The MITRE Corporation

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
--%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Output Object View</title>
</head>
<body style="white-space: pre; font-family: monospace;">
<script>
    (function() {
        var obj = ${jsonObj};
        document.body.innerHTML = "";
        document.body.appendChild(document.createTextNode(JSON.stringify(obj, null, 3)));
    })();
</script>
</body>
</html>
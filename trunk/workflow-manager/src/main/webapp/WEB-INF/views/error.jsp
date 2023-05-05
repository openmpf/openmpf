<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form"%>
<%@ page isErrorPage="true"%>

<%--
    NOTICE

    This software (or technical data) was produced for the U.S. Government
    under contract, and is subject to the Rights in Data-General Clause
    52.227-14, Alt. IV (DEC 2007).

    Copyright 2023 The MITRE Corporation. All Rights Reserved.
--%>

<%--
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
--%>

<html lang="en">
<head>
	<script>
	</script>
</head>
<!-- must not have an html body - will cause issues with the home view -->
<div id="errorResponseDiv">		
    <strong>Warning!</strong> There was a problem processing your request.  More information is available in your web server's log
    <c:if test="${exceptionMessage != null}">
    	<p class="error-message">${exceptionMessage}</p>
    </c:if>
    <c:if test="${exceptionCauseMessage != null}">
        <p class="error-message">${exceptionCauseMessage}</p>
    </c:if>
</div>
</html>
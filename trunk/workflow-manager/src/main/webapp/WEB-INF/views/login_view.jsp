<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib uri="http://www.springframework.org/tags/form" prefix="form"%>
<link href="<c:url value="resources/css/login.css" />" rel="stylesheet"></link>

<%--
    NOTICE

    This software (or technical data) was produced for the U.S. Government
    under contract, and is subject to the Rights in Data-General Clause
    52.227-14, Alt. IV (DEC 2007).

    Copyright 2019 The MITRE Corporation. All Rights Reserved.
--%>

<%--
    Copyright 2019 The MITRE Corporation

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
    <title>Login Page</title>
</head>

<body onload='document.loginForm.username.focus();'>

<div id="uibox">

    <p class="loginViewHeaderSmall">MEDIA PROCESSING</p>
	<p><span class="loginViewHeaderLarge">FRAMEWORK </span> <span class="version">${version}</span></p>

	<div id="login-box">

		<div style="display:none;" id="browserCheckMsg"></div>
		<c:if test="${not empty error}">
			<div class="error">${error}</div>
		</c:if>
		<c:if test="${not empty msg}">
			<div class="msg">${msg}</div>
		</c:if>

		<form name='loginForm' action="<c:url value='j_spring_security_check' />" method='POST'>
			<table>
				<tr>
					<td><input id='username' type='text' placeholder='Username' name='username' value=''></td>
				</tr>
				<tr>
					<td><input id='password' type='password' placeholder='Password' name='password' /></td>
				</tr>
				<tr>
					<td colspan='2'><input id='submit' name="submit" type="submit" value="Sign in" /></td>
				</tr>
			</table>
		</form>
	</div>
</div>

<script src="resources/js/jquery-1.11.0.min.js"></script>
<script src="resources/js/bowser.js"></script>
<script>
$(function() {
	if ( !( bowser.chrome || bowser.firefox ) ) {
		var warningStr = "<p>This application is optimized for FireFox and Chrome.</p>"
			+ "<p>You are using " + bowser.name + " " + bowser.version + ".<br/>"
			+ "Certain features may not work correctly.</p>";
		$('#browserCheckMsg')
				.addClass("error")
				.css('display','block')
				.html(warningStr);
	}
});
</script>


</body>
</html>

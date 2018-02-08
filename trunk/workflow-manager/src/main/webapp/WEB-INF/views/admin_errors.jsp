<%--
    NOTICE

    This software (or technical data) was produced for the U.S. Government
    under contract, and is subject to the Rights in Data-General Clause
    52.227-14, Alt. IV (DEC 2007).

    Copyright 2018 The MITRE Corporation. All Rights Reserved.
--%>

<%--
    Copyright 2018 The MITRE Corporation

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
            <div class="row">
                <div class="col-lg-12">
                    <div class="under-construction">Under Construction - Mocked Data</div>
                    <h1 class="page-header">Errors</h1>
                </div>
                <!-- /.col-lg-12 -->
            </div>
            
            <div class="row">
                <div class="col-lg-12">
                    <div class="panel panel-default">
                        <div class="panel-body">
                            
                            <div class="dataTable_wrapper">
                                <table class="table table-striped table-bordered table-hover" id="error-table">
                                    <thead>
                                        <tr>
                                            <th id="column-header-component">Component</th>
                                            <th id="column-header-timestamp">Timestamp</th>
                                            <th id="column-header-message">Message</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr class="odd danger">
                                            <td>catalina.out</td>
                                            <td>5/22/2014<br/>23:52:01</td>
                                            <td><textarea> o.a.camel.spring.SpringCamelContext Route: FACE_EXTRACTION_EXTRACTION_FACE_RANDOMERROR-RandomErrorRequestRoute started and consuming from: Endpoint[jms://EXTRACTION_FACE_RANDOMERROR_REQUEST]
                                              
java.lang.NullPointerException
abc.investxa.presentation.controllers.UnixServerJobController.handleRequest(UnixServerJobController.java:66)
org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter.handle(SimpleControllerHandlerAdapter.java:48)
org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:875)
org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:807)
org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:571)
org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:501)
javax.servlet.http.HttpServlet.service(HttpServlet.java:690)
javax.servlet.http.HttpServlet.service(HttpServlet.java:803)
org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:96)
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:76)

java.lang.NullPointerException
abc.investxa.presentation.controllers.UnixServerJobController.handleRequest(UnixServerJobController.java:66)
org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter.handle(SimpleControllerHandlerAdapter.java:48)
org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:875)
org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:807)
org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:571)
org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:501)
javax.servlet.http.HttpServlet.service(HttpServlet.java:690)
javax.servlet.http.HttpServlet.service(HttpServlet.java:803)
org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:96)
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:76)

java.lang.NullPointerException
abc.investxa.presentation.controllers.UnixServerJobController.handleRequest(UnixServerJobController.java:66)
org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter.handle(SimpleControllerHandlerAdapter.java:48)
org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:875)
org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:807)
org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:571)
org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:501)
javax.servlet.http.HttpServlet.service(HttpServlet.java:690)
javax.servlet.http.HttpServlet.service(HttpServlet.java:803)
org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:96)
org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:76)
                                              </textarea>
<!--
                                              <br/>
                                              <button type="button" id="clearError_ABCXYZ" class="btn btn-default btn-sm ">Clear This Error</button>
-->
                                            </td>
                                        </tr>
                                        <tr class="odd danger">
                                            <td>catalina.out</td>
                                            <td>5/22/2014<br/>23:52:02</td>
                                            <td><textarea> o.a.camel.spring.SpringCamelContext Route: FACE_EXTRACTION_EXTRACTION_FACE_RANDOMERROR-RandomErrorRequestRoute started and consuming from: Endpoint[jms://EXTRACTION_FACE_RANDOMERROR_REQUEST]
                                              </textarea>
<!--
                                              <br/>
                                              <button type="button" id="clearError_ABCXYZ" class="btn btn-default btn-sm ">Clear This Error</button>
-->
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                            <!-- /.table-responsive -->
                            
                        </div>
                        <!-- /.panel-body -->
                    </div>
                    <!-- /.panel -->
                </div>
                <!-- /.col-lg-12 -->
            </div>
            <!-- /.row -->

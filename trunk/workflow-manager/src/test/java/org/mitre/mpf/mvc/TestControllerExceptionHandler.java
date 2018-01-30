/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.mvc;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class TestControllerExceptionHandler {

    private ControllerUncaughtExceptionHandler _handler;

    @Mock
    private HttpServletRequest _mockRequest;

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private IllegalStateException _testException = new IllegalStateException("Something really bad happened");


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        _handler = new ControllerUncaughtExceptionHandler();

        when(_mockRequest.getHeaders("Accept"))
                .thenReturn(Collections.emptyEnumeration());
    }



    @Test
    public void returnsHtmlWhenNoAcceptHeader() {
        assertReceivedHtml();
    }

    @Test
    public void returnsHtmlWhenWrongRequestedWithHeader() {
        setRequestedWithHeader("foo");
        assertReceivedHtml();
    }


    @Test
    public void returnsModelWhenAjaxHeaderIsSet() {
        setRequestedWithHeader("XMLHttpRequest");
        assertReceivedModel();
    }


    @Test
    public void returnsHtmlWhenHtmlHasHigherPriority() {
        setAcceptHeader("application/foo,text/html,application/json");
        assertReceivedHtml();
    }

    @Test
    public void returnsModelJsonHasHigherPriority() {
        setAcceptHeader("application/foo,application/json,text/html");
        assertReceivedModel();
    }

    @Test
    public void returnsHtmlWhenInvalidAcceptHeader() {
        setAcceptHeader("asdf");
        assertReceivedHtml();
    }


    private void assertReceivedHtml() {
        Object response = _handler.handle(_mockRequest, _testException);
        assertTrue(response instanceof ModelAndView);
        ModelAndView mav = (ModelAndView) response;
        assertEquals("error", mav.getViewName());
    }


    private void assertReceivedModel() {
        Object response = _handler.handle(_mockRequest, _testException);
        assertTrue(response instanceof ResponseEntity);
        ResponseEntity<?> respEntity = (ResponseEntity<?>)  response;
        assertTrue(respEntity.getBody() instanceof ControllerUncaughtExceptionHandler.ErrorModel);
        ControllerUncaughtExceptionHandler.ErrorModel respModel =
                (ControllerUncaughtExceptionHandler.ErrorModel) respEntity.getBody();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respEntity.getStatusCode());
        assertTrue(respModel.isUncaughtError());
        assertNotEquals("Showing the exception message in the response is a security issue. If you need return a message to a user catch the exception closer to the issue. ",
                _testException.getMessage(), respModel.getMessage());
    }

    private void setAcceptHeader(String headerValue) {
        when(_mockRequest.getHeaders("Accept"))
                .thenReturn(Collections.enumeration(Collections.singletonList(headerValue)));
    }

    private void setRequestedWithHeader(String headerValue) {
        when(_mockRequest.getHeader("X-Requested-With"))
                .thenReturn(headerValue);
    }
}
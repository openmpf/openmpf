/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.MessageModel;
import org.mitre.mpf.test.MockitoTest;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;

public class TestControllerExceptionHandler extends MockitoTest.Strict {

    private ControllerUncaughtExceptionHandler _handler;

    @Mock
    private HttpServletRequest _mockRequest;

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private final IllegalStateException _testException = new IllegalStateException("This is a test exception");


    @Before
    public void init() {
        _handler = new ControllerUncaughtExceptionHandler();
    }


    @Test
    public void returnsHtmlWhenNoAcceptHeader() {
        when(_mockRequest.getHeaders("Accept"))
                .thenReturn(Collections.emptyEnumeration());

        assertReceivedHtml();
    }

    @Test
    public void returnsHtmlWhenWrongRequestedWithHeader() {
        when(_mockRequest.getHeaders("Accept"))
                .thenReturn(Collections.emptyEnumeration());

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
        assertEquals(_testException.getMessage(), mav.getModel().get("exceptionMessage"));
    }


    private void assertReceivedModel() {
        Object response = _handler.handle(_mockRequest, _testException);
        assertTrue(response instanceof ResponseEntity);
        ResponseEntity<?> respEntity = (ResponseEntity<?>)  response;
        assertTrue(respEntity.getBody() instanceof MessageModel);
        MessageModel respModel = (MessageModel) respEntity.getBody();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respEntity.getStatusCode());
        assertEquals(_testException.getMessage(), respModel.getMessage());
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

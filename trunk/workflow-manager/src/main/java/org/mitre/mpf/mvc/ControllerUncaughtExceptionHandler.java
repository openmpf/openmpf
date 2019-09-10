/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ControllerAdvice
public class ControllerUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ControllerUncaughtExceptionHandler.class);


    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handle(HttpServletRequest request, Exception exception){
        log.error(String.format("Request for %s raised an uncaught exception", request.getRequestURL()), exception);
        var errorMessage = StringUtils.isBlank(exception.getMessage())
                ? "An unknown error has occurred. Check the Workflow Manager log for details."
                : exception.getMessage();

        boolean isExpectingHtml = !hasAjaxHeader(request) && !jsonIsFirstMatchingMimeType(request);
        return isExpectingHtml
                ? new ModelAndView("error", "exceptionMessage", errorMessage)
                : createErrorModel(errorMessage, getStatus(exception));
    }

    private static HttpStatus getStatus(Exception exception) {
        if (exception instanceof HttpMessageNotReadableException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static boolean hasAjaxHeader(HttpServletRequest request) {
        String requestedWithHeaderVal = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWithHeaderVal);
    }


    private static boolean jsonIsFirstMatchingMimeType(HttpServletRequest request) {
        try {
            return getFirstMatchingMimeType(request)
                    .map(MimeTypeUtils.APPLICATION_JSON::includes)
                    .orElse(false);
        }
        catch (InvalidMimeTypeException ex) {
            log.error("Received an invalid MIME type in the accept header. Using HTML error page", ex);
            return false;
        }
    }

    private static Optional<MimeType> getFirstMatchingMimeType(HttpServletRequest request) {
        List<String> acceptHeaderVals = Collections.list(request.getHeaders("Accept"));
        return acceptHeaderVals.stream()
                .flatMap(s -> MimeTypeUtils.parseMimeTypes(s).stream())
                .filter(mt -> MimeTypeUtils.APPLICATION_JSON.includes(mt) || MimeTypeUtils.TEXT_HTML.includes(mt))
                .findFirst();
    }


    private static ResponseEntity<ErrorModel> createErrorModel(String errorMessage, HttpStatus status) {
        return new ResponseEntity<>(new ErrorModel(errorMessage), status);
    }



    public static class ErrorModel {
        private final String _message;

        public ErrorModel(String message) {
            _message = message;
        }

        public boolean isUncaughtError() {
            return true;
        }

        public String getMessage() {
            return _message;
        }
    }
}


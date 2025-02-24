/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.service;

import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.function.Function;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.mitre.mpf.wfm.WfmProcessingException;
import org.springframework.stereotype.Service;

@Service
public class ConstraintValidationService {

    private final Validator _validator;

    @Inject
    public ConstraintValidationService(Validator validator) {
        _validator = validator;
    }


    public <T> void validate(T object) {
        validate(object, WfmProcessingException::new);
    }

    public <T> void validate(T object, String itemName) {
        validate(object, itemName, WfmProcessingException::new);
    }

    public <T, E extends Exception> void validate(
                T object, String itemName,
                Function<String, E> exceptionCreator) throws E {
        var prefix = itemName + " has errors in the following fields:\n";
        validateInternal(object, prefix, exceptionCreator);
    }


    public <T, E extends Exception> void validate(
                T object,
                Function<String, E> exceptionCreator) throws E {
        var prefix = "There were errors in the following fields:\n";
        validateInternal(object, prefix, exceptionCreator);
    }


    private <T, E extends Exception> void validateInternal(
                T object,
                String prefix,
                Function<String, E> exceptionCreator) throws E {
        var violations = _validator.validate(object);
        if (!violations.isEmpty()) {
            throw exceptionCreator.apply(createFieldErrorMessages(prefix, violations));
        }
    }


    public static String createFieldErrorMessages(
            String prefix, Collection<? extends ConstraintViolation<?>> violations) {
        return violations
                .stream()
                .map(ConstraintValidationService::createViolationMessage)
                .sorted()
                .collect(joining("\n", prefix, ""));
    }

    private static String createViolationMessage(ConstraintViolation<?> violation) {
        var violationPath = violation.getPropertyPath().toString();
        if (violationPath.isEmpty()) {
            return "<root>: " + violation.getMessage();
        }
        return "%s=\"%s\": %s".formatted(
                violationPath, violation.getInvalidValue(), violation.getMessage());
    }
}

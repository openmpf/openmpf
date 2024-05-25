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

package org.mitre.mpf.wfm.service.component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;

public class InvalidExtrasDescriptorException extends ComponentRegistrationException {

    private final Set<ConstraintViolation<JsonExtrasDescriptor>> _validationErrors;

    public InvalidExtrasDescriptorException(Set<ConstraintViolation<JsonExtrasDescriptor>> validationErrors) {
        super(createMessage(validationErrors));
        _validationErrors = ImmutableSet.copyOf(validationErrors);
    }

    private static String createMessage(Set<ConstraintViolation<JsonExtrasDescriptor>> validationErrors) {
        return validationErrors.stream()
                .map(cv -> cv.getPropertyPath() + " " + cv.getMessage())
                .sorted()
                .collect(joining("\n", "The following fields have errors:\n", ""));
    }

    public Set<ConstraintViolation<JsonExtrasDescriptor>> getValidationErrors() {
        return _validationErrors;
    }
}

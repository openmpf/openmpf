/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

public class InvalidComponentDescriptorException extends ComponentRegistrationException {

    private final Set<ConstraintViolation<JsonComponentDescriptor>> _validationErrors;

    private final List<String> _customMessages;

    public InvalidComponentDescriptorException(Set<ConstraintViolation<JsonComponentDescriptor>> validationErrors, List<String> messages) {
        super(createMessage(validationErrors, messages));
        _validationErrors = ImmutableSet.copyOf(validationErrors);
        _customMessages = ImmutableList.copyOf(messages);
    }

    private static String createMessage(Set<ConstraintViolation<JsonComponentDescriptor>> validationErrors, List<String> customMessages) {
        StringBuffer buffer = new StringBuffer();

        if (!validationErrors.isEmpty()) {
            buffer.append(validationErrors.stream()
                    .map(cv -> cv.getPropertyPath() + " " + cv.getMessage())
                    .sorted()
                    .collect(joining("\n", "The following fields have errors:\n", "\n")));
        }

        if (!customMessages.isEmpty()) {
            buffer.append(customMessages.stream()
                    .collect(joining("\n")));
        }

        return buffer.toString().trim();
    }

    public Set<ConstraintViolation<JsonComponentDescriptor>> getValidationErrors() {
        return _validationErrors;
    }

    public List<String> getCustomMessages() {
        return _customMessages;
    }
}

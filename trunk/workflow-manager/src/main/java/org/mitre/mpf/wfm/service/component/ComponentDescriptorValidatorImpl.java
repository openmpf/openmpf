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

import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ComponentDescriptorValidatorImpl implements ComponentDescriptorValidator {

    private final Validator validator;

    @Inject
    public ComponentDescriptorValidatorImpl(Validator validator) {
        this.validator = validator;
    }

    @Override
    public void validate(JsonComponentDescriptor descriptor) throws InvalidComponentDescriptorException {
        Set<ConstraintViolation<JsonComponentDescriptor>> validationErrors = validator.validate(descriptor);
        List<String> customMessages = new ArrayList<>();

        try {
            // ensure that defaultValue or propertiesKey is set, not both
            for (JsonComponentDescriptor.AlgoProvidesProp algoProvidesProp : descriptor.algorithm.providesCollection.properties) {
                boolean isDefaultValueSet = (algoProvidesProp.defaultValue != null);
                boolean isPropertiesKeySet = (algoProvidesProp.propertiesKey != null);

                if (!isDefaultValueSet && !isPropertiesKeySet) {
                    customMessages.add("The following algorithm property must provide a defaultValue or propertiesKey: " + algoProvidesProp.name + ".");
                } else if (isDefaultValueSet && isPropertiesKeySet) {
                    customMessages.add("The following algorithm property must provide a defaultValue or propertiesKey, not both: " + algoProvidesProp.name + ".");
                }
            }
        } catch (NullPointerException e) {
            // this error should already be captured in validationErrors
        }

        if (!validationErrors.isEmpty() || !customMessages.isEmpty()) {
            throw new InvalidComponentDescriptorException(validationErrors, customMessages);
        }
    }
}

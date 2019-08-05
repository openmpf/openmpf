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


package org.mitre.mpf.wfm.pipeline;

import javax.validation.ConstraintViolation;
import java.util.Collection;

import static java.util.stream.Collectors.joining;

public class PipelineValidationException extends InvalidPipelineException {

    public PipelineValidationException(PipelineComponent invalidPipelineComponent,
                                       Collection<ConstraintViolation<PipelineComponent>> validationErrors) {
        super(createMessage(invalidPipelineComponent, validationErrors));
    }


    private static String createMessage(PipelineComponent invalidPipelineComponent,
                                        Collection<ConstraintViolation<PipelineComponent>> validationErrors) {
        String prefix = invalidPipelineComponent.getName() + " has errors in the following fields:\n";
        return validationErrors.stream()
                .map(PipelineValidationException::createFieldMessage)
                .sorted()
                .collect(joining("\n", prefix, ""));
    }

    private static String createFieldMessage(ConstraintViolation<PipelineComponent> violation) {
        return String.format("%s=\"%s\": %s", violation.getPropertyPath(), violation.getInvalidValue(),
                             violation.getMessage());
    }

}

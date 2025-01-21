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

import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionProperty;
import org.mitre.mpf.test.TestUtil;
import org.mitre.mpf.wfm.service.ConstraintValidationService;

import javax.validation.Validator;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDescriptorValidator {

    private ConstraintValidationService _validator = TestUtil.createConstraintValidator();

    @Test
    public void doesNotThrowExceptionWhenDescriptorHasNoViolations() throws InvalidComponentDescriptorException {
        var mockSpringValidator = mock(Validator.class);
        when(mockSpringValidator.validate(notNull()))
                .thenReturn(Collections.emptySet());

        var descriptorValidator = new ConstraintValidationService(mockSpringValidator);
        var descriptor = new JsonComponentDescriptor(
                null, null, null, null, null, null, null, null,
                List.of(), null, List.of(), List.of(), List.of());
        descriptorValidator.validate(descriptor, InvalidComponentDescriptorException::new);
    }


    @Test
    public void canHandleValidDescriptor() throws InvalidComponentDescriptorException {
        _validator.validate(
                TestDescriptorFactory.getWithCustomPipeline(),
                InvalidComponentDescriptorException::new);
    }


    @Test
    public void canValidateDescriptorMissingEverything() {
        var descriptor = new JsonComponentDescriptor(
                "", null, null, null,
                null, null, null, "",
                null, null, null, null, null);

        assertValidationErrors(
                descriptor,
                isBlank("componentName"),
                isNull("componentVersion"),
                isNull("middlewareVersion"),
                "sourceLanguage=\"null\": must be java, c++, or python",
                "<root>: must contain batchLibrary, streamLibrary, or both",
                isNull("algorithm"));
    }


    @Test
    public void canValidateEnvironmentVariables() {
        var envVars = List.of(
                new JsonComponentDescriptor.EnvironmentVariable(null, null, null),
                new JsonComponentDescriptor.EnvironmentVariable("name", "", "asdf"),
                new JsonComponentDescriptor.EnvironmentVariable("name", "asdf", ":"),
                new JsonComponentDescriptor.EnvironmentVariable("name", "", null));

        var descriptor = new JsonComponentDescriptor(
                "componentName", "componentVersion", "1.0",
                null, null, ComponentLanguage.CPP, "lib.so", null,
                envVars, null, List.of(), List.of(), List.of());

        assertValidationErrors(
                descriptor,
                isNull("algorithm"),
                isNullForNotEmpty("environmentVariables[0].name"),
                isNull("environmentVariables[0].value"),
                "environmentVariables[1].sep=\"asdf\": must be \":\" or null");
    }


    @Test
    public void canValidatePipelineElements() {
        // Just making sure pipeline validation errors are reported, more detailed validation tests for the pipelines
        // are in TestPipelineValidator.

        var validAction = new Action("valid action", "descr", "algo",
                                     List.of());
        var invalidAction = new Action("invalid action", "", "",
                                       List.of(new ActionProperty(null, null)));

        var descriptor = new JsonComponentDescriptor(
                "componentName", "componentVersion", "1.0",
                null, null, ComponentLanguage.CPP, "lib.so", null,
                List.of(), null, List.of(validAction, invalidAction), List.of(), List.of());

        assertValidationErrors(
                descriptor,
                isNull("algorithm"),
                isEmpty("actions[1].description"),
                isBlank("actions[1].algorithm"),
                isNullForNotEmpty("actions[1].properties[0].name"),
                isNull("actions[1].properties[0].value"));
    }



    private void assertValidationErrors(JsonComponentDescriptor descriptor, String... expectedMessages) {
        assertTrue("No messages passed in to check", expectedMessages.length > 0);
        try {
            _validator.validate(descriptor, InvalidComponentDescriptorException::new);
            fail("Expected exception");
        }
        catch (InvalidComponentDescriptorException ex) {
            String errorMessage = ex.getMessage();
            for (var expectedMessage : expectedMessages) {
                assertThat(errorMessage, containsString(expectedMessage));
            }


            var lineCount = errorMessage
                    .chars()
                    .filter(ch -> ch == '\n')
                    .count();
            assertEquals("Did not contain the expected number of error messages.",
                         lineCount, expectedMessages.length);
        }
    }


    private static String isEmpty(String field) {
        return "%s=\"\": may not be empty".formatted(field);
    }

    private static String isBlank(String field) {
        return "%s=\"\": may not be blank".formatted(field);
    }

    private static String isNull(String field) {
        return "%s=\"null\": may not be null".formatted(field);
    }

    private static String isNullForNotEmpty(String field) {
        return "%s=\"null\": may not be empty".formatted(field);
    }
}

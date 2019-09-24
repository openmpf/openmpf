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

package org.mitre.mpf.wfm.service.component;

import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.rest.api.pipelines.Action;
import org.mitre.mpf.rest.api.pipelines.ActionProperty;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.validation.Validator;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDescriptorValidator {

    private ComponentDescriptorValidator _validator;

    @Before
    public void init() {
        var springValidator = new LocalValidatorFactoryBean();
        springValidator.afterPropertiesSet();
        _validator = new ComponentDescriptorValidatorImpl(springValidator);
    }


    @Test
    public void doesNotThrowExceptionWhenDescriptorHasNoViolations() throws InvalidComponentDescriptorException {
        var mockSpringValidator = mock(Validator.class);
        when(mockSpringValidator.validate(notNull()))
                .thenReturn(Collections.emptySet());

        ComponentDescriptorValidator descriptorValidator = new ComponentDescriptorValidatorImpl(mockSpringValidator);
        descriptorValidator.validate(mock(JsonComponentDescriptor.class));
    }


    @Test
    public void canHandleValidDescriptor() throws InvalidComponentDescriptorException {
        _validator.validate(TestDescriptorFactory.getWithCustomPipeline());
    }


    @Test
    public void canValidateDescriptorMissingEverything() {
        var descriptor = new JsonComponentDescriptor(
                "", null, null, null,
                null, null, null, "",
                null, null, null, null, null);

        assertValidationErrors(
                descriptor,
                isEmpty("componentName"),
                isNull("componentVersion"),
                isNull("middlewareVersion"),
                "sourceLanguage: must be java, c++, or python",
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
                isEmpty("environmentVariables[0].name"),
                isNull("environmentVariables[0].value"),
                "environmentVariables[1].sep: must be \":\" or null");
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
                isEmpty("actions[1].algorithm"),
                isEmpty("actions[1].properties[0].name"),
                isNull("actions[1].properties[0].value"));
    }



    private void assertValidationErrors(JsonComponentDescriptor descriptor, String... expectedMessages) {
        assertTrue("No messages passed in to check", expectedMessages.length > 0);
        try {
            _validator.validate(descriptor);
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
       return field + ": may not be empty";
    }

    private static String isNull(String field) {
        return field + ": may not be null" ;
    }
}

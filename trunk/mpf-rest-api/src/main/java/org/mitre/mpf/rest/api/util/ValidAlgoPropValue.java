/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.rest.api.util;


import org.mitre.mpf.rest.api.pipelines.AlgorithmProperty;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidAlgoPropValue.Validator.class)
public @interface ValidAlgoPropValue {

    String message() default "must provide either a defaultValue or propertiesKey, but not both.";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };



    public static class Validator
            implements ConstraintValidator<ValidAlgoPropValue, AlgorithmProperty> {

        @Override
        public void initialize(ValidAlgoPropValue constraintAnnotation) {
        }

        @Override
        public boolean isValid(AlgorithmProperty property, ConstraintValidatorContext ctx) {
            boolean bothProvided = property.getDefaultValue() != null && property.getPropertiesKey() != null;
            boolean neitherProvided = property.getDefaultValue() == null && property.getPropertiesKey() == null;
            if (bothProvided || neitherProvided) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("propertiesKey")
                        .addConstraintViolation();

                ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("defaultValue")
                        .addConstraintViolation();
                return false;
            }

            if (property.getPropertiesKey() != null && property.getPropertiesKey().trim().isEmpty()) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate("may not be empty")
                        .addPropertyNode("propertiesKey")
                        .addConstraintViolation();
                return false;
            }

            return true;
        }
    }
}

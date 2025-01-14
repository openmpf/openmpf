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

package org.mitre.mpf.rest.api.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiPredicate;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE,
        ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidName.Validator.class)
public @interface ValidName {
    String message() default "may not contain / or ;";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };

    boolean required() default true;


    public static class Validator implements ConstraintValidator<ValidName, String> {

        private BiPredicate<String, ConstraintValidatorContext> _isValidPred;

        @Override
        public void initialize(ValidName constraintAnnotation) {
            if (constraintAnnotation.required()) {
                _isValidPred = Validator::isValidRequired;
            }
            else {
                _isValidPred = Validator::isValidNotRequired;
            }
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return _isValidPred.test(value, ctx);
        }

        private static boolean isValidRequired(String value, ConstraintValidatorContext ctx) {
            if (value == null) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate("may not be null")
                    .addConstraintViolation();
                return false;
            }
            return isValidNotRequired(value, ctx);
        }

        private static boolean isValidNotRequired(String value, ConstraintValidatorContext ctx) {
            if (value == null) {
                return true;
            }
            if (value.isBlank()) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate("may not be blank")
                    .addConstraintViolation();
                return false;
            }
            return !value.contains("/") && !value.contains(";");
        }
    }
}

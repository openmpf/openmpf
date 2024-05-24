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

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MethodReturnsTrue.Validator.class)
public @interface MethodReturnsTrue {
    String method();
    String message() default "";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };


    class Validator implements ConstraintValidator<MethodReturnsTrue, Object> {

        private String _methodName;

        private String _message;

        private Method _validationTargetMethod;

        @Override
        public void initialize(MethodReturnsTrue constraintAnnotation) {
            _methodName = constraintAnnotation.method();
            if (constraintAnnotation.message().isEmpty()) {
                _message = String.format("Expected %s to return true.", _methodName);
            }
            else {
                _message = constraintAnnotation.message();
            }
        }


        @Override
        public boolean isValid(Object validationTarget, ConstraintValidatorContext ctx) {
            if (invokeTargetMethod(validationTarget)) {
                return true;
            }
            else {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate(_message)
                        .addConstraintViolation();
                return false;
            }
        }

        private boolean invokeTargetMethod(Object validationTarget) {
            setTargetMethod(validationTarget);
            try {
                var targetReturnValue = _validationTargetMethod.invoke(validationTarget);
                return castValidationReturnValue(targetReturnValue, validationTarget);
            }
            catch (InvocationTargetException e) {
                throw new IllegalStateException(
                        String.format("The %s method threw an exception",
                                      fqMethodName(validationTarget)),
                        e.getTargetException());
            }
            catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private void setTargetMethod(Object validationTarget) {
            if (_validationTargetMethod != null) {
                return;
            }

            try {
                _validationTargetMethod = validationTarget.getClass().getMethod(_methodName);
            }
            catch (NoSuchMethodException e) {
                throw new IllegalStateException(String.format(
                    "The %s class does not have a public method named %s with no arguments.",
                    validationTarget.getClass(), _methodName), e);
            }
        }


        private boolean castValidationReturnValue(Object returnValue, Object validationTarget) {
            try {
                return (boolean) returnValue;
            }
            catch (ClassCastException e) {
                throw new IllegalStateException(
                        String.format("The %s method did not return a boolean.",
                                      fqMethodName(validationTarget)),
                        e);
            }
        }

        private String fqMethodName(Object validationTarget) {
            return String.format("%s.%s",
                                 validationTarget.getClass().getName(),
                                 _validationTargetMethod.getName());
        }
    }
}

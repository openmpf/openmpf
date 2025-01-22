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

package org.mitre.mpf.wfm;

import java.lang.reflect.Type;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.hibernate.validator.spi.valuehandling.ValidatedValueUnwrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
public class ValidatorConfig {

    @Bean
    public ParameterMessageInterpolator parameterMessageInterpolator() {
        return new ParameterMessageInterpolator();
    }

    @Bean
    @Primary
    public LocalValidatorFactoryBean localValidatorFactoryBean() {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(parameterMessageInterpolator());
        validator.setProviderClass(HibernateValidator.class);
        validator.setConfigurationInitializer(
                c -> configureValidator((HibernateValidatorConfiguration) c));
        return validator;
    }

    private static void configureValidator(HibernateValidatorConfiguration config) {
        config.addValidatedValueHandler(new ValidatedValueUnwrapper<OptionalInt>() {
            public Object handleValidatedValue(OptionalInt value) {
                return value.isPresent() ? value.getAsInt() : null;
            }

            public Type getValidatedValueType(Type valueType) {
                return Integer.class;
            }
        });

        config.addValidatedValueHandler(new ValidatedValueUnwrapper<OptionalLong>() {
            public Object handleValidatedValue(OptionalLong value) {
                return value.isPresent() ? value.getAsLong() : null;
            }

            public Type getValidatedValueType(Type valueType) {
                return Long.class;
            }
        });

        config.addValidatedValueHandler(new ValidatedValueUnwrapper<OptionalDouble>() {
            public Object handleValidatedValue(OptionalDouble value) {
                return value.isPresent() ? value.getAsDouble() : null;
            }

            public Type getValidatedValueType(Type valueType) {
                return Double.class;
            }
        });
    }
}

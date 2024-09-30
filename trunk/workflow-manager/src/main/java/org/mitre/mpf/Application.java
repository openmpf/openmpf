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


package org.mitre.mpf;

import java.lang.reflect.Type;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.atmosphere.cpr.AtmosphereServlet;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.hibernate.validator.spi.valuehandling.ValidatedValueUnwrapper;
import org.javasimon.console.SimonConsoleServlet;
import org.mitre.mpf.mvc.security.OidcSecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;


@SpringBootApplication
@ImportResource({
    "classpath:/applicationContext-web.xml",
    "classpath:/applicationContext-nm.xml"
})
@Configuration
public class Application extends SpringBootServletInitializer {

    public static void main(String[] args) {
        var app = new SpringApplication(Application.class);
        if (OidcSecurityConfig.isEnabled()) {
            app.setAdditionalProfiles("oidc");
        }
        app.run(args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(Application.class);
    }


    @Bean
    public TomcatServletWebServerFactory tomcatFactory(
                @Value("${security.require-ssl:false}") boolean requireSsl,
                @Value("${server.port:8443}") int sslPort) {
        var tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                // Prevent Tomcat from scanning library JARs to find .jsp files.
                ((StandardJarScanner) context.getJarScanner()).setScanManifest(false);
                if (requireSsl) {
                    var securityConstraint = new SecurityConstraint();
                    securityConstraint.setUserConstraint("CONFIDENTIAL");
                    var collection = new SecurityCollection();
                    collection.addPattern("/*");
                    securityConstraint.addCollection(collection);
                    context.addConstraint(securityConstraint);
                }
            }
        };

        if (requireSsl) {
            var connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setScheme("http");
            connector.setPort(8080);
            connector.setSecure(false);
            connector.setRedirectPort(sslPort);
            tomcat.addAdditionalTomcatConnectors(connector);
        }

        return tomcat;
    }


    @Bean
    public ServletRegistrationBean<SimonConsoleServlet> simonConsoleServlet() {
        var servlet = new ServletRegistrationBean<>(
            new SimonConsoleServlet(), "/javasimon-console/*");
        servlet.addInitParameter("url-prefix", "/javasimon-console");
        return servlet;
    }

    @Bean
    @Profile("!jenkins")
    public ServletRegistrationBean<AtmosphereServlet> atmosphereServlet() {
        var servlet = new ServletRegistrationBean<>(
            new AtmosphereServlet(false, false), "/websocket/*");

        servlet.setName("AtmosphereServlet");
        servlet.addInitParameter("org.atmosphere.websocket.messageContentType",
                "application/json");

        servlet.addInitParameter("com.sun.jersey.config.property.packages",
                "org.mitre.mpf");

        servlet.addInitParameter("org.atmosphere.cpr.AtmosphereInterceptor",
                "org.atmosphere.interceptor.HeartbeatInterceptor");

        servlet.addInitParameter(
            "org.atmosphere.interceptor.HeartbeatInterceptor.heartbeatFrequencyInSeconds", "60");

        servlet.setLoadOnStartup(0);
        servlet.setAsyncSupported(true);
        return servlet;
    }

    @Bean
    public ParameterMessageInterpolator parameterMessageInterpolator() {
        return new ParameterMessageInterpolator();
    }

    @Bean
    @Primary
    public LocalValidatorFactoryBean localValidatorFactoryBean(
            ParameterMessageInterpolator messageInterpolator) {
        var validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(messageInterpolator);
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

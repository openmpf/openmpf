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

package org.mitre.mpf.mvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import org.mitre.mpf.rest.api.MessageModel;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

import com.fasterxml.classmate.TypeResolver;

import io.swagger.annotations.ApiOperation;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.builders.ResponseMessageBuilder;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.schema.AlternateTypeRule;
import springfox.documentation.schema.AlternateTypeRules;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.schema.WildcardType;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ResponseMessage;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;
import springfox.documentation.spring.web.readers.operation.HandlerMethodResolver;
import springfox.documentation.swagger.web.UiConfiguration;
import springfox.documentation.swagger.web.UiConfigurationBuilder;
import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration;

//reference - http://springfox.github.io/springfox/docs/snapshot/
@Configuration
@EnableOpenApi
@Import(BeanValidatorPluginsConfiguration.class)
// This class causes issues when running Maven tests, so we disable it when the jenkins profile is active.
@Profile("!jenkins")
public class SwaggerConfig {

    @Bean
    public Docket api(){
        var globalResponses = getGlobalResponses();

        return new Docket(DocumentationType.OAS_30)
            .select()
            // only show APIs which has the @ApiOperation annotation
            //  alternative is any(), but that defaults to somewhat useless autogen documentation
            .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class))
            .paths(PathSelectors.ant("/rest/**"))
            .build()
            // list classes to be ignored in parameters (useful for optional internal parameters)
            .ignoredParameterTypes(HttpSession.class)
            // opt out of auto-generated response code and their default message
            .useDefaultResponseMessages(false)
            .alternateTypeRules(createAlternateTypeRules())
            .genericModelSubstitutes(Optional.class)
            .directModelSubstitute(Instant.class, String.class)
            .directModelSubstitute(OptionalInt.class, Integer.class)
            .directModelSubstitute(OptionalLong.class, Long.class)
            .directModelSubstitute(OptionalDouble.class, Double.class)
            .globalResponseMessage(RequestMethod.GET, globalResponses)
            .globalResponseMessage(RequestMethod.DELETE, globalResponses)
            .globalResponseMessage(RequestMethod.POST, globalResponses)
            .globalResponseMessage(RequestMethod.PUT, globalResponses)
            .apiInfo(apiInfo());
    }

    private static ApiInfo apiInfo() {
        ApiInfo apiInfo = new ApiInfo(
            "Workflow Manager's REST API",  // title
            "REST-based web services for the Workflow Manager",  // description
            "",  // terms of service url
            "",  // contact email
            "",
            "",  // license name
            ""  // license url
        );
        return apiInfo;
    }


    private static AlternateTypeRule[] createAlternateTypeRules() {
        var resolver = new TypeResolver();
        var streamRule = AlternateTypeRules.newRule(
                resolver.resolve(Stream.class, WildcardType.class),
                resolver.resolve(List.class, WildcardType.class));

        var optionalInstantRule = AlternateTypeRules.newRule(
                resolver.resolve(Optional.class, Instant.class),
                resolver.resolve(String.class),
                // Set order to ensure this rule gets applied before
                // .genericModelSubstitutes(Optional.class)
                AlternateTypeRules.GENERIC_SUBSTITUTION_RULE_ORDER - 1);
        return new AlternateTypeRule[] { streamRule, optionalInstantRule };
    }


    @Bean
    UiConfiguration uiConfig() {
        return UiConfigurationBuilder.builder()
                // disable validation of resulting Swagger JSON at http://online.swagger.io/validator
                .validatorUrl(null)
                .build();
    }


    private static List<ResponseMessage> getGlobalResponses() {
        var unauthorized = new ResponseMessageBuilder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message("Unauthorized")
                .responseModel(new ModelRef(MessageModel.class.getSimpleName()))
                .build();

        return List.of(unauthorized);
    }


    // Without this method, a NullPointerException is thrown during start up when the Swagger URI
    // patterns are being configured.
    // Adapted from https://stackoverflow.com/a/71497144
    @Bean
    public WebMvcRequestHandlerProvider webMvcRequestHandlerProvider(
            Optional<ServletContext> context,
            HandlerMethodResolver methodResolver,
            List<RequestMappingInfoHandlerMapping> handlerMappings) {
        var filteredHandlerMappings = handlerMappings.stream()
                .filter(rh -> !(rh instanceof WebMvcEndpointHandlerMapping))
                .toList();
        return new WebMvcRequestHandlerProvider(context, methodResolver, filteredHandlerMappings);
    }
}

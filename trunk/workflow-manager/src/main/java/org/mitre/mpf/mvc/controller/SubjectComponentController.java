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
package org.mitre.mpf.mvc.controller;

import java.util.Comparator;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.mitre.mpf.rest.api.subject.SubjectComponentDetails;
import org.mitre.mpf.rest.api.subject.SubjectComponentSummary;
import org.mitre.mpf.wfm.data.access.SubjectComponentRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectComponentProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import software.amazon.awssdk.http.HttpStatusCode;

@Api("SubjectTrackingComponents")
@RestController
@RequestMapping(path = "/subject/components", produces = "application/json")
public class SubjectComponentController {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectComponentController.class);

    private final SubjectComponentRepo _subjectComponentRepo;

    @Inject
    SubjectComponentController(SubjectComponentRepo subjectComponentRepo) {
        _subjectComponentRepo = subjectComponentRepo;
    }


    @GetMapping
    @ExposedMapping
    @ApiOperation("Gets subject tracking components")
    public Stream<SubjectComponentSummary> getComponents() {
        return _subjectComponentRepo.findAll()
                .stream()
                .map(c -> new SubjectComponentSummary(
                        c.getComponentName(), c.getComponentVersion()))
                .sorted(Comparator.comparing(SubjectComponentSummary::name));
    }


    @GetMapping("{componentName}")
    @ExposedMapping
    @ApiOperation("Gets details of a subject tracking component")
    @ApiResponses({
        @ApiResponse(code = HttpStatusCode.NOT_FOUND, message = "Component does not exist."),
    })
    public ResponseEntity<SubjectComponentDetails> getComponent(@PathVariable String componentName) {
        var optDbComponent = _subjectComponentRepo.findById(componentName);
        if (optDbComponent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var dbComponent = optDbComponent.get();
        var props = dbComponent.getComponentProperties()
                .stream()
                .map(SubjectComponentController::convertProp)
                .sorted(Comparator.comparing(SubjectComponentDetails.Property::name))
                .toList();

        return ResponseEntity.ok(new SubjectComponentDetails(
                dbComponent.getComponentName(),
                dbComponent.getComponentVersion(),
                dbComponent.getComponentLibrary(),
                props));
    }


    @DeleteMapping("{componentName}")
    @ExposedMapping
    @ApiOperation("Un-registers a subject tracking component")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComponent(@PathVariable String componentName) {
        try {
            LOG.info("Removing subject component: {}", componentName);
            _subjectComponentRepo.deleteById(componentName);
        }
        catch (EmptyResultDataAccessException ignored) {
            // Delete should be idempotent, so if the component has already been deleted we should
            // still report success.
        }
    }


    private static SubjectComponentDetails.Property convertProp(DbSubjectComponentProperty dbProp) {
        return new SubjectComponentDetails.Property(
                dbProp.getName(),
                dbProp.getDescription(),
                dbProp.getDefaultValue(),
                dbProp.getPropertiesKey(),
                dbProp.getType());
    }
}

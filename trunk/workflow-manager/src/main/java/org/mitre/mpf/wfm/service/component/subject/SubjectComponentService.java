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

package org.mitre.mpf.wfm.service.component.subject;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;
import javax.transaction.Transactional;

import org.mitre.mpf.rest.api.pipelines.AlgorithmProperty;
import org.mitre.mpf.wfm.data.access.SubjectComponentRepo;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectComponent;
import org.mitre.mpf.wfm.data.entities.persistent.DbSubjectComponentProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class SubjectComponentService {
    private static final Logger LOG = LoggerFactory.getLogger(SubjectComponentService.class);

    private final SubjectComponentRepo _subjectComponentRepo;

    @Inject
    SubjectComponentService(SubjectComponentRepo subjectComponentRepo) {
        _subjectComponentRepo = subjectComponentRepo;
    }


    @Transactional
    public synchronized RegistrationResult registerComponent(SubjectComponentDescriptor descriptor) {
        var result = registerComponentInternal(descriptor);
        LOG.info(
                "Registration attempt for {} resulted in: {}",
                descriptor.componentName(), result.description);
        return result;
    }


    private RegistrationResult registerComponentInternal(SubjectComponentDescriptor descriptor) {
        var optExistingComponent = _subjectComponentRepo.findById(descriptor.componentName());
        if (optExistingComponent.isEmpty()) {
            registerNewComponent(descriptor);
            return RegistrationResult.NEW;
        }

        var existingComponent = optExistingComponent.get();
        if (areSame(descriptor, existingComponent)) {
            return RegistrationResult.SAME;
        }
        updateComponent(existingComponent, descriptor);
        return RegistrationResult.UPDATED;
    }


    private void registerNewComponent(SubjectComponentDescriptor descriptor) {
        var dbComponent = new DbSubjectComponent(descriptor.componentName());
        copyFields(dbComponent, descriptor);
        _subjectComponentRepo.save(dbComponent);
    }


    private void updateComponent(
            DbSubjectComponent dbComponent, SubjectComponentDescriptor descriptor) {
        dbComponent.getComponentProperties().clear();
        copyFields(dbComponent, descriptor);
        _subjectComponentRepo.save(dbComponent);
    }


    private void copyFields(DbSubjectComponent dbComponent, SubjectComponentDescriptor descriptor) {
        dbComponent.setComponentVersion(descriptor.componentVersion());
        dbComponent.setSourceLanguage(descriptor.sourceLanguage());
        dbComponent.setComponentLibrary(descriptor.componentLibrary());
        for (var descriptorProp : descriptor.properties()) {
            dbComponent.getComponentProperties().add(new DbSubjectComponentProperty(
                    dbComponent,
                    descriptorProp.name(),
                    descriptorProp.description(),
                    descriptorProp.defaultValue(),
                    descriptorProp.propertiesKey(),
                    descriptorProp.type()));
        }
    }


    private boolean areSame(
            SubjectComponentDescriptor descriptor, DbSubjectComponent subjectComponent) {
        return descriptor.componentName().equals(subjectComponent.getComponentName())
                && descriptor.componentVersion().equals(subjectComponent.getComponentVersion())
                && descriptor.sourceLanguage() == subjectComponent.getSourceLanguage()
                && descriptor.componentLibrary().equals(subjectComponent.getComponentLibrary())
                && haveSameProperties(descriptor, subjectComponent);
    }


    private boolean haveSameProperties(
            SubjectComponentDescriptor descriptor,
            DbSubjectComponent subjectComponent) {
        if (descriptor.properties().size() != subjectComponent.getComponentProperties().size()) {
            return false;
        }
        var descriptorProps = descriptor.properties()
                .stream()
                .collect(toMap(p -> p.name(), Function.identity()));

        return subjectComponent.getComponentProperties()
            .stream()
            .allMatch(dbProp -> matchingPropertyExists(dbProp, descriptorProps));
    }


    private static boolean matchingPropertyExists(
            DbSubjectComponentProperty dbProp, Map<String, AlgorithmProperty> descriptorProps) {
        var descriptorProp = descriptorProps.get(dbProp.getName());
        if (descriptorProp == null) {
            return false;
        }
        return dbProp.getName().equals(descriptorProp.name())
                && dbProp.getDescription().equals(descriptorProp.description())
                && dbProp.getType() == descriptorProp.type()
                && optionalFieldMatches(
                        dbProp.getDefaultValue(), descriptorProp.defaultValue())
                && optionalFieldMatches(
                        dbProp.getPropertiesKey(), descriptorProp.propertiesKey());
    }


    private static <T> boolean optionalFieldMatches(Optional<T> opt, T value) {
        return Objects.equals(value, opt.orElse(null));
    }


    public enum RegistrationResult {
        NEW("A new component was registered."),
        SAME("The component was already registered."),
        UPDATED("Updated existing component.");

        public final String description;

        RegistrationResult(String description) {
            this.description = description;
        }
    }
}

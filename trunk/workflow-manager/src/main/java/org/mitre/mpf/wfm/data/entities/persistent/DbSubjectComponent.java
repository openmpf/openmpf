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

package org.mitre.mpf.wfm.data.entities.persistent;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import org.mitre.mpf.wfm.service.component.ComponentLanguage;

@Entity
public class DbSubjectComponent {
    @Id
    private String componentName;
    public String getComponentName() {
        return componentName;
    }


    @Column(nullable = false)
    private String componentVersion;
    public String getComponentVersion() { return componentVersion; }
    public void setComponentVersion(String componentVersion) {
        this.componentVersion = componentVersion;
    }


    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ComponentLanguage sourceLanguage;
    public ComponentLanguage getSourceLanguage() { return sourceLanguage; }
    public void setSourceLanguage(ComponentLanguage sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }


    @Column(nullable = false)
    private String componentLibrary;
    public String getComponentLibrary() { return componentLibrary; }
    public void setComponentLibrary(String componentLibrary) {
        this.componentLibrary = componentLibrary;
    }


    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DbSubjectComponentProperty> componentProperties;
    public List<DbSubjectComponentProperty> getComponentProperties() {
        return componentProperties;
    }


    // Hibernate requires a no-arg constructor.
    public DbSubjectComponent() {
    }

    public DbSubjectComponent(String componentName) {
        this.componentName = componentName;
        this.componentProperties = new ArrayList<>();
    }
}

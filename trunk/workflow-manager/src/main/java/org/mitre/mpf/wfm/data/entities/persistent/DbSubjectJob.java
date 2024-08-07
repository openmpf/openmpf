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

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

import org.mitre.mpf.wfm.data.access.hibernate.AbstractHibernateDao;

@Entity
public class DbSubjectJob {

    @Id
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = AbstractHibernateDao.JOB_ID_SEQUENCE_NAME)
    @SequenceGenerator(name = AbstractHibernateDao.JOB_ID_SEQUENCE_NAME, allocationSize = 1)
    private long id;
    public long getId() { return id; }


    @Column(nullable = false)
    private String componentName;
    public String getComponentName() { return componentName; }


    @Column(nullable = false)
    private Instant timeReceived;
    public Instant getTimeReceived() { return timeReceived; }


    private Instant timeCompleted;
    public Optional<Instant> getTimeCompleted() { return Optional.ofNullable(timeCompleted); }
    public void setTimeCompleted(Instant timeCompleted) { this.timeCompleted = timeCompleted; }

    public boolean isComplete() { return timeCompleted != null; }

    private int priority;
    public int getPriority() { return priority; }


    @ElementCollection
    @Column(nullable = false)
    private Set<Long> detectionJobIds;
    public Set<Long> getDetectionJobIds() { return detectionJobIds; }


    @ElementCollection
    @Column(nullable = false)
    private Map<String, String> jobProperties;
    public Map<String, String> getJobProperties() { return jobProperties; }


    private boolean retrievedDetectionJobs;
    public boolean getRetrievedDetectionJobs() { return retrievedDetectionJobs; }
    public void setRetrievedDetectionJobs(boolean retrievedDetectionJobs) {
        this.retrievedDetectionJobs = retrievedDetectionJobs;
    }


    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DbCancellationState cancellationState;
    public DbCancellationState getCancellationState() {
        return cancellationState;
    }
    public void setCancellationState(DbCancellationState cancellationState) {
        this.cancellationState = cancellationState;
    }


    @ElementCollection
    @Column(nullable = false)
    private Set<String> errors;
    public Set<String> getErrors() { return errors; }
    public void addError(String error) {
        errors.add(error);
    }


    @ElementCollection
    @Column(nullable = false)
    private Set<String> warnings;
    public Set<String> getWarnings() { return warnings; }
    public void addWarning(String warning) {
        warnings.add(warning);
    }


    // Hibernate requires a no-arg constructor.
    public DbSubjectJob() {
    }

    public DbSubjectJob(
            String componentName,
            int priority,
            Collection<Long> jobIds,
            Map<String, String> jobProperties) {
        this.componentName = componentName;
        this.priority = priority == 0 ? 4 : priority;
        this.detectionJobIds = new HashSet<>(jobIds);
        this.jobProperties = new HashMap<>(jobProperties);

        this.timeReceived = Instant.now();

        this.errors = new HashSet<>();
        this.warnings = new HashSet<>();
        this.cancellationState = DbCancellationState.NOT_CANCELLED;
    }
}

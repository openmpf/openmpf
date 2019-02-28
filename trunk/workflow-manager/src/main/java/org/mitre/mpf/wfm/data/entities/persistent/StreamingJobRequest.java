/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.enums.StreamingJobStatusType;

import javax.persistence.*;
import java.time.Instant;

/**
 * This class includes the essential information which describes a streaming job. Instances of this class are stored in a
 * persistent data store (as opposed to a transient data store).
 */
@Entity
public class StreamingJobRequest {

	public StreamingJobRequest() { }

	/** The unique numeric identifier for this job.
	 * 	Using SEQUENCE rather than IDENTITY to avoid conflicts between batch and streaming job Ids
	 * */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private long id;
	public long getId() { return id; }

	/** The timestamp indicating when the server received this streaming job. */
	@Column
	private Instant timeReceived;
	public Instant getTimeReceived() { return timeReceived; }
	public void setTimeReceived(Instant timeReceived) { this.timeReceived = timeReceived; }

	/** The timestamp indicating when the server completed this streaming job.*/
	@Column
	private Instant timeCompleted;
	public Instant getTimeCompleted() { return timeCompleted; }
	public void setTimeCompleted(Instant timeCompleted) { this.timeCompleted = timeCompleted; }
	
	/** The priority of the job set when creating the streaming job.*/
	@Column	
	private int priority;
	public int getPriority() { return  priority; }
	public void setPriority(int priority) { this.priority = priority; }

    /** The current status of this streaming job.
     * Streaming job status includes condition status as defined by StreamingJobStatusType.
     * StatusDetail may also provide more detailed information about the status of the job.
     * Note that we keep the status enumeration and statusDetail as separate parameters for persisting in the
     * database.
     **/
    @Column
    @Enumerated(EnumType.STRING)
    private StreamingJobStatusType status = null;
    public StreamingJobStatusType getStatus() { return status; }
    public void setStatus(StreamingJobStatusType status) { this.status = status; }
    public void setStatus(StreamingJobStatusType status, String statusDetail) {
        setStatus(status);
        setStatusDetail(statusDetail);
    }

    @Column(columnDefinition = "TEXT")
    private String statusDetail = null;
    public String getStatusDetail() { return statusDetail; }
    public void setStatusDetail(String statusDetail) { this.statusDetail = statusDetail; }

	@Column
	@Lob
	private byte[] inputObject;
	public byte[] getInputObject() { return inputObject; }
	public void setInputObject(byte[] inputObject) { this.inputObject = inputObject; }

	@Column(columnDefinition = "TEXT")
	private String outputObjectDirectory;
	public String getOutputObjectDirectory() { return outputObjectDirectory; }
	public void setOutputObjectDirectory(String outputObjectDirectory) { this.outputObjectDirectory = outputObjectDirectory; }

	@Column
	private String pipeline;
	public String getPipeline() { return pipeline; }
	public void setPipeline(String pipeline) { this.pipeline = pipeline; }

	@Column
	private String externalId;
	public String getExternalId() { return externalId; }
	public void setExternalId(String externalId) { this.externalId = externalId; }

	@Column(columnDefinition = "TEXT")
	private String streamUri;
	public String getStreamUri() { return streamUri; }
	public void setStreamUri(String streamUri) { this.streamUri = streamUri; }

	@Column(columnDefinition = "TEXT")
	private String healthReportCallbackUri;
	public String getHealthReportCallbackUri() { return healthReportCallbackUri; }
	public void setHealthReportCallbackUri(String healthReportCallbackUri) { this.healthReportCallbackUri = healthReportCallbackUri; }

	@Column(columnDefinition = "TEXT")
	private String summaryReportCallbackUri;
	public String getSummaryReportCallbackUri() { return summaryReportCallbackUri; }
	public void setSummaryReportCallbackUri(String summaryReportCallbackUri) { this.summaryReportCallbackUri = summaryReportCallbackUri; }

	/** The version of the output object. */
	@Column
	private String outputObjectVersion;
	public void setOutputObjectVersion(String outputObjectVersion) { this.outputObjectVersion = outputObjectVersion; }

    @Column
    private String activityFrameId;
    public void setActivityFrameId(String activityFrameId) { this.activityFrameId = activityFrameId; }
    public String getActivityFrameId() { return activityFrameId; }

    @Column
    private Instant activityTimestamp;
    public Instant getActivityTimestamp() { return activityTimestamp; }
    public void setActivityTimestamp(Instant activityTimestamp) { this.activityTimestamp = activityTimestamp; }


    public String toString() { return String.format("%s#<id='%d'>", this.getClass().getSimpleName(), getId()); }
}

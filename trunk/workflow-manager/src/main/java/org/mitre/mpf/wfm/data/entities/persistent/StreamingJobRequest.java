/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

import java.util.Date;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import org.mitre.mpf.wfm.enums.JobStatusI.JobStatus;
import org.mitre.mpf.wfm.enums.StreamingJobStatus;

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
	@Temporal(TemporalType.TIMESTAMP)
	private Date timeReceived;
	public Date getTimeReceived() { return timeReceived; }
	public void setTimeReceived(Date timeReceived) { this.timeReceived = timeReceived; }

	/** The timestamp indicating when the server completed this streaming job.*/
	@Column
	@Temporal(TemporalType.TIMESTAMP)
	private Date timeCompleted;
	public Date getTimeCompleted() { return timeCompleted; }
	public void setTimeCompleted(Date timeCompleted) { this.timeCompleted = timeCompleted; }
	
	/** The priority of the job set when creating the streaming job.*/
	@Column	
	private int priority;
	public int getPriority() { return  priority; }
	public void setPriority(int priority) { this.priority = priority; }

	/** The current status of this streaming job. */
//	@Column
//	@Enumerated(EnumType.STRING)
    @AttributeOverrides( {
        @AttributeOverride(name="statusString", column = @Column(name="status") ),
        @AttributeOverride(name="detailString", column = @Column(name="status_detail") )
    })
    private StreamingJobStatusData streamingJobStatusData;

    @Transient
    private StreamingJobStatus status = null;
	public StreamingJobStatus getStatus() {
	    if ( status == null ) {
	        status = new StreamingJobStatus(streamingJobStatusData.getStatusString(), streamingJobStatusData.getDetailString());
        }
	    return status;
	}
    public void setStatus( JobStatus jobStatus) { this.status = new StreamingJobStatus(jobStatus); }
    public void setStatus( StreamingJobStatus streamingJobStatus) { this.status = streamingJobStatus; }

	@Column
	@Lob
	private byte[] inputObject;
	public byte[] getInputObject() { return inputObject; }
	public void setInputObject(byte[] inputObject) { this.inputObject = inputObject; }

	@Column
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

	@Column
	private String streamUri;
	public String getStreamUri() { return streamUri; }
	public void setStreamUri(String streamUri) { this.streamUri = streamUri; }

	@Column
	private String healthReportCallbackUri;
	public String getHealthReportCallbackUri() { return healthReportCallbackUri; }
	public void setHealthReportCallbackUri(String healthReportCallbackUri) { this.healthReportCallbackUri = healthReportCallbackUri; }

	@Column
	private String summaryReportCallbackUri;
	public String getSummaryReportCallbackUri() { return summaryReportCallbackUri; }
	public void setSummaryReportCallbackUri(String summaryReportCallbackUri) { this.summaryReportCallbackUri = summaryReportCallbackUri; }

	/** The version of the output object. */
	@Column
	private String outputObjectVersion;
	public void setOutputObjectVersion(String outputObjectVersion) { this.outputObjectVersion = outputObjectVersion; }

	public String toString() { return String.format("%s#<id='%d'>", this.getClass().getSimpleName(), getId()); }
}

@Embeddable
class StreamingJobStatusData {

	private String statusString;
	public void setStatusString(String statusString) { this.statusString = statusString; }
	public String getStatusString() { return statusString; }

	private String detailString;
	public void setDetailString(String detailString) { this.detailString = detailString; }
	public String getDetailString() { return detailString; }
}
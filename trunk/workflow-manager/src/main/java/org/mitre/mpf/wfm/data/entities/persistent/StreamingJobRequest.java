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

import org.mitre.mpf.wfm.enums.JobStatus;

import javax.persistence.*;
import java.util.Date;

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
	@Column
	@Enumerated(EnumType.STRING)
	private JobStatus status;
	public JobStatus getStatus() { return status; }
	public void setStatus(JobStatus status) { this.status = status; }

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

/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

import org.mitre.mpf.wfm.enums.MarkupStatus;

import javax.persistence.*;

@Entity
@Table(indexes = {@Index(name = "MARKUP_NDX_UNIQ", columnList = "jobId,mediaIndex,taskIndex,actionIndex", unique = true)})
public class MarkupResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	public long getId() { return id; }
	public void setId(long id) { this.id = id; }

	@Column
	private long mediaId;
	public long getMediaId() { return mediaId; }
	public void setMediaId(long mediaId) { this.mediaId = mediaId; }

	/** The Job ID with which this result is associated. */
	@Column
	private long jobId;
	public long getJobId() { return jobId; }
	public void setJobId(long jobId) { this.jobId = jobId; }

	/** The task index in the pipeline which generated this result. */
	@Column
	private int taskIndex;
	public int getTaskIndex() { return taskIndex; }
	public void setTaskIndex(int taskIndex) { this.taskIndex = taskIndex; }

	/** The action index in the pipeline which generated this result. */
	@Column
	private int actionIndex;
	public int getActionIndex() { return actionIndex; }
	public void setActionIndex(int actionIndex) { this.actionIndex = actionIndex; }

	/** The index of the media in the job's media collection from which this result was generated. */
	@Column
	private int mediaIndex;
	public int getMediaIndex() { return mediaIndex; }
	public void setMediaIndex(int mediaIndex) { this.mediaIndex = mediaIndex; }

	/** The URI of the file which produced this result. */
	@Column(columnDefinition = "TEXT")
	private String sourceUri;
	public String getSourceUri() { return sourceUri; }
	public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }

	/** The URI of the marked-up file. */
	@Column(columnDefinition = "TEXT")
	private String markupUri;
	public String getMarkupUri() { return markupUri; }
	public void setMarkupUri(String markupUri) { this.markupUri = markupUri; }

	/** The pipeline which created this result. */
	@Column
	private String pipeline;
	public String getPipeline() { return pipeline; }
	public void setPipeline(String pipeline) { this.pipeline = pipeline; }

	@Column
	@Enumerated(EnumType.STRING)
	private MarkupStatus markupStatus = MarkupStatus.UNKNOWN;
	public MarkupStatus getMarkupStatus() { return (markupStatus == null) ? MarkupStatus.UNKNOWN : markupStatus; }
	public void setMarkupStatus(MarkupStatus markupStatus) { this.markupStatus = markupStatus; }

	@Column(columnDefinition = "TEXT")
	private String message;
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	public String toString() {
		return String.format("%s#<id=%d, mediaId=%d, jobId=%d, taskIndex=%d, actionIndex=%d, mediaIndex=%d, sourceUri='%s', markupUri='%s', pipeline='%s', markupStatus='%s', message='%s'>",
				getClass().getSimpleName(), getMediaId(), getJobId(), getTaskIndex(), getActionIndex(), getMediaIndex(), getId(), getSourceUri(), getMarkupUri(), getPipeline(), getMarkupStatus(), message);
	}
}

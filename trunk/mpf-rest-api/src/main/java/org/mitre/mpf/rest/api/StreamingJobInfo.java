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

package org.mitre.mpf.rest.api;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import org.mitre.mpf.rest.api.util.TimeUtils;

// swagger includes
@Api(value = "streaming-jobs")
@ApiModel(description="StreamingJobInfo model")
public class StreamingJobInfo {
	private Long jobId;
	@ApiModelProperty(position=0)
	public Long getJobId() {
		return jobId;
	}
	private String pipelineName;
    @ApiModelProperty(position=3)
	public String getPipelineName() {
		return pipelineName;
	}
	// TODO jobPriority may be included in a later release
//	private int jobPriority = -1;
//	public int getJobPriority() { return jobPriority; }
	private String jobStatus;
    @ApiModelProperty(position=5)
	public String getJobStatus() {
		return jobStatus;
	}

	private String jobStatusDetail;
    @ApiModelProperty(position=6)
	public String getJobStatusDetail() { return jobStatusDetail; }

	// TODO jobProgress (alternative name jobRunTime) may be included in a later release
// 	private float jobProgress;
//	public float getJobProgress() { return jobProgress; }
	private String startDate;
    /**
     * The start time of this streaming job.
     * @return The start time of this streaming job. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    @ApiModelProperty(position=1, dataType="String", value = "streaming job start time, local system time. Example: 2018-01-07 10:23:04.6")
	public String getStartDate() {
		return startDate;
	}
	private String endDate;
    /**
     * The end time of this streaming job.
     * @return The end time of this streaming job. May be empty String if this job has not completed.
     * This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    @ApiModelProperty(position=2, dataType="String", value = "streaming job end time, local system time. Example: 2018-01-08 00:00:00.0 or empty String if the job hasn't completed.")
    public String getEndDate() {
		return endDate;
	}
	private String outputObjectDirectory;
    @ApiModelProperty(position=7)
    public String getOutputObjectDirectory() {
		return outputObjectDirectory;
	}
	private String streamUri = null;
    @ApiModelProperty(position=4)
    public String getStreamUri() { return streamUri; }

	private String activityFrameId = null;
    @ApiModelProperty(position=8)
    public String getActivityFrameId() { return activityFrameId; }

    private String activityTimestamp = null;
    /**
     * The detection time associated with the activityFrameId
     * @return The detection time associated with the activityFrameId.
     * May be empty String if no activity has been detected. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    @ApiModelProperty(position=9, dataType="String", value = "detection time associated with the activityFrameId, local system time. Example: 2018-01-07 18:30:00.5 or empty String if there has been no activity found in the job.")
    public String getActivityTimestamp() { return activityTimestamp; }

	//terminal if status is JOB_CREATION_ERROR, COMPLETE, CANCELLED, or ERROR - will be set in ModelUtils
	//to maintain the use of only standard Java in the model.api classes
	private boolean terminal;
    @ApiModelProperty(position=10)
	public boolean isTerminal() {
		return terminal;
	}

	public StreamingJobInfo() {}

	/** Constructor.
	 * @param jobId job id of this streaming job
	 * @param pipelineName name of the pipeline defined for this job
	 * @param jobPriority priority associated with this job
	 * @param jobStatus status of this job
     * @param jobStatusDetail additional details about the status of this job. May be null.
	 * @param jobProgress progress of this job
	 * @param startDate date when this job was started
	 * @param endDate endDate may be null if the streaming job is still active
	 * @param outputObjectDirectory directory where objects from this streaming job are created
	 * @param streamUri URI of the streaming data
	 * @param activityFrameId The frame id corresponding to the start of the first track generated in the current segment.
     * @param activityTimestamp The detection time associated with the activityFrameId. May be null if no activity has been detected.
	 * @param terminal if true, marks a terminal error
	 */
	public StreamingJobInfo(Long jobId, String pipelineName, int jobPriority, String jobStatus,
                            String jobStatusDetail, float jobProgress,
                            Date startDate, Date endDate, String outputObjectDirectory,
                            String streamUri, String activityFrameId, Date activityTimestamp, boolean terminal) {
		this.jobId = jobId;
		this.pipelineName = pipelineName;
		this.jobStatus = jobStatus;
		this.jobStatusDetail = jobStatusDetail;
		// TODO jobPriority and jobProgress (alternate name jobRunTime) may be included in a later release
//		this.jobPriority = jobPriority;
//		this.jobProgress = jobProgress;
		this.startDate = TimeUtils.getDateAsString(startDate);
		this.endDate = TimeUtils.getDateAsString(endDate);
		this.outputObjectDirectory = outputObjectDirectory;
		this.terminal = terminal;
		this.streamUri = streamUri;
		this.activityFrameId = activityFrameId;
		// Only get activity timestamp as a String if not null. Handling this here because TimeUtils.getDateAsString will return empty
        // String if activityTimestamp is passed as a null.
		if ( activityTimestamp != null ) {
            this.activityTimestamp = TimeUtils.getDateAsString(activityTimestamp);
        }
	}
	
}

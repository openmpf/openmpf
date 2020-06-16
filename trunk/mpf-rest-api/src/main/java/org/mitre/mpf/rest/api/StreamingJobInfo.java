/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

import java.time.Instant;

// swagger includes
@Api(value = "streaming-jobs")
@ApiModel(description="StreamingJobInfo model")
public class StreamingJobInfo {

    // TODO update springfox.swagger version after the @ApiModelProperty position bug is fixed
	// Note: a bug in springfox swagger may be causing the @ApiModelProperty position specification to not be honored.
    // See the bug report at https://github.com/springfox/springfox/issues/1280.
    // Leaving @ApiModelProperty position element usage in as this springfox swagger bug may be eventually fixed.

	private Long jobId;
	@ApiModelProperty(position=1, required = true)
	public Long getJobId() {
		return jobId;
	}
	private String pipelineName;
    @ApiModelProperty(position=4, required = true)
	public String getPipelineName() {
		return pipelineName;
	}
	// TODO jobPriority may be included in a later release
//	private int jobPriority = -1;
//	public int getJobPriority() { return jobPriority; }
	private String jobStatus;
    @ApiModelProperty(position=6, required = true)
	public String getJobStatus() {
		return jobStatus;
	}

	private String jobStatusDetail;
    @ApiModelProperty(position=7, required = true)
	public String getJobStatusDetail() { return jobStatusDetail; }

	// TODO jobProgress (alternative name jobRunTime) may be included in a later release
// 	private float jobProgress;
//	public float getJobProgress() { return jobProgress; }

	private Instant startDate;
    /**
     * The start time of this streaming job.
     * @return The start time of this streaming job.
     */
    @ApiModelProperty(position = 2, required = true, dataType = "java.lang.String",
		    value = "streaming job start time, local system time. Example: 2018-12-19T12:12:59.995-05:00")
    public Instant getStartDate() {
        return startDate;
    }


    private Instant endDate;
    /**
     * The end time of this streaming job.
     * @return The end time of this streaming job. May be null if this job has not completed.
     */
    @ApiModelProperty(position=3, required = true, dataType = "java.lang.String",
		    value = "streaming job end time, local system time. Example: 2018-12-19T12:12:59.995-05:00 or null if the job hasn't completed.")
    public Instant getEndDate() {
        return endDate;
    }


	private String outputObjectDirectory;
    @ApiModelProperty(position=8, required = true)
    public String getOutputObjectDirectory() {
		return outputObjectDirectory;
	}

	private String streamUri = null;
    @ApiModelProperty(position=5, required = true)
    public String getStreamUri() { return streamUri; }

	private String activityFrameId = null;
    @ApiModelProperty(position=9, required = true)
    public String getActivityFrameId() { return activityFrameId; }

    private Instant activityTimestamp = null;
    /**
     * The detection time associated with the activityFrameId
     * @return The detection time associated with the activityFrameId. May be null if no activity has been detected.
     */
    @ApiModelProperty(position=10, required = true, dataType = "java.lang.String",
		    value = "detection time associated with the activityFrameId, local system time. Example: 2018-12-19T12:12:59.995-05:00 or null if there has been no activity found in the job.")
    public Instant getActivityTimestamp() { return activityTimestamp; }


	//terminal if status is JOB_CREATION_ERROR, COMPLETE, CANCELLED, or ERROR.
	private boolean terminal;
    @ApiModelProperty(position=11, required = true)
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
                            Instant startDate, Instant endDate, String outputObjectDirectory,
                            String streamUri, String activityFrameId, Instant activityTimestamp, boolean terminal) {
		this.jobId = jobId;
		this.pipelineName = pipelineName;
		this.jobStatus = jobStatus;
		this.jobStatusDetail = jobStatusDetail;
		// TODO jobPriority and jobProgress (alternate name jobRunTime) may be included in a later release
//		this.jobPriority = jobPriority;
//		this.jobProgress = jobProgress;
		this.startDate = startDate;
		this.endDate = endDate;
		this.outputObjectDirectory = outputObjectDirectory;
		this.terminal = terminal;
		this.streamUri = streamUri;
		this.activityFrameId = activityFrameId;
        this.activityTimestamp = activityTimestamp;
 	}

}

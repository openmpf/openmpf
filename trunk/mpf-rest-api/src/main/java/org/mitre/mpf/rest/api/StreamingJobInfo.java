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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import org.mitre.mpf.interop.exceptions.MpfInteropUsageException;
import org.mitre.mpf.interop.util.TimeUtils;

// swagger includes
@Api(value = "streaming-jobs")
@ApiModel(description="StreamingJobInfo model")
public class StreamingJobInfo {

    // TODO update springfox.swagger version after the @ApiModelProperty postion bug is fixed
	// Note: a bug in springfox swagger may be causing the @ApiModelProperty position specification to to not be honored.
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

	private Date startDate;
    @JsonSetter("startDate")
    public void setStartDateFromString(String timestampStr) throws MpfInteropUsageException{
        if ( timestampStr != null ) {
            this.startDate = TimeUtils.parseStringAsDate(timestampStr);
        }
    }

    /**
     * The start time of this streaming job.
     * @return The start time of this streaming job.
     */
	public Date getStartDate() {
		return startDate;
	}
    @ApiModelProperty(position=2, required = true, dataType="String", value = "streaming job start time, local system time. Example: 2018-01-07 10:23:04.6.")
    @JsonGetter("startDate")
    /**
     * The start time of this streaming job.
     * @return The start time of this streaming job. This timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    public String getStartDateAsString() {
        if ( startDate != null ) {
            return TimeUtils.getDateAsString(startDate);
        } else {
            return null;
        }
    }

    private Date endDate;
    @JsonSetter("endDate")
    public void setEndDateFromString(String timestampStr) throws MpfInteropUsageException {
        if ( timestampStr != null ) {
            this.endDate = TimeUtils.parseStringAsDate(timestampStr);
        }
    }
    /**
     * The end time of this streaming job.
     * @return The end time of this streaming job. May be null if this job has not completed.
     */
    public Date getEndDate() {
        return endDate;
    }
    @ApiModelProperty(position=3, required = true, dataType="String", value = "streaming job end time, local system time. Example: 2018-01-08 00:00:00.0 or empty String if the job hasn't completed.")
    @JsonGetter("endDate")
    /**
     * The end time of this streaming job.
     * @return The end time of this streaming job. May be null if this job has not completed.
     * This timestamp will be returned as a String matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    public String getEndDateAsString() {
        if ( endDate != null ) {
            return TimeUtils.getDateAsString(endDate);
        } else {
            return null;
        }
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

    private Date activityTimestamp = null;
    @JsonSetter("activityTimestamp")
    public void setActivityTimestampFromString(String timestampStr) throws MpfInteropUsageException {
        if ( timestampStr != null ) {
            this.activityTimestamp = TimeUtils.parseStringAsDate(timestampStr);
        }
    }
    /**
     * The detection time associated with the activityFrameId
     * @return The detection time associated with the activityFrameId. May be null if no activity has been detected.
     */
    public Date getActivityTimestamp() { return activityTimestamp; }
    @ApiModelProperty(position=10, required = true, dataType="String", value = "detection time associated with the activityFrameId, local system time. Example: 2018-01-07 18:30:00.5 or empty String if there has been no activity found in the job.")
    @JsonGetter("activityTimestamp")
    /**
     * The detection time associated with the activityFrameId
     * @return The detection time associated with the activityFrameId.
     * May be null if no activity has been detected. Otherwise, this timestamp will be returned as a String
     * matching the TIMESTAMP_PATTERN, which is currently defined as {@value TimeUtils#TIMESTAMP_PATTERN}
     */
    public String getActivityTimestampAsString() {
        if ( activityTimestamp != null ) {
            return TimeUtils.getDateAsString(activityTimestamp);
        } else {
            return null;
        }
    }

	//terminal if status is JOB_CREATION_ERROR, COMPLETE, CANCELLED, or ERROR - will be set in ModelUtils
	//to maintain the use of only standard Java in the model.api classes
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
                            Date startDate, Date endDate, String outputObjectDirectory,
                            String streamUri, String activityFrameId, Date activityTimestamp, boolean terminal) {
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

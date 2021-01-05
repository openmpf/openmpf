/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

package org.mitre.mpf.component.executor.detection;

import org.mitre.mpf.component.api.detection.MPFDataType;

import java.util.HashMap;
import java.util.Map;

public class MPFMessageMetadata {

	// TODO: Consider moving dataType and algorithmProperties out of this structure so that it has parity with the
	// message metadata structure on the C++ side.

    private String dataUri;
	private final MPFDataType dataType;
	private long mediaId;
	private String taskName;
	private int taskIndex;
	private String actionName;
	private int actionIndex;
	private Map<String, String> algorithmProperties;
	private Map<String, String> mediaProperties;
	private long requestId;
	private String correlationId;
	private String breadcrumbId;
	private int splitSize;
	private long jobId;
	private String jobName;

	public String getDataUri() {
		return dataUri;
	}

	public MPFDataType getDataType() {
		return dataType;
	}

	public long getMediaId() {
		return mediaId;
	}

	public String getTaskName() {
		return taskName;
	}

	public int getTaskIndex() {
		return taskIndex;
	}

	public String getActionName() {
		return actionName;
	}

	public int getActionIndex() {
		return actionIndex;
	}

	public Map<String, String> getAlgorithmProperties() {
		return algorithmProperties;
	}

	public Map<String, String> getMediaProperties() {
		return mediaProperties;
	}

	public long getRequestId() {
		return requestId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public String getBreadcrumbId() {
		return breadcrumbId;
	}

	public int getSplitSize() {
		return splitSize;
	}

	public long getJobId() {
		return jobId;
	}

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}


	public MPFMessageMetadata(
        String dataUri,
        MPFDataType dataType,
        long mediaId,
        String taskName,
        int taskIndex,
        String actionName,
        int actionIndex,
        Map<String, String> algorithmProperties,
        Map<String, String> mediaProperties,
        long requestId,
        String correlationId,
        String breadcrumbId,
        int splitSize,
        long jobId,
        String jobName
    ) {
        this.dataUri = dataUri;
        this.dataType = dataType;
        this.requestId = requestId;

        this.mediaId = mediaId;

        this.taskName = taskName;
        this.taskIndex = taskIndex;

        this.actionName = actionName;
        this.actionIndex = actionIndex;

        this.correlationId = correlationId;
        this.breadcrumbId = breadcrumbId;
        this.splitSize = splitSize;
        this.jobId = jobId;

        this.jobName = jobName;

        //TODO:  may need to deep copy map depending on the types of properties it contains
	    if(algorithmProperties == null) {
		    this.algorithmProperties = new HashMap<String, String>(); // Treat a null properties map as an empty map.
	    } else {
		    this.algorithmProperties = new HashMap<String, String>(algorithmProperties);
	    }

		//TODO:  may need to deep copy map depending on the types of properties it contains
		if(mediaProperties == null) {
			this.mediaProperties = new HashMap<String, String>(); // Treat a null properties map as an empty map.
		} else {
			this.mediaProperties = new HashMap<String, String>(mediaProperties);
		}
    }

}

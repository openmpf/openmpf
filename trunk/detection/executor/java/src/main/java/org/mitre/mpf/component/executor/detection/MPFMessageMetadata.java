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
	private String stageName;
	private int stageIndex;
	private String actionName;
	private int actionIndex;
	private Map<String, String> algorithmProperties;
	private Map<String, String> mediaProperties;
	private long requestId;

	private String jobName;

	public String getDataUri() {
		return dataUri;
	}

	public void setDataUri(String dataUri) {
		this.dataUri = dataUri;
	}

	public MPFDataType getDataType() {
		return dataType;
	}

	public long getMediaId() {
		return mediaId;
	}

	public void setMediaId(long mediaId) {
		this.mediaId = mediaId;
	}

	public String getStageName() {
		return stageName;
	}

	public void setStageName(String stageName) {
		this.stageName = stageName;
	}

	public int getStageIndex() {
		return stageIndex;
	}

	public void setStageIndex(int stageIndex) {
		this.stageIndex = stageIndex;
	}

	public String getActionName() {
		return actionName;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public int getActionIndex() {
		return actionIndex;
	}

	public void setActionIndex(int actionIndex) {
		this.actionIndex = actionIndex;
	}

	public Map<String, String> getAlgorithmProperties() {
		return algorithmProperties;
	}

	public void setAlgorithmProperties(Map<String, String> algorithmProperties) {
		this.algorithmProperties = algorithmProperties;
	}


	public Map<String, String> getMediaProperties() {
		return mediaProperties;
	}

	public void setMediaProperties(Map<String, String> mediaProperties) {
		this.mediaProperties = mediaProperties;
	}

	public long getRequestId() {
		return requestId;
	}

	public void setRequestId(long requestId) {
		this.requestId = requestId;
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
        String stageName,
        int stageIndex,
        String actionName,
        int actionIndex,
        Map<String, String> algorithmProperties,
		Map<String, String> mediaProperties,
        long requestId,
		String jobName
    ) {
        this.dataUri = dataUri;
        this.dataType = dataType;
        this.requestId = requestId;

	    this.mediaId = mediaId;

	    this.stageName = stageName;
	    this.stageIndex = stageIndex;

	    this.actionName = actionName;
	    this.actionIndex = actionIndex;
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

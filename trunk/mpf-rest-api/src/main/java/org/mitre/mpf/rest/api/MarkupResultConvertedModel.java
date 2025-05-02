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

package org.mitre.mpf.rest.api;

public class MarkupResultConvertedModel {
    private long id;
    private String jobId;
    private long mediaId;
    private long parentMediaId;
    private String pipeline;
    private String markupUri;

    private String markupMediaType;
    private boolean markupFileAvailable;
    private String markupDownloadUrl;

    private MediaUri sourceUri;
    private String sourceMediaType;
    private boolean sourceFileAvailable;
    private String sourceDownloadUrl;

    public long getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public long getMediaId() {
        return mediaId;
    }

    public long getParentMediaId() {
        return parentMediaId;
    }

    public String getPipeline() {
        return pipeline;
    }

    public String getMarkupUri() {
        return markupUri;
    }

    public String getMarkupMediaType() {
        return markupMediaType;
    }

    public boolean isMarkupFileAvailable() {
        return markupFileAvailable;
    }

    public String getMarkupDownloadUrl() {
        return markupDownloadUrl;
    }


    public MediaUri getSourceUri() {
        return sourceUri;
    }

    public String getSourceMediaType() {
        return sourceMediaType;
    }

    public boolean isSourceFileAvailable() {
        return sourceFileAvailable;
    }

    public String getSourceDownloadUrl() {
        return sourceDownloadUrl;
    }

    public void setSourceDownloadUrl(String sourceDownloadUrl) {
        this.sourceDownloadUrl = sourceDownloadUrl;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public void setMediaId(long mediaId) {
        this.mediaId = mediaId;
    }

    public void setParentMediaId(long parentMediaId) {
        this.parentMediaId = parentMediaId;
    }

    public void setPipeline(String pipeline) {
        this.pipeline = pipeline;
    }

    public void setMarkupUri(String markupUri) {
        this.markupUri = markupUri;
    }

    public void setMarkupMediaType(String markupMediaType) {
        this.markupMediaType = markupMediaType;
    }

    public void setMarkupFileAvailable(boolean markupFileAvailable) {
        this.markupFileAvailable = markupFileAvailable;
    }

    public void setMarkupDownloadUrl(String markupDownloadUrl) {
        this.markupDownloadUrl = markupDownloadUrl;
    }

    public void setSourceUri(MediaUri sourceUri) {
        this.sourceUri = sourceUri;
    }

    public void setSourceMediaType(String sourceMediaType) {
        this.sourceMediaType = sourceMediaType;
    }

    public void setSourceFileAvailable(boolean sourceFileAvailable) {
        this.sourceFileAvailable = sourceFileAvailable;
    }


    public MarkupResultConvertedModel() {
    }

	public MarkupResultConvertedModel(long id, String jobId, long mediaId, long parentMediaId,
                                      String pipeline, String markupUri, String markupMediaType,
                                      String markupDownloadUrl, boolean markupFileAvailable,
                                      MediaUri sourceUri, String sourceMediaType,
                                      String sourceDownloadUrl, boolean sourceFileAvailable) {
		this.id = id;
		this.jobId = jobId;
		this.mediaId = mediaId;
		this.parentMediaId = parentMediaId;
		this.pipeline = pipeline;
		this.markupUri = markupUri;
		this.markupMediaType = markupMediaType;
		this.markupFileAvailable = markupFileAvailable;
		this.markupDownloadUrl = markupDownloadUrl;
		this.sourceUri = sourceUri;
		this.sourceMediaType = sourceMediaType;
		this.sourceFileAvailable = sourceFileAvailable;
		this.sourceDownloadUrl = sourceDownloadUrl;
	}
}

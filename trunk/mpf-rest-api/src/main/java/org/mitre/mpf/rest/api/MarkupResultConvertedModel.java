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

package org.mitre.mpf.rest.api;

public class MarkupResultConvertedModel {
	private long id;
	private long jobId;
	private String pipeline;
	private String markupUri;

	private String markupUriContentType;
	private boolean markupFileAvailable;
	private String markupImgUrl ;
	private String markupDownloadUrl;

	private String sourceUri;
	private String sourceURIContentType;
	private boolean sourceFileAvailable;
	private String sourceImgUrl;
	private String sourceDownloadUrl;

	public long getId() { return id; }
	public long getJobId() { return jobId; }
    public String getPipeline() { return pipeline; }
    public String getMarkupUri() { return markupUri; }
    public String getMarkupUriContentType() { return markupUriContentType; }
	public boolean isMarkupFileAvailable() {return markupFileAvailable;}
	public String getMarkupImgUrl() {return markupImgUrl;}
	public String getMarkupDownloadUrl() {return markupDownloadUrl;}


	public String getSourceUri() { return sourceUri; }
	public String getSourceUriContentType() { return sourceURIContentType; }
	public boolean isSourceFileAvailable() {return sourceFileAvailable;}
	public String getSourceImgUrl() {return sourceImgUrl;}
	public String getSourceDownloadUrl() {return sourceDownloadUrl;}

	public void setSourceDownloadUrl(String sourceDownloadUrl) {
		this.sourceDownloadUrl = sourceDownloadUrl;
	}

	public void setJobId(long jobId) {
		this.jobId = jobId;
	}

	public void setPipeline(String pipeline) {
		this.pipeline = pipeline;
	}

	public void setMarkupUri(String markupUri) {
		this.markupUri = markupUri;
	}

	public void setMarkupUriContentType(String markupUriContentType) {
		this.markupUriContentType = markupUriContentType;
	}

	public void setMarkupFileAvailable(boolean markupFileAvailable) {
		this.markupFileAvailable = markupFileAvailable;
	}

	public void setMarkupImgUrl(String markupImgUrl) {
		this.markupImgUrl = markupImgUrl;
	}

	public void setMarkupDownloadUrl(String markupDownloadUrl) {
		this.markupDownloadUrl = markupDownloadUrl;
	}

	public void setSourceUri(String sourceUri) {
		this.sourceUri = sourceUri;
	}

	public void setSourceURIContentType(String sourceURIContentType) {
		this.sourceURIContentType = sourceURIContentType;
	}

	public void setSourceFileAvailable(boolean sourceFileAvailable) {
		this.sourceFileAvailable = sourceFileAvailable;
	}

	public void setSourceImgUrl(String sourceImgUrl) {
		this.sourceImgUrl = sourceImgUrl;
	}






	public MarkupResultConvertedModel() {}

    public MarkupResultConvertedModel(long id, long jobId, String pipeline,
									  String markupUri, String markupUriContentType,String markupImgUrl,String markupDownloadUrl,boolean markupFileAvailable, String sourceUri,String sourceURIContentType,String sourceImgUrl,String sourceDownloadUrl, boolean sourceFileAvailable) {
		this.id = id;
		this.jobId = jobId;
		this.pipeline = pipeline;
		this.markupUri = markupUri;
		this.markupUriContentType = markupUriContentType;
		this.markupFileAvailable = markupFileAvailable;
		this.markupImgUrl = markupImgUrl;
		this.markupDownloadUrl = markupDownloadUrl;
		this.sourceUri = sourceUri;
		this.sourceURIContentType = sourceURIContentType;
		this.sourceFileAvailable = sourceFileAvailable;
		this.sourceImgUrl = sourceImgUrl;
		this.sourceDownloadUrl = sourceDownloadUrl;
	}
}

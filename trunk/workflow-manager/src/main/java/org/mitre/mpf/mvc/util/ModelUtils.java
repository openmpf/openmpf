/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

package org.mitre.mpf.mvc.util;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.rest.api.*;
import org.mitre.mpf.rest.api.StreamingJobInfoResponse;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.StreamingJobRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.enums.JobStatus;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("singleton")
public class ModelUtils {
	private static final Logger log = LoggerFactory.getLogger(ModelUtils.class);

	@Autowired
	@Qualifier(PropertiesUtil.REF)
	private PropertiesUtil propertiesUtil;

	// class variable for lazy initialization
	static InfoModel s_infoModel = null;

	//prevents needing org.mitre.mpf.** types in MarkupResultModel - this allows the api model classes
	//to be used in other projects without added dependencies
	public static MarkupResultModel converMarkupResult(
			MarkupResult markupResult) {
		boolean isImage = false;
		boolean fileExists = true;
		if(markupResult.getMarkupUri() != null) {
			String nonUrlPath = markupResult.getMarkupUri().replace("file:", "");
			String markupContentType = NIOUtils.getPathContentType(Paths.get(nonUrlPath));
			isImage = (markupContentType != null && StringUtils.startsWithIgnoreCase(markupContentType, "IMAGE"));
			fileExists = new File(nonUrlPath).exists();
		}		
		
		//if the markup uri does not end with 'avi', the file is considered an image
		return new MarkupResultModel(markupResult.getId(), markupResult.getJobId(),
				  markupResult.getPipeline(), markupResult.getMarkupUri(), 
				  markupResult.getSourceUri(), isImage, fileExists);
	}

	public static MarkupResultConvertedModel convertMarkupResultWithContentType(MarkupResult markupResult) {
		String markupUriContentType = "";
		String markupImgUrl = "";
		String markupDownloadUrl ="";
		String sourceUriContentType="";
		String sourceImgUrl = "";
		String sourceDownloadUrl ="";
		boolean markupFileAvailable = false;
		boolean sourceFileAvailable = false;

		if(markupResult.getMarkupUri() != null) {
			String nonUrlPath = markupResult.getMarkupUri();
			markupUriContentType = NIOUtils.getPathContentType(Paths.get(URI.create(nonUrlPath)));
			File f = new File(URI.create(nonUrlPath));
			if(f != null && f.exists()) {
				markupFileAvailable = true;
				markupImgUrl="markup/content?id="+markupResult.getId();
				markupDownloadUrl = "markup/download?id=" + markupResult.getId();
			}
		}

		if(markupResult.getSourceUri() != null) {
			String nonUrlPath = markupResult.getSourceUri();
			sourceUriContentType = NIOUtils.getPathContentType(Paths.get(URI.create(nonUrlPath)));
			File f = new File(URI.create(nonUrlPath));
			if (f != null && f.exists()){
				sourceFileAvailable = true;
				sourceImgUrl = "server/node-image?nodeFullPath=" + Paths.get(URI.create(nonUrlPath));
				sourceDownloadUrl = "server/download?fullPath=" + Paths.get(URI.create(nonUrlPath));
			}
		}

		return new MarkupResultConvertedModel(markupResult.getId(), markupResult.getJobId(),markupResult.getPipeline(),
				markupResult.getMarkupUri(),markupUriContentType,markupImgUrl,markupDownloadUrl,markupFileAvailable,
				markupResult.getSourceUri(),sourceUriContentType,sourceImgUrl,sourceDownloadUrl,sourceFileAvailable);
	}
	
	//this method is created for the same reason as converMarkupResult
	public static SingleJobInfo convertJobRequest(JobRequest jobRequest,
			float jobContainerProgress) {
		JobStatus jobStatus = jobRequest.getStatus();
		// some job status' may be terminal
		boolean isTerminal = (jobStatus != null && jobStatus.isTerminal());
		
		return new SingleJobInfo(jobRequest.getId(), jobRequest.getPipeline(), jobRequest.getPriority(), 
				jobRequest.getStatus().toString(), jobContainerProgress, jobRequest.getTimeReceived(),
				jobRequest.getTimeCompleted(), jobRequest.getOutputObjectPath(), isTerminal);
	}

	public static StreamingJobInfoResponse convertJobRequest(StreamingJobRequest streamingJobRequest,
			float jobContainerProgress) {
		JobStatus jobStatus = streamingJobRequest.getStatus();
		// some job status' may be terminal
		boolean isTerminal = (jobStatus != null && jobStatus.isTerminal());

		return new StreamingJobInfoResponse(streamingJobRequest.getId(), streamingJobRequest.getExternalId(),
				streamingJobRequest.getPipeline(),
				streamingJobRequest.getPriority(),
				streamingJobRequest.getStatus().toString(), jobContainerProgress, streamingJobRequest.getTimeReceived(),
				streamingJobRequest.getTimeCompleted(),
				streamingJobRequest.getOutputObjectDirectory(),
				streamingJobRequest.getStreamURI(),
				isTerminal);
	}


	// returns an InfoModel
	public InfoModel getInfoModel() {
		if ( s_infoModel == null ) {
			PropertiesUtil utils = propertiesUtil;
			if (utils != null) {
				s_infoModel = new InfoModel(/*app-family|appFamily*/ "Media Processing Framework",
							/*app*/ "Workflow Manager",
							/*version*/ utils.getSemanticVersion(),
							/*gitHash*/ utils.getGitHash(),
							/*gitBranch*/ utils.getGitBranch(),
							/*buildNum*/ utils.getBuildNum());
			} else {
				s_infoModel = new InfoModel(/*app-family|appFamily*/ "Media Processing Framework",
							/*app*/ "Workflow Manager",
							/*version*/ "x.x.x",
							/*gitHash*/ "???",
							/*gitBranch*/ "???",
							/*buildNum*/ "0");
			}
		}
		return s_infoModel;
	}
}

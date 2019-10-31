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

package org.mitre.mpf.rest.client;

import com.fasterxml.jackson.core.type.TypeReference;
import http.rest.RequestInterceptor;
import http.rest.RestClientException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.HttpRequestBase;
import org.mitre.mpf.rest.api.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
	
    public static void main( String[] args ) throws RestClientException, IOException, InterruptedException
    {
        System.out.println("Starting rest-client!");
        
        //not necessary for localhost, but left if a proxy configuration is needed
        //System.setProperty("http.proxyHost","");
        //System.setProperty("http.proxyPort","");  
        
        String currentDirectory;
		currentDirectory = System.getProperty("user.dir");
		System.out.println("Current working directory : " + currentDirectory);
		
		String username = "mpf";
		String password = "mpf123";
		byte[] encodedBytes = Base64.encodeBase64((username + ":" + password).getBytes());
		String base64 = new String(encodedBytes);
		System.out.println("encodedBytes " + base64);
		final String mpfAuth = "Basic " + base64;
        
        RequestInterceptor authorize = new RequestInterceptor() {
            @Override
            public void intercept(HttpRequestBase request) {
                request.addHeader("Authorization", mpfAuth /*credentials*/);
            }
        };
        
        //RestClient client = RestClient.builder().requestInterceptor(authorize).build();
        CustomRestClient client = (CustomRestClient) CustomRestClient.builder().restClientClass(CustomRestClient.class).requestInterceptor(authorize).build();
        
		//getAvailableWorkPipelineNames
        String url = "http://localhost:8080/workflow-manager/rest/pipelines";
        Map<String, String> params = new HashMap<String, String>();  
        List<PipelinesResponse> pipelineResponses = client.get(url, params, new TypeReference<List<PipelinesResponse>>() {});
        System.out.println("availableWorkPipelines size: " + pipelineResponses.size());
        System.out.println(Arrays.toString(pipelineResponses.stream().map(pipelineResponse -> pipelineResponse.getPipelineName()).toArray()));
        
        //processMedia        
		JobCreationRequest jobCreationRequest = new JobCreationRequest();
        URI uri = Paths.get(currentDirectory,"/trunk/workflow-manager/src/test/resources/samples/meds/aa/S001-01-t10_01.jpg").toUri();
        jobCreationRequest.getMedia().add(new JobCreationMediaData(uri.toString()));
        uri = Paths.get(currentDirectory,"/trunk/workflow-manager/src/test/resources/samples/meds/aa/S008-01-t10_01.jpg").toUri();
		jobCreationRequest.getMedia().add(new JobCreationMediaData(uri.toString()));
		jobCreationRequest.setExternalId("external id");
		
		//get first DLIB pipeline
		String firstDlibPipeline = pipelineResponses.stream().map(PipelinesResponse::getPipelineName)
				//.peek(pipelineName -> System.out.println("will filter - " + pipelineName))
	            .filter(pipelineName -> pipelineName.startsWith("DLIB"))
	            .findFirst()
	            .get();
		
		System.out.println("found firstDlibPipeline: " + firstDlibPipeline);
		
		jobCreationRequest.setPipelineName(firstDlibPipeline); //grabbed from 'rest/pipelines' - see #1
		//two optional params
		jobCreationRequest.setBuildOutput(true);
		//jobCreationRequest.setPriority(priority); //will be set to 4 (default) if not set
		JobCreationResponse jobCreationResponse = client.customPostObject("http://localhost:8080/workflow-manager/rest/jobs", jobCreationRequest, JobCreationResponse.class);
		System.out.println("jobCreationResponse job id: " + jobCreationResponse.getJobId());        
        
		System.out.println("\n---Sleeping for 10 seconds to let the job process---\n");
        Thread.sleep(10000);
		
        //getJobStatus
        url = "http://localhost:8080/workflow-manager/rest/jobs"; // /status";
        params = new HashMap<String, String>();
        //OPTIONAL
        //params.put("v", "") - no versioning currently implemented         
        //id is now a path var - if not set, all job info will returned
        url = url + "/" + Long.toString(jobCreationResponse.getJobId());
        SingleJobInfo jobInfo = client.get(url, params, SingleJobInfo.class);
        System.out.println("jobInfo id: " + jobInfo.getJobId());
	        
        //getSerializedOutput
        String jobIdToGetOutputStr = Long.toString(jobCreationResponse.getJobId());
        url = "http://localhost:8080/workflow-manager/rest/jobs/" + jobIdToGetOutputStr + "/output/detection";
        params = new HashMap<String, String>();                
        //REQUIRED  - job id is now a path var and required for this endpoint
        String serializedOutput = client.getAsString(url, params);
        System.out.println("serializedOutput: " + serializedOutput);
    }
}

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

package org.mitre.mpf.rest.client;

import http.rest.RestClient;
import http.rest.RestClientBuilder;
import http.rest.RestClientException;
import org.apache.commons.io.Charsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.Map;

public class CustomRestClient extends RestClient {

	public CustomRestClient(RestClientBuilder builder) {
		super(builder);
	}

	// Allows for posting with request/url params and returns an object
	public <T> T customPostParams(String path, Map<String, String> queryParams, Class<T> entityClass, int expectedStatus) throws RestClientException, IOException {
		HttpPost post = newHttpPost(appendParams(path, queryParams));
		HttpResponse response = execute(interceptor, post, expectedStatus); //201 for post with create, 200 for updates
		String jsonContentStr = contentAsString(response);
		return bindObject(jsonContentStr, entityClass);
	}

	// Post that allows an object to be posted and an object to be returned
	public <T> T customPostObject(String urlStr, Object objToSend, 
			Class<T> entityClass) throws IOException, RestClientException {
		HttpPost post = contentTypeJson(newHttpPost(urlStr));
    	HttpEntity entity = new StringEntity(toJson(objToSend).toString(), Charsets.UTF_8);
    	post.setEntity(entity);
    	HttpResponse response = execute(interceptor, post, 201 /*expectedStatus*/); // 201 for post
    	// System.out.println(Arrays.toString(response.getAllHeaders()));

    	String jsonContentStr = contentAsString(response);
    	// System.out.println("response: " + jsonContentStr);
	    	
    	if (jsonContentStr != null) {
    		return bindObject(jsonContentStr, entityClass);
    	} else {
    	    return null;
    	}
    }
	
	//get that returns String content without Object binding
	public String getAsString(String path, Map<String, String> queryParams) throws IOException {	
		HttpGet get = newHttpGet(appendParams(path, queryParams));
		HttpResponse response = execute(interceptor, get);
		String content = null;
		try {
		    content = contentAsString(response);
		} catch (IOException e) {
		    consume(response);
		}
		return content;
	}
}
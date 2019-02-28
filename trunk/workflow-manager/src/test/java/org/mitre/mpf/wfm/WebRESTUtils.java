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

package org.mitre.mpf.wfm;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.mitre.mpf.rest.api.SingleJobInfo;
import org.mitre.mpf.wfm.enums.BatchJobStatusType;
import org.mitre.mpf.wfm.ui.Utils;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebRESTUtils {

	public static final String REST_URL = Utils.BASE_URL + "/workflow-manager/rest/";
	public static final String MPF_AUTHORIZATION = "Basic bXBmOm1wZjEyMw==";// mpf user base64 <username:password>
	public static final String ADMIN_AUTHORIZATION = "Basic YWRtaW46bXBmYWRtCg";// admin user base64 <username:password>

	private static final Logger log = LoggerFactory.getLogger(WebRESTUtils.class);

	private static final ObjectMapper objectMapper = ObjectMapperFactory.customObjectMapper();

	public static JSONArray getNodes() throws JSONException, MalformedURLException {
		String url = REST_URL + "nodes/info.json";
		log.debug("getNodes get {}",url);
		JSONObject obj = new JSONObject(getJSON(new URL(url), MPF_AUTHORIZATION));
		return obj.getJSONArray("nodeModels");
	}

	public static String getJSON(URL url, String auth) {
		HttpURLConnection conn = null;
		log.debug("getJSON url :" + url);
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			conn.setRequestProperty("Accept", "application/json");

			if (auth != null && auth.length() > 0)// add authorization
				conn.setRequestProperty("Authorization", auth);

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}
			String results = getStringFromInputStream(conn.getInputStream());
			log.debug("url :" + url + " json results:" + results);
			return results;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (conn != null)
				conn.disconnect();
		}
		return null;
	}

	/***
	 *
	 * @param url
	 * @param params
	 *            i.e. "{\"qty\":100,\"name\":\"iPad 4\"}"
	 * @return
	 */
	public static String postJSON(URL url, String params, String auth) {
		log.debug("postJSON url :" + url);
		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");

			if (auth != null && auth.length() > 0)// add authorization
				conn.setRequestProperty("Authorization", auth);

			if(params != null){
				log.debug("url :" + url + " params:" + params);
				OutputStream os = conn.getOutputStream();
				os.write(params.getBytes());
				os.flush();
			}

			if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED && conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				log.error("postJSON Error: Failed to make HttpURLConnection, responseCode is " + conn.getResponseCode() + ", response message is " +
						conn.getResponseMessage());
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			String results = getStringFromInputStream(conn.getInputStream());
			log.debug("url :" + url + " json results:" + results);
			conn.disconnect();
			return results;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/***
	 *
	 * @param url
	 *            i.e. "param1=a&param2=b&param3=c"
	 * @return
	 * @throws IOException
	 */
	public static String postParams(URL url, List<NameValuePair> paramsList, String auth, int httpResponseCode) {
		try {
			//URLEncodedUtils is a nice helper when building a params set for http requests
			String urlParameters = URLEncodedUtils.format(paramsList, StandardCharsets.UTF_8);

			byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
			int postDataLength = postData.length;

			HttpURLConnection conn= (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod( "POST" );
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("charset", "utf-8");
			conn.setRequestProperty("Content-Length", Integer.toString( postDataLength));
			conn.setUseCaches(false);

			if (auth != null && auth.length() > 0)// add authorization
				conn.setRequestProperty("Authorization", auth);

			OutputStream os = conn.getOutputStream();
			os.write(postData);
			os.flush();

			if (conn.getResponseCode() != httpResponseCode) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			String results = getStringFromInputStream(conn.getInputStream());
			conn.disconnect();
			return results;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/***
	 * Helper method to convert InputStream to String
	 *
	 * @param is
	 * @return the data as a string
	 */
	public static String getStringFromInputStream(InputStream is) {
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();
		String line;
		try {
			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return sb.toString();
	}

	public static SingleJobInfo getSingleJobInfo(long jobId) throws JsonParseException, JsonMappingException, IOException {
		String urlJobsStatus = REST_URL + "jobs/" + Long.toString(jobId) + ".json";
		String jsonJobResponse = getJSON(new URL(urlJobsStatus), MPF_AUTHORIZATION);
		Assert.assertTrue("Failed to retrieve JSON when GETting job info for job id: " + Long.toString(jobId), jsonJobResponse.length() >= 0);
		return objectMapper.readValue(jsonJobResponse, SingleJobInfo.class);
	}

	public static BatchJobStatusType getJobsStatus(long jobid)throws JsonParseException, JsonMappingException, IOException  {
		SingleJobInfo singleJobInfo = getSingleJobInfo(jobid);
		//convert to the enum and return
		return BatchJobStatusType.valueOf(singleJobInfo.getJobStatus());
	}

	public static boolean waitForJobToTerminate(long jobid, long delay) throws InterruptedException, JsonParseException, JsonMappingException, IOException {
		log.info("[waitForJobToTerminate] job {}, delay:{} ", jobid, delay);
		int count=20;
		BatchJobStatusType status;
		do{
			status = getJobsStatus(jobid);
			log.info("[waitForJobToTerminate] job {}, status:{} delay:{} count{}" ,jobid,status,delay,count);
			Thread.sleep(delay);
			count--;
		}
		while(count > 0 && !status.isTerminal());
		if(count > 0) return true;
		return false;
	}
}

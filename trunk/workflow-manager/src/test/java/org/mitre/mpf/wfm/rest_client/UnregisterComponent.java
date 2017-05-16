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


package org.mitre.mpf.wfm.rest_client;

import http.rest.RequestInterceptor;
import http.rest.RestClient;
import http.rest.RestClientException;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by mpf on 10/2/15.
 */
public class UnregisterComponent {
    private static final Logger log = LoggerFactory.getLogger(RegisterComponent.class);

    public static void main(String[] args) {

        String filePath = "/home/mpf/mpf/trunk/java-hello-world/src/main/resources/HelloWorldComponent.json";
            //"/home/mpf/mpf/trunk/extraction/hello/cpp/src/helloComponent.json";
        String url = "http://localhost:8080/workflow-manager/rest/component/unregisterViaFile";
        final String credentials = "Basic bXBmOm1wZjEyMw==";

        Map<String, String> params = new HashMap<String, String>();

        System.out.println("Starting rest-client!");

        RequestInterceptor authorize = new RequestInterceptor() {
            @Override
            public void intercept(HttpRequestBase request) {
                request.addHeader("Authorization", credentials);
            }
        };
        RestClient client = RestClient.builder().requestInterceptor(authorize).build();

        if (args.length > 0) {
            filePath = args[0];
        }
        log.info(filePath);
        params.put("filePath", filePath);
        Map<String, String> stringVal = null;
        try {
            stringVal = client.get(url, params, Map.class);
        } catch (RestClientException e) {
            log.error("RestClientException occurred");
            e.printStackTrace();
        } catch (IOException e) {
            log.error("IOException occurred");
            e.printStackTrace();
        }
        System.out.println(stringVal.get("message"));
    }
}

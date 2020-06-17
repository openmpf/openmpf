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

import com.fasterxml.jackson.core.type.TypeReference;
import http.rest.RequestInterceptor;
import http.rest.RestClientException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.HttpRequestBase;
import org.mitre.mpf.interop.util.MpfObjectMapper;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.JobCreationResponse;
import org.mitre.mpf.rest.api.SingleJobInfo;
import org.mitre.mpf.rest.api.pipelines.Pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static final String USAGE = "Args: <username> <raw-password> <face-image-file-path>";
    public static final String HOST_PREFIX = "http://localhost:8080/workflow-manager/rest/";

    public static void main(String[] args) {

        if (args.length < 3 || args[0].equals("-h") || args[0].equals("-help") || args[0].equals("--help")) {
            System.out.println(USAGE);
            System.exit(0);
        }

        String userName = args[0];
        String password = args[1];
        String media = args[2];

        Path mediaPath = Paths.get(media);
        if (!Files.exists(mediaPath)) {
            System.err.println("Error: " + media + " does not exist.");
            System.exit(1);
        }

        System.out.println("Starting example REST client!\n");

        // Not necessary for localhost, but may be useful if a proxy configuration is needed:
        // System.setProperty("http.proxyHost","");
        // System.setProperty("http.proxyPort","");

        // Base64 encoding does not mean encryption or hashing.
        // HTTP Authorization is as secure as sending the credentials in clear text. Consider using HTTPS.
        byte[] encodedBytes = Base64.encodeBase64((userName + ":" + password).getBytes());
        String base64 = new String(encodedBytes);

        final String mpfAuth = "Basic " + base64;

        RequestInterceptor authorize = new RequestInterceptor() {
            @Override
            public void intercept(HttpRequestBase request) {
                request.addHeader("Authorization", mpfAuth);
            }
        };

        CustomRestClient client = (CustomRestClient) CustomRestClient.builder()
                .restClientClass(CustomRestClient.class)
                .requestInterceptor(authorize)
                .objectMapper(new MpfObjectMapper())
                .build();

        try {

            // Get available pipeline names
            List<Pipeline> pipelines = client.get(HOST_PREFIX + "pipelines", new HashMap<>(), new TypeReference<>() {
            });
            System.out.println("Number of available pipelines: " + pipelines.size());
            System.out.println("Available pipelines:\n" +
                    pipelines.stream().map(Pipeline::getName).collect(Collectors.joining("\n")) + "\n");

            JobCreationRequest jobCreationRequest = new JobCreationRequest();
            jobCreationRequest.getMedia().add(new JobCreationMediaData(mediaPath.toUri().toString()));
            jobCreationRequest.setExternalId("external id");

            // Get the first DLIB pipeline
            String firstDlibPipeline = pipelines.stream().map(Pipeline::getName)
                    //.peek(pipelineName -> System.out.println("will filter - " + pipelineName))
                    .filter(pipelineName -> pipelineName.startsWith("DLIB"))
                    .findFirst()
                    .get();

            System.out.println("Using DLIB pipeline: " + firstDlibPipeline);

            jobCreationRequest.setPipelineName(firstDlibPipeline);
            jobCreationRequest.setBuildOutput(true);
            // jobCreationRequest.setPriority(priority); //will be set to 4 (default) if not set

            JobCreationResponse jobCreationResponse = client.customPostObject(HOST_PREFIX + "jobs",
                    jobCreationRequest, JobCreationResponse.class);
            System.out.println("Job id: " + jobCreationResponse.getJobId() + "\n");

            System.out.println("---Sleeping for 10 seconds to let the job process---\n");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Id is now a path var. If not set, all job info will returned.
            SingleJobInfo jobInfo = client.get(HOST_PREFIX + "jobs/" + jobCreationResponse.getJobId(),
                    new HashMap<>(), SingleJobInfo.class);
            System.out.println("Job status: " + jobInfo.getJobStatus() + "\n");

            // Job id is now a path var and required for this endpoint
            String jsonOutput = client.getAsString(HOST_PREFIX + "jobs/" + jobCreationResponse.getJobId() +
                    "/output/detection", new HashMap<>());

            // Format output to look pretty
            MpfObjectMapper objectMapper = new MpfObjectMapper();
            Object jsonObject = objectMapper.readValue(jsonOutput, Object.class);
            String formattedJsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);

            System.out.println("JSON output:\n" + formattedJsonOutput);

        } catch (RestClientException | IOException e) {
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        }
    }
}

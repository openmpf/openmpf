/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.interop.util.MpfObjectMapper;
import org.mitre.mpf.rest.api.JobCreationMediaData;
import org.mitre.mpf.rest.api.JobCreationRequest;
import org.mitre.mpf.rest.api.JobCreationResponse;
import org.mitre.mpf.rest.api.SingleJobInfo;
import org.mitre.mpf.rest.api.pipelines.Pipeline;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {

    public static final String DEFAULT_HOST_PREFIX = "http://localhost:8080";
    public static final String WFM_REST_PATH = "/rest/";

    private static final String DEFAULT_MPF_USER = "mpf";
    private static final String DEFAULT_MPF_USER_PWD = "mpf123";

    public static final String USAGE =
            "Args: <host-prefix> <username> <raw-password> <face-image-file-path>\n" +
            "    Example args: " + DEFAULT_HOST_PREFIX + " " + DEFAULT_MPF_USER + " " + DEFAULT_MPF_USER_PWD +
                    " /home/mpf/Desktop/SAMPLES/Lenna.jpg\n" +
            "Args: <face-image-file-path>\n" +
            "    Example args: /home/mpf/Desktop/SAMPLES/Lenna.jpg";

    public static void main(String[] args) {

        if ((args.length != 1 && args.length != 4) ||
                args[0].equals("-h") || args[0].equals("-help") || args[0].equals("--help")) {
            System.out.println(USAGE);
            System.exit(0);
        }

        String hostPrefix = DEFAULT_HOST_PREFIX;
        String userName = DEFAULT_MPF_USER;
        String password = DEFAULT_MPF_USER_PWD;
        String media = null;

        if (args.length == 1) {
            media = args[0];
        }
        else if (args.length == 4) {
            hostPrefix = args[0];
            userName = args[1];
            password = args[2];
            media = args[3];
        }

        String restPrefix = hostPrefix + WFM_REST_PATH;

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
        String base64 = Base64.getEncoder().encodeToString((userName + ':' + password).getBytes());
        String mpfAuth = "Basic " + base64;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", mpfAuth);

        MpfObjectMapper mpfObjectMapper = new MpfObjectMapper();

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new MappingJackson2HttpMessageConverter(mpfObjectMapper));


        // Get available pipeline names

        ResponseEntity<List<Pipeline>> pipelineResponseEntity =
                restTemplate.exchange(
                        restPrefix + "pipelines",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<>() {}
                );

        if (!pipelineResponseEntity.getStatusCode().equals(HttpStatus.OK)) {
            System.err.println("There was a problem getting the pipeline names. Status code: "
                    + pipelineResponseEntity.getStatusCode());
            System.exit(1);
        }

        List<Pipeline> pipelines = pipelineResponseEntity.getBody();

        System.out.println("Number of available pipelines: " + pipelines.size());
        System.out.println("Available pipelines:\n" +
                pipelines.stream().map(Pipeline::getName).collect(Collectors.joining("\n")) + "\n");


        // Get the first FACE pipeline

        JobCreationRequest jobCreationRequest = new JobCreationRequest();
        jobCreationRequest.getMedia().add(new JobCreationMediaData(mediaPath.toUri().toString()));
        jobCreationRequest.setExternalId("external id");

        Optional<String> firstFacePipeline = pipelines.stream().map(Pipeline::getName)
                .filter(pipelineName -> pipelineName.contains("FACE"))
                .findFirst();

        if (!firstFacePipeline.isPresent()) {
            System.err.println("No available FACE pipelines.");
            System.exit(1);
        }

        System.out.println("Using FACE pipeline: " + firstFacePipeline.get());

        jobCreationRequest.setPipelineName(firstFacePipeline.get());
        jobCreationRequest.setBuildOutput(true);
        // jobCreationRequest.setPriority(priority); //will be set to 4 (default) if not set

        HttpEntity<JobCreationRequest> jobCreationRequestEntity =
                new HttpEntity<>(jobCreationRequest, headers);

        ResponseEntity<JobCreationResponse> jobCreationResponseEntity =
                restTemplate.postForEntity(
                        restPrefix + "jobs",
                        jobCreationRequestEntity,
                        JobCreationResponse.class
                );

        if (!jobCreationResponseEntity.getStatusCode().equals(HttpStatus.CREATED)) {
            System.err.println("There was a problem creating the job. Status code: "
                    + jobCreationResponseEntity.getStatusCode());
            System.exit(1);
        }


        // Create a job using the first FACE pipeline

        String   jobId = jobCreationResponseEntity.getBody().getJobId();
        System.out.println("Created job with id: " + jobId + "\n");

        System.out.println("---Sleeping for 10 seconds to let the job process---\n");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Id is now a path var. If not set, all job info will returned.
        ResponseEntity<SingleJobInfo> jobInfoResponseEntity =
                restTemplate.exchange(
                        restPrefix + "jobs/" + jobId,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        SingleJobInfo.class
                );

        if (!jobInfoResponseEntity.getStatusCode().equals(HttpStatus.OK)) {
            System.err.println("There was a problem getting the job info. Status code: "
                    + jobInfoResponseEntity.getStatusCode());
            System.exit(1);
        }

        if (!jobInfoResponseEntity.getBody().getJobStatus().equals("COMPLETE")) {
            System.err.println("The job did not complete within the expected time period.");
            System.exit(1);
        }


        // Get the job JSON output object

        // Job id is now a path var and required for this endpoint.
        ResponseEntity<JsonOutputObject> jobOutputResponseEntity =
                restTemplate.exchange(
                        restPrefix + "jobs/" + jobId + "/output/detection",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        JsonOutputObject.class
                );

        if (!jobOutputResponseEntity.getStatusCode().equals(HttpStatus.OK)) {
            System.err.println("There was a problem getting the job output. Status code: "
                    + jobOutputResponseEntity.getStatusCode());
            System.exit(1);
        }

        try {
            // Format output to look pretty
            String formattedJsonOutput =
                    mpfObjectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(jobOutputResponseEntity.getBody());
            System.out.println("JSON output:\n" + formattedJsonOutput);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

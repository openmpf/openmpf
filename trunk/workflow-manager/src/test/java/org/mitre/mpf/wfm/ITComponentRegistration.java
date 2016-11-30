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

package org.mitre.mpf.wfm;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.mpf.wfm.ui.Utils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ITComponentRegistration {

    private RestTemplate _restClient;
    private HttpHeaders _headers;


    private static final String _javaComponentName = "JavaHelloWorldComponent";
    private static final String _javaComponentPackage = _javaComponentName + ".tar.gz";

    private static final String _cppComponentName = "CplusplusHelloComponent";
    private static final String _cppComponentPackage = _cppComponentName + ".tar.gz";

    private static final String _customPipelinesComponentName = "CplusplusHelloCustomPipelinesComponent";
    private static final String _customPipelinesComponentPackage = _customPipelinesComponentName + ".tar.gz";


    @Before
    public void init() {
        _restClient = new RestTemplate();
        _headers = new HttpHeaders();
        _headers.set("Authorization", "Basic YWRtaW46bXBmYWRtCg");
        cleanUp();
    }

    @After
    public void cleanUp() {
        String[] componentPackages = { _javaComponentPackage, _cppComponentPackage, _customPipelinesComponentPackage };
        for (String packageName :  componentPackages) {
            try {
                removePackage(packageName);
            }
            catch (HttpClientErrorException ex) {
                if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                    throw ex;
                }
            }
        }
    }



    @Test
    public void canRegisterAndUnregisterJavaComponent() {
        canRegisterAndUnRegisterComponent(_javaComponentName, _javaComponentPackage);
    }

    @Test
    public void canRegisterAndUnregisterCppComponent() {
        canRegisterAndUnRegisterComponent(_cppComponentName, _cppComponentPackage);
    }

    @Test
    public void canRegisterAndUnregisterCustomPipelinesComponent() {
        canRegisterAndUnRegisterComponent(_customPipelinesComponentName, _customPipelinesComponentPackage);
    }


    private void canRegisterAndUnRegisterComponent(String componentName, String componentPackage) {
        uploadFile(componentPackage);

        ResponseEntity<String> registerResponse = _restClient.exchange(
                getUrl(String.format("components/%s/register", componentPackage)),
                HttpMethod.POST,
                new HttpEntity<>(_headers),
                String.class);
        assertOk(registerResponse);

        // Get it to verify added
        assertOk(getComponent(componentPackage));

        // remove
        assertEquals(HttpStatus.NO_CONTENT, removeComponent(componentName).getStatusCode());

        // Get it to verify removed
        try {
            getComponent(componentPackage);
            fail("Failed to remove component");
        }
        catch (HttpClientErrorException ex) {
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

    }


    private static String getUrl(String path) {
        return Utils.BASE_URL + "/workflow-manager/rest/" + path;
    }


    private void uploadFile(String path) {
        LinkedMultiValueMap<String, Object> fileEntity = getFileEntity(path);
        HttpEntity<?> httpEntity = new HttpEntity<>(fileEntity, _headers);
        ResponseEntity<String> result = _restClient.exchange(
                getUrl("components"),
                HttpMethod.POST,
                httpEntity,
                String.class);
        assertOk(result);
    }

    private ResponseEntity<String> getComponent(String componentPackage) {
        return _restClient.exchange(
                getUrl(String.format("components/%s/", componentPackage)),
                HttpMethod.GET,
                new HttpEntity<>(_headers),
                String.class
        );

    }

    private ResponseEntity<String> removeComponent(String component) {
        return _restClient.exchange(
                getUrl(String.format("components/%s", component)),
                HttpMethod.DELETE,
                new HttpEntity<>(_headers),
                String.class
        );
    }

    private ResponseEntity<String> removePackage(String packageName) {
        return _restClient.exchange(
                getUrl(String.format("components/packages/%s/", packageName)),
                HttpMethod.DELETE,
                new HttpEntity<>(_headers),
                String.class
        );

    }


    private static LinkedMultiValueMap<String, Object> getFileEntity(String path)  {
        Resource componentFile = new ClassPathResource(path);
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.parseMediaType("application/gzip"));
        HttpEntity<Resource> componentEntity = new HttpEntity<>(componentFile, formHeaders);

        LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add("file", componentEntity);
        return map;
    }


    private static void assertOk(ResponseEntity<?> entity) {
        assertEquals(HttpStatus.OK, entity.getStatusCode());
    }
}

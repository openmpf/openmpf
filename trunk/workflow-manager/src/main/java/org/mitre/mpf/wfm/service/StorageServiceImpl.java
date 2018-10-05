/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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


package org.mitre.mpf.wfm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;


@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

    private final PropertiesUtil _propertiesUtil;

    private final ObjectMapper _objectMapper;

    private final CustomNginxStorageBackend _nginxStorageBackend;


    @Inject
    StorageServiceImpl(
            PropertiesUtil propertiesUtil,
            ObjectMapper objectMapper,
            CustomNginxStorageBackend nginxStorageBackend) {
        _propertiesUtil = propertiesUtil;
        _objectMapper = objectMapper;
        _nginxStorageBackend = nginxStorageBackend;
    }


    @Override
    public String store(JsonOutputObject outputObject) throws IOException {
        StorageBackend.Type httpStorageType = _propertiesUtil.getHttpObjectStorageType();
        try {
            if (httpStorageType == StorageBackend.Type.CUSTOM_NGINX) {
                return _nginxStorageBackend.storeAsJson(outputObject);
            }
            if (httpStorageType != StorageBackend.Type.NONE) {
                log.warn("Encountered unexpected storage service type: {}. Output object for job {} will be stored locally.",
                         httpStorageType, outputObject.getJobId());
            }
        }
        catch (StorageException e) {
            log.warn(String.format("Failed to store output object for job id %s. It will be stored locally instead.",
                                   outputObject.getJobId()), e);
        }
        return storeLocally(outputObject);
    }


    private String storeLocally(JsonOutputObject outputObject) throws IOException {
        File outputFile = _propertiesUtil.createDetectionOutputObjectFile(outputObject.getJobId());
        _objectMapper.writeValue(outputFile, outputObject);
        return outputFile.getAbsoluteFile().toURI().toString();
    }
}

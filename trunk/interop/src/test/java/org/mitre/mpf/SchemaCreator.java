/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import org.mitre.mpf.interop.*;

import java.io.File;

public class SchemaCreator {
    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper);
        for(Class clazz : new Class[] {
                // JsonAction.class,
                JsonCallbackBody.class,
                // JsonDetectionOutputObject.class,
                // JsonDetectionProcessingError.class,
                // JsonMarkupOutputObject.class,
                // JsonMediaOutputObject.class,
                JsonOutputObject.class,
                // JsonPipeline.class,
                // JsonTask.class,
                // JsonTrackOutputObject.class
                JsonSegmentSummaryReport.class,
                JsonHealthReportCollection.class
        }) {
            JsonSchema jsonSchema = jsonSchemaGenerator.generateSchema(clazz);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(clazz.getSimpleName()+".schema.json"), jsonSchema);
        }
    }
}


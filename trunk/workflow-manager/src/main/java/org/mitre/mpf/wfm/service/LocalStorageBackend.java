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


package org.mitre.mpf.wfm.service;

import com.google.common.collect.Table;
import org.apache.commons.lang3.mutable.Mutable;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.entities.persistent.BatchJob;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;
import org.mitre.mpf.wfm.data.entities.persistent.Media;

import java.io.IOException;
import java.net.URI;

public interface LocalStorageBackend extends StorageBackend {

    @Override
    public boolean canStore(JsonOutputObject outputObject);

    @Override
    public URI store(JsonOutputObject outputObject, Mutable<String> outputSha) throws IOException;

    @Override
    public boolean canStore(MarkupResult markupResult);

    @Override
    public void store(MarkupResult markupResult);

    @Override
    public boolean canStore(ArtifactExtractionRequest request);

    @Override
    public Table<Integer, Integer, URI> storeArtifacts(ArtifactExtractionRequest request) throws IOException;

    @Override
    public boolean canStoreDerivativeMedia(BatchJob job, long parentMediaId);

    @Override
    public void storeDerivativeMedia(BatchJob job, Media media) throws IOException;
}

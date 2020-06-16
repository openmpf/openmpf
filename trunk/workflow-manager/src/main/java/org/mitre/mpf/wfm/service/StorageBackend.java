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


package org.mitre.mpf.wfm.service;

import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.wfm.camel.operations.detection.artifactextraction.ArtifactExtractionRequest;
import org.mitre.mpf.wfm.data.entities.persistent.MarkupResult;

import com.google.common.collect.Table;

import java.io.IOException;
import java.net.URI;

public interface StorageBackend {

    public boolean canStore(JsonOutputObject outputObject) throws StorageException;
    public URI store(JsonOutputObject outputObject) throws StorageException, IOException;


    public boolean canStore(ArtifactExtractionRequest request) throws StorageException;
    public Table<Integer, Integer, URI> storeArtifacts(ArtifactExtractionRequest request) throws IOException, StorageException;

    public boolean canStore(MarkupResult markupResult) throws StorageException;
    public void store(MarkupResult markupResult) throws IOException, StorageException;
}

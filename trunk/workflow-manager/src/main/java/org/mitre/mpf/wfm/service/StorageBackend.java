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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public interface StorageBackend {

    public String store(URI serviceUri, InputStream content) throws StorageException;

    public String storeAsJson(URI serviceUri, Object content) throws StorageException;

    public default String store(URI serviceUri, Path path) throws IOException, StorageException {
        try (InputStream is = Files.newInputStream(path)) {
            return store(serviceUri, is);
        }
    }

    public default String store(URI serviceUri, URL content) throws IOException, StorageException {
        try (InputStream is = content.openStream()) {
            return store(serviceUri, is);
        }
    }


    public Type getType();

    public enum Type {
        NONE,
        CUSTOM_NGINX
    }
}

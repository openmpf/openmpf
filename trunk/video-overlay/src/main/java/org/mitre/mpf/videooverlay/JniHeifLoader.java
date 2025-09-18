/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.videooverlay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JniHeifLoader {
    private static final Logger log = LoggerFactory.getLogger(JniHeifLoader.class);

    private static boolean _isLoaded;

    static {
        log.info("Loading Heif JNI libraries...");
        try {
            System.loadLibrary("mpfheifjni");
            _isLoaded = true;
        }
        catch (UnsatisfiedLinkError ex) {
            log.warn("System.loadLibrary() failed due to: {}", ex.getMessage());
            String path = System.getenv("MPF_HOME") + "/lib/" + "libmpfheifjni.so";
            log.warn("Trying to load library using full path: {}", path);
            System.load(path);
            _isLoaded = true;
        }
    }

    private JniHeifLoader() {
    }

    /**
     * This method exists to force the static initializer to run when a class with native methods
     * is first used. This should always return true.
     * @return true
     */
    public static boolean ensureLoaded() {
        return _isLoaded;
    }
}
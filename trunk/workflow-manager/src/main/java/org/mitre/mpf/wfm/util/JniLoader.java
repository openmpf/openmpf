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

package org.mitre.mpf.wfm.util;

import org.mitre.mpf.wfm.enums.EnvVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This class loads any JNI libraries needed by the WFM. Classes which rely on these JNI libraries
 * should be marked appropriately using the {@link org.springframework.context.annotation.DependsOn}
 * annotation.
 */
@Component(JniLoader.REF)
@Scope("prototype")
public class JniLoader {
    public static final String REF = "jniLoader";
    private static final Logger log = LoggerFactory.getLogger(JniLoader.class);

    private static boolean _isLoaded;

    static {
        log.info("Loading JNI libraries...");
        try {
            System.loadLibrary("mpfopencvjni");
            _isLoaded = true;
        }
        catch (UnsatisfiedLinkError ex) {
            log.warn("System.loadLibrary() failed due to: {}", ex.getMessage());
            String libFullPath = System.getenv(EnvVar.MPF_HOME) + "/lib/libmpfopencvjni.so";
            log.warn("Trying full path to library: {}", libFullPath);
            System.load(libFullPath);
            _isLoaded = true;
        }
    }

    /**
     * This method exists to force the static initializer run when running unit tests. This should always return true.
     * @return true
     */
    public static boolean isLoaded() {
        return _isLoaded;
    }
}

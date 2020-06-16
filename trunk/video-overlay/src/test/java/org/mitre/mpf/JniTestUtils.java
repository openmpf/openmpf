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


package org.mitre.mpf;

import org.junit.Assert;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class JniTestUtils {

    private static final boolean _jniLibsLoaded;

    static {
        String mpfHome = System.getenv("MPF_HOME");
        List<String> libNames = Arrays.asList("libmpfDetectionComponentApi.so", "libmpfopencvjni.so");
        for (String libName : libNames) {
            try {
                String libraryFile = new File("install/lib", libName).getAbsolutePath();
                System.load(libraryFile);
            }
            catch (UnsatisfiedLinkError e) {
                if (mpfHome == null) {
                    throw e;
                }
                String libraryFile = new File(mpfHome, "lib/" + libName).getAbsolutePath();
                System.load(libraryFile);
            }
        }
        _jniLibsLoaded = true;
    }


    private JniTestUtils() {

    }

    /**
     * This method exists to force the static initializer run when running unit tests. This should always return true.
     * @return true
     */
    public static boolean jniLibsLoaded() {
        return _jniLibsLoaded;
    }


    public static URI getFileResource(String resourcePath) {
        try {
            URL resource = JniTestUtils.class.getClassLoader().getResource(resourcePath);
            Assert.assertNotNull(resourcePath);
            return resource.toURI();
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}

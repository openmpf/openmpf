/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

package org.mitre.mpf.framecounter;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.mpf.JniTestUtils;

import java.io.File;
import java.io.IOException;

public class TestFrameCounter {
    static {
        Assert.assertTrue(JniTestUtils.jniLibsLoaded());
    }

    @Test
    public void testFrameCounterOnVideo() {
        countFrames("samples/five-second-marathon-clip-numbered.mp4", false, 2000);
    }

    @Test
    public void testFrameCounterOnGif() {
        countFrames("samples/face-morphing.gif", true, 29);
    }


    private static void countFrames(String filePath, boolean bruteForce, int expectedCount) {
        try {
            File sourceFile = new File(JniTestUtils.getFileResource(filePath));

            if (!sourceFile.exists()) {
                throw new IOException(String.format("File not found %s.", sourceFile.getAbsolutePath()));
            }

            FrameCounter counter = new FrameCounter(sourceFile);
            Assert.assertEquals("Did not count the expected number of frames.", expectedCount, counter.count(bruteForce));
        } catch (IOException ioe) {
            Assert.fail(String.format("Encountered an exception when none was expected. %s", ioe));
        }
    }
}

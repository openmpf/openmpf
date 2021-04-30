package org.mitre.mpf.wfm.util;
/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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


import org.junit.Test;
import org.mitre.mpf.test.TestUtil;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestFrameRateTypeDetector {

    @Test
    public void testSamples() throws IOException {
        assertTrue(hasConstantFrameRate("/samples/five-second-marathon-clip.mkv"));
        assertTrue(hasConstantFrameRate("/samples/video_01.mp4"));
        assertTrue(hasConstantFrameRate("/samples/video_02.mp4"));

        assertFalse(hasConstantFrameRate("/samples/bbb24p_00_short.ts"));
        assertFalse(hasConstantFrameRate("/samples/new_face_video.avi"));
        assertFalse(hasConstantFrameRate("/samples/video_01_invalid.mp4"));
        assertFalse(hasConstantFrameRate("/samples/video_02_audio_only.mp4"));
        assertFalse(hasConstantFrameRate("/samples/STRUCK_Test_720p.mp4"));
    }


    private static boolean hasConstantFrameRate(String path) throws IOException {
        return FrameRateTypeDetector.hasConstantFrameRate(TestUtil.findFilePath(path));
    }
}

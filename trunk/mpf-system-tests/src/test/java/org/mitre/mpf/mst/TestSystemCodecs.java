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

package org.mitre.mpf.mst;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.mpf.interop.JsonOutputObject;
import org.mitre.mpf.rest.api.JobCreationMediaData;

import java.util.Collection;
import java.util.List;

public class TestSystemCodecs extends TestSystemWithDefaultConfig {

    private void runTest(String pipelineName, String testMediaPath) {
        List<JobCreationMediaData> media = toMediaObjectList(ioUtils.findFile(testMediaPath));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTrackTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runSpeechSphinxDetectAudioAmr() {
        runTest("SPHINX SPEECH DETECTION PIPELINE", "/samples/speech/amrnb.amr");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoH264Aac() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/h264_aac.mp4");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoH264Mp3() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/h264_mp3.mp4");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoH265Aac() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/h265_aac.mp4");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoMPv4Aac() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/mp4v_aac.mp4");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoXvidAac() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/xvid_aac.avi");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoTheoraOpus() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/theora_opus.ogg");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoTheoraVorbis() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/theora_vorbis.ogg");
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectVideoVp8Opus() {
        runTest("OCV FACE DETECTION PIPELINE", "/samples/face/vp8_opus.webm");
    }

}

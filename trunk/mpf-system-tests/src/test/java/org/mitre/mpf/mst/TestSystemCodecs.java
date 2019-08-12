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

package org.mitre.mpf.mst;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.mpf.interop.JsonMediaInputObject;
import org.mitre.mpf.interop.JsonOutputObject;

import java.util.Collection;
import java.util.List;

public class TestSystemCodecs extends TestSystemWithDefaultConfig {

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageAMR() {
        String pipelineName = "SPHINX SPEECH DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/speech/amrnb.amr"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageH264AAC() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/h264_aac.mp4"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageH264MP3() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/h264_mp3.mp4"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageH265AAC() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/h265_aac.mp4"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageXvidAAC() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/xvid_aac.mp4"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageTheoraOpus() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/theora_opus.ogg"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageTheoraVorbis() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/theora_vorbis.ogg"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

    @Test(timeout = 5 * MINUTES)
    public void runFaceOcvDetectImageVp8Opus() {
        String pipelineName = "OCV FACE DETECTION PIPELINE";
        addPipeline(pipelineName);

        List<JsonMediaInputObject> media = toMediaObjectList(ioUtils.findFile("/samples/face/vp8_opus.webm"));

        long jobId = runPipelineOnMedia(pipelineName, media);
        JsonOutputObject outputObject = getJobOutputObject(jobId);

        boolean detectionFound = outputObject.getMedia()
                .stream()
                .flatMap(m -> m.getTypes().values().stream())
                .flatMap(Collection::stream)
                .flatMap(a -> a.getTracks().stream())
                .flatMap(t -> t.getDetections().stream())
                .findAny()
                .isPresent();

        if (!detectionFound && outputObject.getJobErrors() != null) {
            System.out.println(outputObject.getStatus());
            System.err.println(outputObject.getJobErrors());
            System.out.println(outputObject.getMedia());
        }
        Assert.assertTrue(detectionFound);
    }

}
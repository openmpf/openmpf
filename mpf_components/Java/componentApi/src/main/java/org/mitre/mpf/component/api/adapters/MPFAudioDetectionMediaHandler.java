/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

package org.mitre.mpf.component.api.adapters;

import org.mitre.mpf.audioVideo.util.MPFAudioAttributes;
import org.mitre.mpf.audioVideo.util.MPFEncoder;
import org.mitre.mpf.audioVideo.util.MPFEncodingAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class MPFAudioDetectionMediaHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MPFAudioDetectionMediaHandler.class);
    private static MPFAudioDetectionMediaHandler instance;
    private MPFEncoder encoder;

    private MPFAudioDetectionMediaHandler() {
        encoder = new MPFEncoder();
    }

    public void ripAudio(File input, File output, int startTime, int stopTime)
        throws IllegalArgumentException, IOException {

        final int channels = 1;
        final int samplingRate = 16000;
        final int highpassCutoffHz = 200;
        final int lowpassCutoffHz = 3000;
        final String codec = "pcm_s16le";
        final String format = "wav";
        
        MPFAudioAttributes audioAttr = new MPFAudioAttributes();
        MPFEncodingAttributes encodingAttr = new MPFEncodingAttributes();

        audioAttr.setChannels(channels);
        audioAttr.setSamplingRate(samplingRate);
        audioAttr.setCodec(codec);
        audioAttr.setHighpassCutoffFrequency(highpassCutoffHz);
        audioAttr.setLowpassCutoffFrequency(lowpassCutoffHz);

        encodingAttr.setFormat(format);
        encodingAttr.setAudioAttributes(audioAttr);

        // convert milliseconds to seconds
        float offset = (float) startTime / 1000;
        float duration = ((float) (stopTime - startTime)) / 1000;

        encodingAttr.setOffset(offset);
        encodingAttr.setDuration(duration);


        LOG.debug("encoding attr: {}", encodingAttr.toString());

        // invoke ffmpeg to transcode audio from video segment
        encoder.transcodeWithFiltering(input, output, encodingAttr);
    }


    public static MPFAudioDetectionMediaHandler getInstance() {
        if (instance == null) {
            instance = new MPFAudioDetectionMediaHandler();
        }
        return instance;
    }
}

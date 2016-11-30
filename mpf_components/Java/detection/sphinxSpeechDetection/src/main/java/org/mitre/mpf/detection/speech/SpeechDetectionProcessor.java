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

package org.mitre.mpf.detection.speech;

import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.SpeechResult;
import edu.cmu.sphinx.api.StreamSpeechRecognizer;
import edu.cmu.sphinx.result.WordResult;
import edu.cmu.sphinx.util.TimeFrame;
import org.mitre.mpf.component.api.detection.MPFAudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.*;
import java.util.*;

public class SpeechDetectionProcessor {
	
    private static final Logger LOG = LoggerFactory.getLogger(SpeechDetectionProcessor.class);

    private final Configuration sphinxConfiguration;

    public SpeechDetectionProcessor() {
        try {
            Properties properties = PropertiesLoaderUtils.loadAllProperties("speechExtraction.properties");
            sphinxConfiguration = new Configuration();
            // load model from jar
            sphinxConfiguration.setAcousticModelPath(properties.getProperty("sphinx.config.acoustic.model.path"));
            sphinxConfiguration.setDictionaryPath(properties.getProperty("sphinx.config.dict.path"));
            sphinxConfiguration.setLanguageModelPath(properties.getProperty("sphinx.config.lang.model.path"));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to load properties.", e);
        }
    }


    public void processAudio(File target, int startTime, Collection<MPFAudioTrack> tracks) throws IOException {

        LOG.debug("Attempting to detect and transcribe speech in file " + target.getAbsoluteFile());

        StreamSpeechRecognizer sphinxRecognizer = new StreamSpeechRecognizer(sphinxConfiguration);

        try (InputStream stream = new FileInputStream(target)) {
            // perform simple speech recognition with generic model
            sphinxRecognizer.startRecognition(stream);
            SpeechResult sphinxResult;

            while ((sphinxResult = sphinxRecognizer.getResult()) != null) {
                tracks.add(convert(sphinxResult, startTime));
            }
        } finally {
            sphinxRecognizer.stopRecognition();
        }
    }


    private static MPFAudioTrack convert(SpeechResult sphinxResult, int timeOffset) {
        String hypothesis = sphinxResult.getHypothesis();
        LOG.info("Sphinx transcription hypothesis: " + hypothesis);

        List<WordResult> wordResults = sphinxResult.getWords();
        long sphinxResultStartTime = wordResults.isEmpty()
                ? 0
                : Long.MAX_VALUE;
        long sphinxResultStopTime = 0;

        for (WordResult wordResult : wordResults) {
            if (!wordResult.isFiller()) {
                TimeFrame timeFrame = wordResult.getTimeFrame();
                sphinxResultStartTime = Math.min(sphinxResultStartTime, timeFrame.getStart());
                sphinxResultStopTime = Math.max(sphinxResultStopTime, timeFrame.getEnd());
            }
        }

        logResults(wordResults);

        // the StreamSpeechRecognizer is operating on a slice of the audio file, so the true start and stop times
        // for the track needs to account for the slice offset since the beginning of the whole file
        int trackStartTime = (int) sphinxResultStartTime + timeOffset;
        int trackStopTime  = (int) sphinxResultStopTime + timeOffset;

        Map<String,String> properties = new HashMap<>();
        properties.put("TRANSCRIPTION", hypothesis);
        return new MPFAudioTrack(trackStartTime, trackStopTime, -1, properties);
    }



    private static void logResults(Collection<WordResult> wordResults) {
        if (wordResults.isEmpty()) {
            LOG.info("No recognized words found.");
            return;
        }

        LOG.info("List of recognized words and their times:");
        for (WordResult wordResult : wordResults) {
            String suffix = wordResult.isFiller() ? " (filler)" : "";
            LOG.info(wordResult + suffix);
        }
    }
}

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

import org.apache.commons.io.FilenameUtils;
import org.mitre.mpf.component.api.adapters.MPFAudioAndVideoDetectionComponentAdapter;
import org.mitre.mpf.component.api.adapters.MPFAudioDetectionMediaHandler;
import org.mitre.mpf.component.api.detection.MPFAudioTrack;
import org.mitre.mpf.component.api.detection.MPFDetectionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.apache.commons.io.FileUtils.deleteQuietly;

public class SpeechDetectionComponent extends MPFAudioAndVideoDetectionComponentAdapter {
	
    private static final Logger LOG = LoggerFactory.getLogger(SpeechDetectionComponent.class);
    private final MPFAudioDetectionMediaHandler mediaHandler;
    private final SpeechDetectionProcessor speechProcessor;

    public SpeechDetectionComponent() {
        mediaHandler = MPFAudioDetectionMediaHandler.getInstance();
        speechProcessor = new SpeechDetectionProcessor();
    }

    @Override
    public String getDetectionType() {
        return "SPEECH";
    }

    @Override
    public MPFDetectionError getDetectionsFromAudio(
        final String jobName,
        final int startTime,
        final int stopTime,
        final String dataUri,
        final Map <String, String> algorithmProperties,
        final Map <String, String> mediaProperties,
        List<MPFAudioTrack> tracks
    ) {

        LOG.debug("jobName = {}, startTime = {}, stopTime = {}, dataUri = {}, size of algorithmProperties = {}, size of mediaProperties = {}",
                jobName, startTime, stopTime, dataUri, algorithmProperties.size(), mediaProperties.size());

        // TODO: Do away with the need to handle the startTime=0, stopTime=-1 convention (which means to process the entire file).
        // There should not be any special semantics associated with the startTime and stopTime.

        // determine actual start and stop times

        int newStartTime = startTime;
        int newStopTime = stopTime;

        if (stopTime == -1) {
            Integer duration;
            try {
                if (mediaProperties.get("DURATION")==null) {
                    LOG.error("Could not obtain duration");
                    return MPFDetectionError.MPF_MISSING_PROPERTY;
                }
                duration = Integer.valueOf(mediaProperties.get("DURATION"));
            } catch (NumberFormatException ex) {
                LOG.error("Could not obtain duration", ex);
                return MPFDetectionError.MPF_PROPERTY_IS_NOT_INT;
            }
            LOG.debug("For file {}, duration = {} milliseconds", dataUri, duration);

            newStopTime = duration;
        }

        // create a name for temporary audio file to be created from video
        // always create the file in tmp directory in case source is server media file in read-only directory
        String directory = System.getenv("MPF_HOME");
        if (directory == null) {
            directory = "/tmp/";
            LOG.warn("Value for environment MPF_HOME not available - storing interim files in {}", directory);
        } else {
            directory += "/tmp/";
        }
        LOG.debug("directory = {}", directory);

        String basename = FilenameUtils.getBaseName(dataUri);
        File target = new File(directory, String.format("%s_%s_%s.wav", basename, newStartTime, newStopTime));

        LOG.debug("Storing temporary audio for analysis in {}", target);

        try {
            // separate the audio from input media in the proper format and save it in a temporary file
            File source = new File(dataUri);
            try {
                mediaHandler.ripAudio(source, target, newStartTime, newStopTime);

            // TODO: Consider handling these exceptions in different ways.
            } catch (IllegalArgumentException | IOException e) {
                LOG.error(String.format(
                        "Failed to rip the audio from '%s' (startTime = %s, stopTime = %s) to '%s' due to an Exception.",
                            source, newStartTime, newStopTime, target),
                        e);
                return MPFDetectionError.MPF_COULD_NOT_READ_DATAFILE;
            }

            // detect and transcribe any spoken English in the audio

            try {
                speechProcessor.processAudio(target, newStartTime, tracks);
            } catch (IOException e) {
                LOG.error("Could not initialize Sphinx StreamSpeechRecognizer.", e);
                return MPFDetectionError.MPF_OTHER_DETECTION_ERROR_TYPE;
            } catch (NullPointerException e) {
                // if Sphinx doesn't like an input file it will sometimes generate a NPE and just stop
                // this needs to be caught so that processing isn't halted, but there is
                // nothing specific to be done about it since its cause is internal to Sphinx
                LOG.error(String.format(
                        "NullPointerException encountered during audio processing of file %s",
                        target.getAbsoluteFile()), e);
                return MPFDetectionError.MPF_COULD_NOT_READ_DATAFILE;
            }
        }
        catch(Exception e) {
            LOG.error("Unhandled exception processing file {}. {}", target.getAbsoluteFile(), e);
            return MPFDetectionError.MPF_DETECTION_FAILED;
        }
        finally {
            // delete the temporary audio file without throwing any exceptions
            // TODO: may want to keep file around in case it is used again, and clear out files in the temp directory when the system is shut down
            deleteQuietly(target);
        }

        LOG.debug("target = {}, startTime = {}, stopTime = {}, tracks size = {}",
                target, newStartTime, newStopTime, tracks.size());
        
        return MPFDetectionError.MPF_DETECTION_SUCCESS;
    }
}

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

import org.mitre.mpf.component.api.detection.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class MPFAudioAndVideoDetectionComponentAdapter extends MPFDetectionComponentBase {

    private static final Logger LOG = LoggerFactory.getLogger(MPFAudioAndVideoDetectionComponentAdapter.class);

    public MPFDetectionError getDetectionsFromVideo(
        final String jobName,
        final int startFrame,
        final int stopFrame,
        final String dataUri,
        final Map <String, String> algorithmProperties,
        final Map <String, String> mediaProperties,
        List<MPFVideoTrack> tracks
    ) {
        LOG.debug("jobName = {}, startFrame = {}, stopFrame = {}, dataUri = {}, size of algorithmProperties = {}, size of mediaProperties = {}",
                jobName, startFrame, stopFrame, dataUri, algorithmProperties.size(), mediaProperties.size());

        Float fps;
        try {
            if (mediaProperties.get("FPS") == null) {
                LOG.error("Could not obtain video frame rate");
                return MPFDetectionError.MPF_MISSING_PROPERTY;
            }
            fps = Float.valueOf(mediaProperties.get("FPS"));
        } catch (NumberFormatException ex) {
            LOG.error("Could not obtain video frame rate");
            return MPFDetectionError.MPF_PROPERTY_IS_NOT_FLOAT;
        }

        Integer duration;
        try {
            if (mediaProperties.get("DURATION") == null) {
                LOG.error("Could not obtain duration");
                return MPFDetectionError.MPF_MISSING_PROPERTY;
            }
            duration = Integer.valueOf(mediaProperties.get("DURATION"));
        }  catch (NumberFormatException ex) {
            LOG.error("Could not obtain duration");
            return MPFDetectionError.MPF_PROPERTY_IS_NOT_INT;
        }

        // calculate the final frame
        // int process_to_end = Integer.MAX_VALUE; // TODO: use actual stop frame / stop time
        int finalFrame = (int) (fps* duration / 1000); // TODO: this needs to be tested


        // TODO: Do away with the need to handle the startTime=0, stopTime=-1 convention (which means to process the entire file).
        // There should not be any special semantics associated with the startTime and stopTime.

        // determine actual start and stop frames

        int newStartFrame = 0;
        int newStopFrame = 0;

        if (startFrame < 0) {
            // process the entire video
            newStartFrame = 0;
            newStopFrame = finalFrame;
        }
        else if (stopFrame < 0) {
            if (startFrame < 0) {
                LOG.error("Start frame and stop frame are both < 0");
                return MPFDetectionError.MPF_BAD_FRAME_SIZE;  // Can't have both less than 0
            }
            // process from the start frame to the end
            newStartFrame = startFrame;
            newStopFrame = finalFrame;
        }
        else { // process from start frame to stop frame
            newStartFrame = startFrame;
            newStopFrame = stopFrame;
        }

        if (newStopFrame <= newStartFrame) {
            LOG.error("Stop frame is less than the start frame");
            return MPFDetectionError.MPF_INVALID_STOP_FRAME;
        }

        // calculate start and stop times in milliseconds based on fps
        int startTime = (int) (newStartFrame / fps * 1000);
        int stopTime  = (int) (newStopFrame  / fps * 1000);


        // get audio tracks

        List<MPFAudioTrack> audioTracks = new LinkedList<MPFAudioTrack>();
        MPFDetectionError err = getDetectionsFromAudio(jobName, startTime, stopTime, dataUri, algorithmProperties, mediaProperties, audioTracks);
        LOG.info("Results: {}", audioTracks.size());

        // convert audio tracks to video tracks

        if (MPFDetectionError.MPF_DETECTION_SUCCESS == err) {
            for (MPFAudioTrack audioTrack : audioTracks) {
                LOG.debug("Track start time: {}, track stop time: {}", audioTrack.getStartTime(), audioTrack.getStopTime());
                // convert milliseconds to seconds
                int trackStartFrame = (int) Math.floor(fps * audioTrack.getStartTime() / 1000.0);
                int trackStopFrame  = (int) Math.ceil(fps * audioTrack.getStopTime()  / 1000.0);

                MPFVideoTrack videoTrack = new MPFVideoTrack(trackStartFrame, trackStopFrame,
                        new HashMap<Integer, MPFImageLocation>(), audioTrack.getConfidence(), audioTrack.getDetectionProperties());

                videoTrack.getFrameLocations().put(trackStartFrame,new MPFImageLocation(0,0,0,0,1,audioTrack.getDetectionProperties()));

                tracks.add(videoTrack);
                LOG.info("Added video track: startFrame {}, stopFrame {}, location count {}", trackStartFrame, trackStopFrame, videoTrack.getFrameLocations().size());
            }
        }

        return err;
    }

    public abstract MPFDetectionError getDetectionsFromAudio(
        final String jobName,
        final int startTime,
        final int stopTime,
        final String dataUri,
        final Map <String, String> algorithm_properties,
        final Map <String, String> mediaProperties,
        List<MPFAudioTrack> tracks
    );

    public MPFDetectionError getDetectionsFromImage(
        final String jobName,
        final String dataUri,
        final Map <String, String> algorithm_properties,
        final Map <String, String> mediaProperties,
        List<MPFImageLocation> locations
    ) {
        return MPFDetectionError.MPF_UNSUPPORTED_DATA_TYPE;
    }

    public boolean supports(MPFDataType dataType) {
        return (MPFDataType.AUDIO == dataType) || (MPFDataType.VIDEO == dataType);
    }

    public abstract String getDetectionType();
}

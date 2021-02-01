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

package org.mitre.mpf.detection.test;

import org.mitre.mpf.component.api.*;
import org.mitre.mpf.component.api.detection.*;

import java.util.*;

public class JavaTestDetectionComponent extends MPFDetectionComponentBase {

    @Override
    public List<MPFVideoTrack> getDetections(MPFVideoJob job) {
        MPFImageLocation loc = new MPFImageLocation(0, 0, 0, 0, -1, generateDetectionProperties());
        List<MPFVideoTrack> tracks = new LinkedList<>();
        Map<Integer, MPFImageLocation> locations = new HashMap<>();
        locations.put(0, loc);
        MPFVideoTrack track = new MPFVideoTrack(job.getStartFrame(), job.getStopFrame(), locations, -1, Collections.emptyMap());
        tracks.add(track);
        return tracks;
    }

    @Override
    public List<MPFAudioTrack> getDetections(MPFAudioJob job) {
        List<MPFAudioTrack> tracks = new LinkedList<>();
        MPFAudioTrack track = new MPFAudioTrack(0, 0, -1, generateDetectionProperties());
        tracks.add(track);
        return tracks;
    }

    @Override
    public List<MPFImageLocation> getDetections(MPFImageJob job) {
        List<MPFImageLocation> locations = new LinkedList<>();
        MPFImageLocation loc = new MPFImageLocation(0, 0, 0, 0, -1, generateDetectionProperties());
        locations.add(loc);
        return locations;
    }

    @Override
    public List<MPFGenericTrack> getDetections(MPFGenericJob job) {
        List<MPFGenericTrack> tracks = new LinkedList<>();
        MPFGenericTrack track = new MPFGenericTrack(0, generateDetectionProperties());
        tracks.add(track);
        return tracks;
    }

    private Map<String,String> generateDetectionProperties() {
        Map<String,String> props = new HashMap<>();
        props.put("METADATA", "Hello World!");
        return props;
    }

    @Override
    public boolean supports(MPFDataType dataType) {
        return MPFDataType.IMAGE == dataType
                || MPFDataType.VIDEO == dataType
                || MPFDataType.AUDIO == dataType
                || MPFDataType.UNKNOWN == dataType;
    }

    @Override
    public String getDetectionType() {
        return "HELLO";
    }

    @Override
    public MPFComponentType getComponentType() {
        return MPFComponentType.DETECTION;
    }

}

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

package org.mitre.mpf.component.api.detection;

import org.mitre.mpf.component.api.MPFComponentBase;
import org.mitre.mpf.component.api.MPFComponentType;

import java.util.List;
import java.util.Map;

public abstract class MPFDetectionComponentBase extends MPFComponentBase implements MPFDetectionComponentInterface {

    public abstract MPFDetectionError getDetectionsFromVideo(
        final String jobName,
        final int startFrame,
        final int stopFrame,
        final String dataUri,
        final Map <String, String> algorithmProperties,
        final Map <String, String> mediaProperties,
        List<MPFVideoTrack> tracks
    );

    public abstract MPFDetectionError getDetectionsFromAudio(
        final String jobName,
        final int startTime,
        final int stopTime,
        final String dataUri,
        final Map <String, String> algorithmProperties,
        final Map <String, String> mediaProperties,
        List<MPFAudioTrack> tracks
    );

    public abstract MPFDetectionError getDetectionsFromImage(
        final String jobName,
        final String dataUri,
        final Map <String, String> algorithmProperties,
        final Map <String, String> mediaProperties,
        List<MPFImageLocation> tracks
    );

    public abstract boolean supports(MPFDataType dataType);

    public abstract String getDetectionType();

    @Override
    public MPFComponentType getComponentType() {
        return MPFComponentType.DETECTION;
    }
}

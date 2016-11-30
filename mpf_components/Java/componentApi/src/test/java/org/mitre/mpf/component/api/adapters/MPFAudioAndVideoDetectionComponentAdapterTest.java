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

import junit.framework.Assert;
import junit.framework.TestCase;
import org.junit.Test;
import org.mitre.mpf.component.api.detection.MPFAudioTrack;
import org.mitre.mpf.component.api.detection.MPFDetectionError;
import org.mitre.mpf.component.api.detection.MPFVideoTrack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MPFAudioAndVideoDetectionComponentAdapterTest extends TestCase {

    @Test
    public void testGetDetectionsFromVideo() throws Exception {
        MPFAudioAndVideoDetectionComponentAdapter component = new TestInstanceMPFAudioAndVideoDetectionComponentAdapter();

        List<MPFVideoTrack> tracks = new LinkedList<>();
        HashMap<String,String> mediaProperties = new HashMap<>();
        mediaProperties.put("DURATION","2000");
        mediaProperties.put("FPS","20");
        component.getDetectionsFromVideo("TEST", 0,600,"test",new HashMap<>(),mediaProperties,tracks);
        Assert.assertEquals(2, tracks.size());
        MPFVideoTrack track1 = tracks.get(0);
        Assert.assertEquals(10,track1.getStartFrame());
        Assert.assertEquals(24,track1.getStopFrame());
        Assert.assertEquals(1,track1.getFrameLocations().size());
    }

    @Test
    public void testGetDetectionsFromVideoWithError() throws Exception {
        MPFAudioAndVideoDetectionComponentAdapter component = new TestInstanceMPFAudioAndVideoDetectionComponentAdapter();

        HashMap<String,String> mediaProperties = new HashMap<>();
        mediaProperties.put("DURATION","300");
        mediaProperties.put("FPS","20");
        List<MPFVideoTrack> tracks = new LinkedList<>();
        Assert.assertEquals(MPFDetectionError.MPF_DETECTION_FAILED, component.getDetectionsFromVideo("TEST",0, 600, "TESTERROR", new HashMap<>(), mediaProperties, tracks));
        Assert.assertEquals(0,tracks.size());
    }


    private class TestInstanceMPFAudioAndVideoDetectionComponentAdapter extends MPFAudioAndVideoDetectionComponentAdapter {

        @Override
        public MPFDetectionError getDetectionsFromAudio(String jobName, int startTime, int stopTime, String dataUri, Map<String, String> algorithm_properties, Map<String, String> mediaProperties, List<MPFAudioTrack> tracks) {

            tracks.add(new MPFAudioTrack(500,1200,1.0f,new HashMap<>()));
            tracks.add(new MPFAudioTrack(3000,5000,2.0f,new HashMap<>()));
            return (dataUri.equals("TESTERROR") ? MPFDetectionError.MPF_DETECTION_FAILED : MPFDetectionError.MPF_DETECTION_SUCCESS);
        }

        @Override
        public String getDetectionType() {
            return "TEST";
        }

    }

}
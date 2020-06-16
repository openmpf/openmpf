/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

package org.mitre.mpf.videooverlay;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestBoundingBoxMap {
    @Test
    public void testCreateBoundingBoxMap() {
        BoundingBoxMap map = new BoundingBoxMap();
        Assert.assertEquals("Map initialized with an incorrect size.", map.size(), 0);
    }

    @Test
    public void testToString() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.toString();
    }

    @Test
    public void testPutOnFrame() {
        BoundingBoxMap map = new BoundingBoxMap();

        BoundingBox boundingBox = new BoundingBox(25, 30, 100, 100, 0, false, 0xFF, 0, 0);
        BoundingBox boundingBoxCopy = new BoundingBox(25, 30, 100, 100, 0, false, 0xFF, 0, 0);

        map.putOnFrame(1, boundingBox);
        Assert.assertNotEquals("The value for map.get(1) must not be null.", map.get(1), null);
        Assert.assertEquals("The value for map.get(1) must contain 1 element.", map.get(1).size(), 1);
        Assert.assertEquals("The value for map.get(1).get(0) did not match the expected value.", map.get(1).get(0), boundingBoxCopy);
    }

    @Test
    public void testPutOnFrames() {
        BoundingBoxMap map = new BoundingBoxMap();

        BoundingBox boundingBox = new BoundingBox(25, 30, 100, 100, 0, false, 0xFF, 0, 0);
        BoundingBox boundingBoxCopy = new BoundingBox(25, 30, 100, 100, 0, false, 0xFF, 0, 0);

        map.putOnFrames(5, 10, boundingBox);

        Assert.assertEquals("The map must contain 5 elements.", 10 - 5 + 1, map.size());
        for(int i = 5; i <= 10; i++) {
            Assert.assertNotEquals(String.format("The value for map.get(%d) must not be null.", i), map.get(i), null);
            Assert.assertEquals(String.format("The value for map.get(%d) must contain 1 element.", i), 1, map.get(i).size());
            Assert.assertEquals(String.format("The value for map.get(%d).get(0) did not match the expected value.", i), boundingBoxCopy, map.get(i).get(0));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutOnFrameNegativeFrame() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrame(-1, new BoundingBox(0, 0, 0, 0, 0, false, 0, 0, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutOnFrameNullBox() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrame(1, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutOnFramesStartFrameGreaterThanStopFrame() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrames(4, 3, new BoundingBox(0, 0, 0, 0, 0, false, 0, 0, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutOnFramesNullBox() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrames(3, 4, null);
    }

    @Test
    public void testPutAll() {
        Map<Integer, List<BoundingBox>> sourceMap = new HashMap<Integer, List<BoundingBox>>();

        int keysAdded = 0;
        for(int i = 1; i <= 10; i += 2) {
            sourceMap.put(i, new ArrayList<>());
            BoundingBox boundingBox = new BoundingBox(i, i, i, i, i, false, i, i, i);

            sourceMap.get(i).add(boundingBox);
            keysAdded++;
        }

        BoundingBoxMap map = new BoundingBoxMap();
        map.putAll(sourceMap);

        Assert.assertEquals("sourceMap and map must have the same size", sourceMap.size(), map.size());


        for(Map.Entry<Integer, List<BoundingBox>> entry : sourceMap.entrySet()) {
            Assert.assertTrue(String.format("map must contain key %d", entry.getKey()), map.containsKey(entry.getKey()));
            for(BoundingBox box : entry.getValue()) {
                Assert.assertTrue(String.format("map.get(%d) must contain element %s", entry.getKey(), box), map.get(entry.getKey()).contains(box));
            }
        }
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutAllWithNullKey() {
        Map<Integer, List<BoundingBox>> sourceMap = new HashMap<Integer, List<BoundingBox>>();
        sourceMap.put(null, new ArrayList<BoundingBox>());

        BoundingBoxMap map = new BoundingBoxMap();
        map.putAll(sourceMap);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutAllWithNullValue() {
        Map<Integer, List<BoundingBox>> sourceMap = new HashMap<Integer, List<BoundingBox>>();
        sourceMap.put(17, null);

        BoundingBoxMap map = new BoundingBoxMap();
        map.putAll(sourceMap);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutAllWithNullValueInList() {
        Map<Integer, List<BoundingBox>> sourceMap = new HashMap<Integer, List<BoundingBox>>();
        sourceMap.put(17, new ArrayList<BoundingBox>());
        sourceMap.get(17).add(null);

        BoundingBoxMap map = new BoundingBoxMap();
        map.putAll(sourceMap);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutAllWithNegativeKey() {
        Map<Integer, List<BoundingBox>> sourceMap = new HashMap<Integer, List<BoundingBox>>();
        sourceMap.put(-10, new ArrayList<BoundingBox>());

        BoundingBoxMap map = new BoundingBoxMap();
        map.putAll(sourceMap);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutWithNullKey() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.put(null, new ArrayList<BoundingBox>());
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutWithNullValue() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.put(10, null);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutWithNullValueInList() {
        BoundingBoxMap map = new BoundingBoxMap();
        List<BoundingBox> boxes = new ArrayList<BoundingBox>();
        boxes.add(null);
        boxes.add(new BoundingBox(0, 0, 0, 0, 0, false, 0, 0, 0));
        map.put(5, boxes);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutOnFramesWithNullStartFrame() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrames(null, 15, new BoundingBox(0, 0, 0, 0, 0, false, 0, 0, 0));
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutOnFramesWithNullStopFrame() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrames(15, null, new BoundingBox(0, 0, 0, 0, 0, false, 0, 0, 0));
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutOnFramesWithNullBox() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrames(12, 15, null);
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutOnFramesWithNegativeStartFrame() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrames(-3, 15, new BoundingBox(0, 0, 0, 0, 0, false, 0, 0, 0));
    }

    @Test(expected =  IllegalArgumentException.class)
    public void testPutOnFramesWithNegativeStopFrame() {
        BoundingBoxMap map = new BoundingBoxMap();
        map.putOnFrames(3, -15, new BoundingBox(0, 0, 0, 0, 0, false, 0, 0, 0));
    }
}

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

package org.mitre.mpf.videooverlay;

import org.mitre.mpf.wfm.buffers.*;

import java.util.*;

public class BoundingBoxMap extends TreeMap<Integer, List<BoundingBox>> {
    /**
     * Indicates that a box should be drawn on all frames.
     */
    public static final int ALL_FRAMES = Integer.MAX_VALUE;

    /**
     * Associates a list of bounding boxes (values) with the given 0-based frame index in the video (key). If the
     * key already exists, its value will be overwritten.
     *
     * @param key   The 0-based index of the video. Must not be null.
     * @param value The list of bounding boxes to draw on this frame. Must not be null or contain null.
     */
    @Override
    public List<BoundingBox> put(Integer key, List<BoundingBox> value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }

        if (key < 0) {
            throw new IllegalArgumentException("key must not be less than 0");
        }

        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        if (value.contains(null)) {
            throw new IllegalArgumentException("value must not contain null elements");
        }

        return super.put(key, value);
    }


    @Override
    public void putAll(Map<? extends Integer, ? extends List<BoundingBox>> m) {
        for (java.util.Map.Entry<? extends Integer, ? extends List<BoundingBox>> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Puts a bounding box on a specific frame. Frames are 0-based.
     *
     * @param frame       The 0-based index of the frame in the video. Must not be null.
     * @param boundingBox The bounding box to add. Must not be null.
     */
    public void putOnFrame(Integer frame, BoundingBox boundingBox) {
        if (frame == null) {
            throw new IllegalArgumentException("frame must not be null");
        }

        if (frame < 0) {
            throw new IllegalArgumentException("frame must not be less than 0");
        }

        if (boundingBox == null) {
            throw new IllegalArgumentException("boundingBox must not be null");
        }

        if (!containsKey(frame)) {
            put(frame, new ArrayList<BoundingBox>());
        }

        get(frame).add(boundingBox);
    }

    /**
     * Puts a bounding box on all frames between the frames with index firstFrame and lastFrame (inclusive).
     *
     * @param firstFrame  The 0-based index on which the boundingBox will first be drawn. Must not be null or less than 0.
     * @param lastFrame   The last 0-based index on which the boundingBox will be drawn. Must not be null or less than firstFrame.
     * @param boundingBox The bounding box. Must not be null.
     */
    public void putOnFrames(Integer firstFrame, Integer lastFrame, BoundingBox boundingBox) {
        if (firstFrame == null) {
            throw new IllegalArgumentException("firstFrame must not be null");
        }

        if (lastFrame == null) {
            throw new IllegalArgumentException("lastFrame must not be null");
        }

        if (lastFrame < firstFrame) {
            throw new IllegalArgumentException(String.format("lastFrame must not be smaller than firstFrame (%s <= %s)",
                    lastFrame.toString(),
                    firstFrame.toString()));
        }

        for (int i = firstFrame; i <= lastFrame; i++) {
            putOnFrame(i, boundingBox);
        }
    }

    /**
     * Translates and resizes {@code origin} to {@code destination} over the course of {@code interval} frames. The animation
     * starts at frame {@code firstFrame} and ends at frame {@code firstFrame + interval}, but NO ENTRY IS CREATED for
     * {@code firstFrame + interval} to avoid duplicates.
     * @param origin The original bounding box.
     * @param destination The target bounding box.
     * @param firstFrame The frame number in the video at which the animation starts.
     * @param interval The number of frames over which this animation occurs.
     */
    public void animate(BoundingBox origin, BoundingBox destination, int firstFrame, int interval) {
        if(origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }

        if(destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }

        if(firstFrame < 0) {
            throw new IllegalArgumentException("firstFrame must be greater than or equal to 0.");
        }

        if(interval < 1) {
            throw new IllegalArgumentException("interval must be greater than 0");
        }

        putOnFrame(firstFrame, origin);

        double dx = (destination.getX() - origin.getX()) / (1.0 * interval);
        double dy = (destination.getY() - origin.getY()) / (1.0 * interval);
        double dWidth = (destination.getWidth() - origin.getWidth()) / (1.0 * interval);
        double dHeight = (destination.getHeight() - origin.getHeight()) / (1.0 * interval);

        for(int frameOffset = 0; frameOffset < interval; frameOffset++) {
            BoundingBox translatedBox = new BoundingBox();
            translatedBox.setX((int)Math.round(origin.getX() + dx * frameOffset));
            translatedBox.setY((int)Math.round(origin.getY() + dy * frameOffset));
            translatedBox.setWidth((int)Math.round(origin.getWidth() + dWidth * frameOffset));
            translatedBox.setHeight((int)Math.round(origin.getHeight() + dHeight * frameOffset));
            translatedBox.setColor(origin.getColor());
            putOnFrame(firstFrame + frameOffset, translatedBox);
        }
    }

    @Override
    public String toString() {
        return String.format("%s#<keys=%d, size=%d>",
                this.getClass().getSimpleName(), this.keySet().size(), this.values().size());
    }

    /** This method is costly! */
    public List<Markup.BoundingBoxMapEntry> toBoundingBoxMapEntryList() {
        List<Markup.BoundingBoxMapEntry> entries = new ArrayList<Markup.BoundingBoxMapEntry>(getEntryCount());

        Collection<BoundingBox> values = null;
        for(int key : keySet()) {
            values = get(key);
            if(values != null) {
                for(BoundingBox value : values) {
                    if(value != null) {
                        entries.add(
                                Markup.BoundingBoxMapEntry.newBuilder()
                                        .setFrameNumber(key)
                                        .setBoundingBox(value.toProtocolBuffer())
                                        .build());
                    }
                }
            }
        }
        return entries;
    }

    public int getEntryCount() {
        int size = 0;
        Collection tmpCollection = null;
        for(int key : keySet()) {
            tmpCollection = get(key);
            size += tmpCollection == null ? 0 : tmpCollection.size();
        }
        return size;
    }
}

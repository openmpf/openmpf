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

package org.mitre.mpf.videooverlay;

import org.mitre.mpf.wfm.buffers.Markup;

import java.util.Objects;
import java.util.Optional;

/**
 * A bounding box is rectangle with an RGB color associated with it. Coordinates are
 * defined according to <a href="http://docs.oracle.com/javase/tutorial/2d/overview/coordinate.html">Java's 2D API Concepts</a>.
 */
public class BoundingBox {

    /**
     * The x-coordinate of the top-left corner of this bounding box on the given frame.
     */
    private final int x;
    public int getX() {
        return x;
    }

    /**
     * The y-coordinate of the top-left corner of this bounding box on the given frame.
     */
    private final int y;
    public int getY() {
        return y;
    }


    /**
     * The width of the bounding box.
     */
    private final int width;
    public int getWidth() {
        return width;
    }


    /**
     * The height of the bounding box.
     */
    private final int height;
    public int getHeight() {
        return height;
    }


    private final double rotationDegrees;
    public double getRotationDegrees() {
        return rotationDegrees;
    }


    private final boolean flip;
    public boolean getFlip() {
        return flip;
    }


    private final int red;
    public int getRed() {
        return red;
    }

    private final int green;
    public int getGreen() {
        return green;
    }

    private final int blue;
    public int getBlue() {
        return blue;
    }

    private final float confidence;
    public float getConfidence() {
        return confidence;
    }

    private final Optional<String> classification;
    public Optional<String> getClassification() {
        return classification;
    }

    public BoundingBox(int x, int y, int width, int height, double rotationDegrees, boolean flip,
                       int red, int green, int blue, float confidence, Optional<String> classification) {
        if (red < 0 || red > 255) {
            throw new IllegalArgumentException("red must be in range [0,255]");
        }
        if (green < 0 || green > 255) {
            throw new IllegalArgumentException("green must be in range [0,255]");
        }
        if (blue < 0 || blue > 255) {
            throw new IllegalArgumentException("blue must be in range [0,255]");
        }

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rotationDegrees = rotationDegrees;
        this.flip = flip;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.confidence = confidence;
        this.classification = classification;
    }

    @Override
    public String toString() {
        String str = String.format("%s#<x=%d, y=%d, height=%d, width=%d, rotation=%f, color=(%d, %d, %d), confidence=%f",
                this.getClass().getSimpleName(), x, y, height, width, rotationDegrees, red, green, blue, confidence);
        if (classification.isPresent()) {
            str += ", classification=" + classification.get();
        }
        str += ">";
        return str;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BoundingBox)) {
            return false;
        }
        BoundingBox casted = (BoundingBox) obj;
        return x == casted.x
                && y == casted.y
                && height == casted.height
                && width == casted.width
                && Double.compare(rotationDegrees, casted.rotationDegrees) == 0
                && flip == casted.flip
                && red == casted.red
                && green == casted.green
                && blue == casted.blue
                && confidence == casted.confidence
                && classification == casted.classification;
    }

    /**
     * Uses mutable fields - do not modify an object which is a key in a map.
     */
    @Override
    public int hashCode() {
        return Objects.hash(x, y, height, width, rotationDegrees, flip, red, green, blue, confidence, classification);
    }

    public Markup.BoundingBox toProtocolBuffer() {
        Markup.BoundingBox.Builder builder = Markup.BoundingBox.newBuilder()
                .setX(x)
                .setY(y)
                .setWidth(width)
                .setHeight(height)
                .setRotationDegrees(rotationDegrees)
                .setFlip(flip)
                .setRed(red)
                .setBlue(blue)
                .setGreen(green)
                .setConfidence(confidence);
        if (classification.isPresent()) {
            builder.setClassification(classification.get());
        }
        return builder.build();
    }
}

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

/**
 * A bounding box is rectangle with a 4-byte ARGB color associated with it. Coordinates are
 * defined according to <a href="http://docs.oracle.com/javase/tutorial/2d/overview/coordinate.html">Java's 2D API Concepts</a>.
 */
public class BoundingBox {

    /**
     * The x-coordinate of the top-left corner of this bounding box on the given frame.
     */
    private int x;

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    /**
     * The y-coordinate of the top-left corner of this bounding box on the given frame.
     */
    private int y;

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    /**
     * The width of the bounding box.
     */
    private int width;

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * The height of the bounding box.
     */
    private int height;

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * The color of the bounding box as a 4-byte ARGB value.
     */
    public int color;

    public int getColor() {
        return color;
    }

    /**
     * Sets the color using a 4-byte ARGB integer.
     *
     * @param color A 4-byte integer of the form 0xWWXXYYZZ where WW is the hex value for alpha, XX is the hex value for red, YY is the hex value for green, and ZZ is the hex value for blue.
     */
    public void setColor(int color) {
        this.color = color;
    }

    /**
     * Equivalent to See {@link #setColor(int, int, int, int) setColor(255, red, green, blue)}.
     */
    public void setColor(int red, int green, int blue) {
        setColor(255, red, green, blue);
    }

    /**
     * Sets the color of this bounding box according to the given RGBA parameter values.
     *
     * @param alpha Must be in range [0,255].
     * @param red   Must be in range [0,255].
     * @param green Must be in range [0,255].
     * @param blue  Must be in range [0,255]
     */
    public void setColor(int alpha, int red, int green, int blue) {
        // Check that each of ARGB is between 0,255 and throw an exception if that is not the case.
        if (!(0 <= alpha && alpha <= 255)) {
            throw new IllegalArgumentException("alpha must be in range [0,255]");
        } else if (!(0 <= red && red <= 255)) {
            throw new IllegalArgumentException("red must be in range [0,255]");
        } else if (!(0 <= green && green <= 255)) {
            throw new IllegalArgumentException("green must be in range [0,255]");
        } else if (!(0 <= blue && blue <= 255)) {
            throw new IllegalArgumentException("blue must be in range [0,255]");
        }

        int newColor = 0;
        newColor = (newColor | ((0x000000FF & alpha) << 24));
        newColor = (newColor | ((0x000000FF & red) << 16));
        newColor = (newColor | ((0x000000FF & green) << 8));
        newColor = (newColor | ((0x000000FF & blue) << 0));

        setColor(newColor);
    }

    public BoundingBox() { }

    public BoundingBox(int x, int y, int width, int height, int colorArgb) {
        setX(x);
        setY(y);
        setHeight(height);
        setWidth(width);
        setColor(colorArgb);
    }

    public BoundingBox(int x, int y, int width, int height, int a, int r, int g, int b) {
        this(x, y, width, height, 0);
        setColor(a, r, g, b);
    }

    @Override
    public String toString() {
        return String.format("%s#<x=%d, y=%d, height=%d, width=%d, color=%d>",
                this.getClass().getSimpleName(), x, y, height, width, color);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof BoundingBox)) {
            return false;
        } else {
            BoundingBox casted = (BoundingBox) obj;
            return x == casted.x &&
                    y == casted.y &&
                    color == casted.color &&
                    height == casted.height &&
                    width == casted.width;
        }
    }

    /**
     * Uses mutable fields - do not modify an object which is a key in a map.
     */
    @Override
    public int hashCode() {
        return color ^ (width * 37) ^ (height * 41) ^ (x * 13) ^ (y * 23);
    }

    public Markup.BoundingBox toProtocolBuffer() {
        return Markup.BoundingBox.newBuilder()
                .setColorArgb(color)
                .setHeight(height)
                .setWidth(width)
                .setX(x)
                .setY(y)
                .build();
    }
}

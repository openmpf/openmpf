/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

class Color {
    int red, green, blue, alpha;

    public int getRed() {
        return red;
    }

    public void setRed(int red) {
        this.red = red;
    }

    public int getGreen() {
        return green;
    }

    public void setGreen(int green) {
        this.green = green;
    }

    public int getBlue() {
        return blue;
    }

    public void setBlue(int blue) {
        this.blue = blue;
    }

    public int getAlpha() {
        return alpha;
    }

    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    public Color(int argb, boolean hasAlpha) {
        if(hasAlpha) { alpha = ((argb & 0xFF000000) >> 24) & 0xFF; }
        else { alpha = 0xFF; }

        red = ((argb & 0xFF0000) >> 16) & 0xFF;
        green = ((argb & 0xFF00) >> 8) & 0xFF;
        blue = ((argb & 0xFF) >> 0);
    }
}

public class TestBoundingBox {
    @Test
    public void testSetColorUsingArgb() {
        BoundingBox boundingBox = new BoundingBox();
        boundingBox.setColor(0xAA, 0xBB, 0xCC, 0xDD);

        Color color = new Color(boundingBox.getColor(), true);
        Assert.assertEquals("Alpha value was not correct.", color.getAlpha(), 0xAA);
        Assert.assertEquals("Red value was not correct.", color.getRed(), 0xBB);
        Assert.assertEquals("Green value was not correct.", color.getGreen(), 0xCC);
        Assert.assertEquals("Blue value was not correct.", color.getBlue(), 0xDD);
    }

    @Test
    public void testSetColorUsingArgbInt() {
        BoundingBox boundingBox = new BoundingBox();
        boundingBox.setColor(0xAABBCCDD);

        Color color = new Color(boundingBox.getColor(), true);
        Assert.assertEquals("Alpha value was not correct.", color.getAlpha(), 0xAA);
        Assert.assertEquals("Red value was not correct.", color.getRed(), 0xBB);
        Assert.assertEquals("Green value was not correct.", color.getGreen(), 0xCC);
        Assert.assertEquals("Blue value was not correct.", color.getBlue(), 0xDD);
    }

    @Test
    public void testSetColorUsingRgb() {
        BoundingBox boundingBox = new BoundingBox();
        boundingBox.setColor(0xBB, 0xCC, 0xDD);

        Color color = new Color(boundingBox.getColor(), true);
        Assert.assertEquals("Alpha value was not correct.", color.getAlpha(), 0xFF);
        Assert.assertEquals("Red value was not correct.", color.getRed(), 0xBB);
        Assert.assertEquals("Green value was not correct.", color.getGreen(), 0xCC);
        Assert.assertEquals("Blue value was not correct.", color.getBlue(), 0xDD);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetColorUsingArgbNegativeAlphaException() {
        new BoundingBox().setColor(-1, 0, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetColorUsingArgbNegativeRedException() {
        new BoundingBox().setColor(0, -1, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetColorUsingArgbNegativeGreenException() {
        new BoundingBox().setColor(0, 0, -1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetColorUsingArgbNegativeBlueException() {
        new BoundingBox().setColor(0, 0, 0, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetColorUsingRgbNegativeRedException() {
        new BoundingBox().setColor(-1, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetColorUsingRgbNegativeGreenException() {
        new BoundingBox().setColor(0, -1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetColorUsingRgbNegativeBlueException() {
        new BoundingBox().setColor(0, 0, -1);
    }

    @Test
    public void testToString() {
        BoundingBox box = new BoundingBox();
        box.setX(12);
        box.setY(34);
        box.setWidth(56);
        box.setHeight(78);
        box.setColor(0x0);
        box.toString();
    }

    @Test
    public void testEquals() {
        BoundingBox box1 = new BoundingBox();
        box1.setX(12);
        box1.setY(34);
        box1.setWidth(56);
        box1.setHeight(78);
        box1.setColor(0x0);

        BoundingBox box2 = new BoundingBox();
        box2.setX(12);
        box2.setY(34);
        box2.setWidth(56);
        box2.setHeight(78);
        box2.setColor(0x0);

        BoundingBox box3 = new BoundingBox();
        box3.setX(12);
        box3.setY(34);
        box3.setWidth(56);
        box3.setHeight(78);
        box3.setColor(0xFF0000FF); // Differ only by color.

        // Test that objects equal themselves...
        Assert.assertTrue("box1.equals(box1) should be true", box1.equals(box1));
        Assert.assertTrue("box2.equals(box2) should be true", box2.equals(box2));
        Assert.assertTrue("box3.equals(box3) should be true", box3.equals(box3));

        // Box1 and Box2 should be equal with the same hash code values.
        Assert.assertTrue("box1.equals(box2) should be true", box1.equals(box2));
        Assert.assertTrue("box2.equals(box1) should be true", box2.equals(box1));
        Assert.assertTrue("box1.hashCode() != box2.hashCode()", box1.hashCode() == box2.hashCode());

        // Box1 and Box2 should not reference the same object.
        Assert.assertFalse("box1 == box2 should not be true (they should be different objects)", box1 == box2);

        // Box1 and Box3 should not be the equal (reflexive).
        Assert.assertFalse("box1.equals(box3) should be false", box1.equals(box3));
        Assert.assertFalse("box3.equals(box1) should be false", box3.equals(box1));
    }
}

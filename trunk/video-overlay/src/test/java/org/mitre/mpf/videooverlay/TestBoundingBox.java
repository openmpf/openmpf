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

import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;


public class TestBoundingBox {

    @Test
    public void testEquals() {
        BoundingBox box1 = new BoundingBox(12, 34, 56, 78, 0, false, 0, 0, 0, Optional.empty());
        BoundingBox box2 = new BoundingBox(12, 34, 56, 78, 0, false, 0, 0, 0, Optional.empty());
        // Differ only by color.
        BoundingBox box3 = new BoundingBox(12, 34, 56, 78, 0, false, 0, 0, 0xFF, Optional.empty());

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

        // Box1 and Box3 should not be equal (reflexive).
        Assert.assertFalse("box1.equals(box3) should be false", box1.equals(box3));
        Assert.assertFalse("box3.equals(box1) should be false", box3.equals(box1));
    }
}

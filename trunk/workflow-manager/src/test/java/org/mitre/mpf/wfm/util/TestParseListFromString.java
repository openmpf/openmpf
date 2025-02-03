/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/
/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.util;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

public class TestParseListFromString {

    @Test
    public void parseEmptyList() {
        String testString = "";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.isEmpty());
    }


    @Test
    public void parseListWithSingleString() {
        String testString = "Hey";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 1);
        assertTrue(result.get(0).equals(testString));
    }


    @Test
    public void ParseDelimitedList() {
        String testString = "Hey;Hello;World";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 3);
        assertTrue(result.get(0).equals("Hey"));
        assertTrue(result.get(1).equals("Hello"));
        assertTrue(result.get(2).equals("World"));
    }

    @Test
    public void ParseListWithEscapedDelimiter() {
        String testString = "Hey;Hello\\;World";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 2);
        assertTrue(result.get(0).equals("Hey"));
        assertTrue(result.get(1).equals("Hello;World"));
    }

    @Test
    public void ParseListWithUnnecessaryDoubleBackslash() {
        String testString = "Hey\\Hello;World";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 2);
        assertTrue(result.get(0).equals("HeyHello"));
        assertTrue(result.get(1).equals("World"));
    }

    @Test
    public void ParseListWithQuadrupleBackslash() {
        String testString = "Hey\\\\Hello;World";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 2);
        assertTrue(result.get(0).equals("Hey\\Hello"));
        assertTrue(result.get(1).equals("World"));
    }

    @Test
    public void ParseListWithNewlines() {
        String testString = "Hey\nHello;World\\\nFoo\\nBar";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 2);
        assertTrue(result.get(0).equals("Hey\nHello"));
        assertTrue(result.get(1).equals("World\nFoonBar"));
    }

    @Test
    public void ParseListWith8BackslashesAndNewline() {
        String testString = "Hey\\\\\\\\\nHello;World";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 2);
        assertTrue(result.get(0).equals("Hey\\\\\nHello"));
        assertTrue(result.get(1).equals("World"));
    }

    @Test
    public void ParseListWithExtraDelimiter() {
        String testString = "Hello;;World";
        List<String> result = new ArrayList<String>(TextUtils.parseListFromString(testString));
        assertTrue(result.size() == 2);
        assertTrue(result.get(0).equals("Hello"));
        assertTrue(result.get(1).equals("World"));
    }

}

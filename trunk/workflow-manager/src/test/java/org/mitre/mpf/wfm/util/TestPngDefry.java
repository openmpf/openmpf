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


package org.mitre.mpf.wfm.util;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.mpf.test.TestUtil;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class TestPngDefry {

    private static final Path CRUSHED_IMG
            = TestUtil.findFilePath("/samples/pngdefry/lenna-crushed.png");

    @Rule
    public TemporaryFolder _tempDir = new TemporaryFolder();

    private Path _tempPath;

    @Before
    public void init() {
        _tempPath = _tempDir.getRoot().toPath();
    }


    @Test
    public void canDetectCrushedPng() throws IOException {
        assertTrue(PngDefry.isCrushed(CRUSHED_IMG));

        assertFalse(PngDefry.isCrushed(TestUtil.findFilePath(
                "/samples/pngdefry/lenna-normal.png")));
        assertFalse(PngDefry.isCrushed(TestUtil.findFilePath("/samples/meds1.jpg")));
    }

    @Test
    public void canDefry() throws IOException {
        assertDefried(PngDefry.defry(CRUSHED_IMG, _tempPath));
    }

    @Test
    public void canDefryWithWrongExtension() throws IOException {
        var input = _tempPath.resolve("lenna-crushed.asdf");
        Files.copy(CRUSHED_IMG, input);
        assertDefried(PngDefry.defry(input, _tempPath));
    }

    @Test
    public void canDefryWithNoExtension() throws IOException {
        var input = _tempPath.resolve("lenna-crushed");
        Files.copy(CRUSHED_IMG, input);
        assertDefried(PngDefry.defry(input, _tempPath));
    }


    @Test
    public void canDetectWhenDefryFails() {
        var notPng = TestUtil.findFilePath("/samples/meds1.jpg");
        TestUtil.assertThrows(IllegalStateException.class,
                              () -> PngDefry.defry(notPng, _tempPath));

        var regularPng = TestUtil.findFilePath("/samples/pngdefry/lenna-normal.png");
        TestUtil.assertThrows(IllegalStateException.class,
                              () -> PngDefry.defry(regularPng, _tempPath));

        var doesNotExist = _tempPath.resolve("asdf");
        TestUtil.assertThrows(IllegalStateException.class,
                              () -> PngDefry.defry(doesNotExist, _tempPath));
    }


    private static void assertDefried(Path imgPath) throws IOException {
        var image = ImageIO.read(imgPath.toFile());
        assertEquals(512, image.getHeight());
        assertEquals(512, image.getWidth());
    }
}

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

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class TestIoUtils {

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private Path _tempRoot;

    private final IoUtils _ioUtils = new IoUtils();

    @BeforeClass
    public static void initClass() {
        // The "file" command will silently ignore missing files as long as one of the files provided when using the
        // --magic-file option is available. Ensure that the default Linux magic file is installed.
        assertTrue(Files.exists(Paths.get("/usr/share/misc/magic.mgc")));
        ThreadUtil.start();
    }

    @Before
    public void init() {
        _tempRoot = _tempFolder.getRoot().toPath();
    }

    @Test
    public void canRecursivelyDeleteEmptyDirectoryTree() throws IOException {
        _tempFolder.newFolder("level_1_dir_1", "level_2_dir_1", "level_3_dir_1");
        _tempFolder.newFolder("level_1_dir_1", "level_2_dir_1", "level_3_dir_2");
        _tempFolder.newFolder("level_1_dir_1", "level_2_dir_2");
        _tempFolder.newFolder("level_1_dir_2");

        IoUtils.deleteEmptyDirectoriesRecursively(_tempRoot);
        assertFalse(Files.exists(_tempRoot));
    }

    @Test
    public void doesNotDeleteFilesWhenDeletingEmptyDirs() throws IOException {
        Path emptyDir1 = _tempFolder.newFolder("level_1_dir_1", "level_2_dir_1", "level_3_dir_1").toPath();
        Path emptyDir2 = _tempFolder.newFolder("level_1_dir_1", "level_2_dir_1", "level_3_dir_2", "level_4_dir_1").toPath();
        _tempFolder.newFolder("level_1_dir_1", "level_2_dir_2");
        Path emptyDir3 = _tempFolder.newFolder("level_1_dir_2").toPath();
        Path file1 = _tempFolder.newFile("level_1_dir_1/level_2_dir_1/level_3_dir_2/file1").toPath();
        Path file2 = _tempFolder.newFile("level_1_dir_1/level_2_dir_2/file2").toPath();

        IoUtils.deleteEmptyDirectoriesRecursively(_tempRoot);
        assertTrue(Files.exists(file1));
        assertTrue(Files.exists(file2));

        assertFalse(Files.exists(emptyDir1));
        assertFalse(Files.exists(emptyDir2));
        assertFalse(Files.exists(emptyDir3));
    }

    @Test
    public void canDetectMimeType() {
        assertEquals("image/jpeg", _ioUtils.getMimeType(getResourcePath("/samples/meds1.jpg")));

        assertEquals("video/mp4", _ioUtils.getMimeType(getResourcePath("/samples/video_01.mp4")));

        assertEquals("audio/vnd.wave", _ioUtils.getMimeType(getResourcePath("/samples/green.wav")));

        assertEquals("text/plain", _ioUtils.getMimeType(getResourcePath("/samples/NOTICE")));
    }

    @Test
    public void canDetectMimeTypeUsingTika() throws IOException {
        assertEquals("video/vnd.dlna.mpeg-tts", _ioUtils.getMimeTypeUsingTika(getResourcePath("/samples/bbb24p_00_short.ts")));
    }

    @Test
    public void canDetectMimeTypeUsingFile() throws IOException, InterruptedException {
        assertEquals("audio/x-hx-aac-adts", _ioUtils.getMimeTypeUsingFile(getResourcePath("/samples/green.adts")));
    }

    private Path getResourcePath(String subpath) {
        return Paths.get(this.getClass().getResource(subpath).getPath());
    }
}

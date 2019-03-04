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


package org.mitre.mpf.wfm.util;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestIoUtils {

    @Rule
    public TemporaryFolder _tempFolder = new TemporaryFolder();

    private Path _tempRoot;

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
}

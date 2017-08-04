/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

package org.mitre.mpf.framecounter;

import org.mitre.mpf.framecounter.FrameCounterJniException;

import java.io.File;
import java.io.IOException;

public class FrameCounter {
    private File file;
    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }

    public FrameCounter(File file) { this.file = file; }

    public int count(boolean bruteForce) throws IOException {
        if(file == null) {
            throw new IllegalArgumentException("File must not be null.");
        }

        if(!file.exists()) {
            throw new IllegalArgumentException(String.format("The file '%s' does not exist.", file));
        }

        if(!file.canRead()) {
            throw new IOException(String.format("Cannot read file '%s'.", file));
        }

        int returnCode = countNative(file.getAbsolutePath(), bruteForce);
        if(returnCode < 0) {
            throw new FrameCounterJniException(returnCode);
        } else {
            return returnCode;
        }
    }

    private native int countNative(String absolutePath, boolean bruteForce);
}

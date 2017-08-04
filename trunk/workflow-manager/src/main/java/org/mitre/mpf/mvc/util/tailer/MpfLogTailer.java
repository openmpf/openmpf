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

/*
 * This source code file constitutes a derivative work of Apache
 * Commons-IO v2.4 org.apache.commons.io.input.Tailer,
 * which is distributed under the following license.
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mitre.mpf.mvc.util.tailer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/*
 * Simple implementation of the unix "tail -f" functionality.
 */
public class MpfLogTailer {

    private static final String RAF_MODE = "r";

    private static final int DEFAULT_BUFSIZE = 4096;

    /**
     * Buffer on top of RandomAccessFile.
     */
    private final byte inbuf[];

    /**
     * The file which will be tailed.
     */
    private final File file;

    /**
     * Whether to tail from the end or start of file
     */
    private final boolean end;

    /**
     * The listener to notify of events when tailing.
     */
    private final MpfLogTailerListener listener;

    /**
     * Whether to close and reopen the file whilst waiting for more input.
     */
    private final boolean reOpen;

    /*
     * The reader to access the file content.
     */
    private RandomAccessFile reader = null;

    /*
     * The last time the file was checked for changes.
     */
    private long lastChecked = 0;

    /*
     * Current position within the file.
     */
    private long position = 0;

    /**
     * Creates a Tailer for the given file, starting from the beginning.
     * @param file the file to follow.
     * @param listener the MpfLogTailerListener to use.
     */
    public MpfLogTailer(File file, MpfLogTailerListener listener) {
        this(file, listener, false, false, DEFAULT_BUFSIZE, -1, -1);
    }

    /**
     * Creates a Tailer for the given file, starting from the beginning.
     * @param file the file to follow.
     * @param listener the MpfLogTailerListener to use.
     * @param lastChecked The last time the file was checked for changes
     * @param position Current position within the file.
     */
    public MpfLogTailer(File file, MpfLogTailerListener listener, long lastChecked, long position) {
        this(file, listener, false, false, DEFAULT_BUFSIZE, lastChecked, position);
    }

    /**
     * Creates a Tailer for the given file, with a specified buffer size.
     * @param file the file to follow.
     * @param listener the MpfLogTailerListener to use.
     * @param end Set to true to tail from the end of the file, false to tail from the beginning of the file.
     * @param reOpen if true, close and reopen the file between reading chunks
     * @param bufSize Buffer size
     * @param lastChecked The last time the file was checked for changes
     * @param position Current position within the file.
     */
    public MpfLogTailer(File file, MpfLogTailerListener listener, boolean end, boolean reOpen,
                        int bufSize,long lastChecked, long position) {
        this.file = file;
        this.end = end;

        this.inbuf = new byte[bufSize];

        // Save and prepare the listener
        this.listener = listener;
        listener.init(this);
        this.reOpen = reOpen;

        this.lastChecked = lastChecked;
        this.position = position;
    }

    /**
     * Return the file.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Return the last time the file was checked for changes.
     *
     * @return lastChecked
     */
    public long getLastChecked() {
        return lastChecked;
    }

    /**
     * Return the current position within the file.
     *
     * @return position
     */
    public long getLastPosition() {
        return position;
    }

    /**
     * Follows changes in the file, calling the MpfLogTailerListener's handle method for each new line.
     *
     * @param maxLines The maximum number of lines to read
     * @return The number of lines read
     */
    public int readLines(int maxLines) {
        int numLines = 0;

        try {
            // Open the file
            if (reader == null) {
                try {
                    reader = new RandomAccessFile(file, RAF_MODE);
                } catch (FileNotFoundException e) {
                    listener.fileNotFound();
                }

                if (reader != null) {
                    if (lastChecked == -1) {
                        lastChecked = System.currentTimeMillis();
                    }
                    if (position == -1) {
                        position = end ? file.length() : 0;
                    }
                    reader.seek(position);
                }
            }

            // Get new content

            boolean newer = FileUtils.isFileNewer(file, lastChecked); // IO-279, must be done first

            // Check the file length to see if it was rotated
            long length = file.length();

            if (length < position) {

                // File was rotated
                listener.fileRotated();

                // Reopen the reader after rotation
                try {
                    // Ensure that the old file is closed iff we re-open it successfully
                    RandomAccessFile save = reader;
                    reader = new RandomAccessFile(file, RAF_MODE);
                    position = 0;
                    // close old file explicitly rather than relying on GC picking up previous RAF
                    IOUtils.closeQuietly(save);
                } catch (FileNotFoundException e) {
                    // in this case we continue to use the previous reader and position values
                    listener.fileNotFound();
                }

                return readLines(maxLines); // start over with rotated file

            } else {

                // File was not rotated

                // See if the file needs to be read again
                if (length > position) {

                    // The file has more content than it did last time
                    numLines = readLines(reader, maxLines);
                    lastChecked = System.currentTimeMillis();

                } else if (newer) {

                    /*
                     * This can happen if the file is truncated or overwritten with the exact same length of
                     * information. In cases like this, the file position needs to be reset
                     */
                    position = 0;
                    reader.seek(position); // cannot be null here

                    // Now we can read new lines
                    numLines = readLines(reader, maxLines);
                    lastChecked = System.currentTimeMillis();
                }
            }
            if (reOpen) {
                IOUtils.closeQuietly(reader);
            }
            if (reOpen) {
                reader = new RandomAccessFile(file, RAF_MODE);
                reader.seek(position);
            }

        } catch (Exception e) {
            listener.handle(e);
        }

        return numLines;
    }

    /**
     * Cleanup loose ends.
     */
    public void cleanup() {
        IOUtils.closeQuietly(reader);
    }

    /**
     * Read new lines.
     *
     * @param reader The file to read
     * @param maxLines The maximum number of lines to read
     * @return The number of lines read
     * @throws IOException if an I/O error occurs.
     */
    private int readLines(RandomAccessFile reader, int maxLines) throws IOException {
        int numLines = 0;
        StringBuilder sb = new StringBuilder();

        long pos = reader.getFilePointer();
        long rePos = pos; // position to re-read

        byte ch;
        boolean seenCR = false;
        while ( ((ch = (byte)(reader.read())) != -1) && (numLines < maxLines)) {
            switch (ch) {
                case '\n':
                    seenCR = false; // swallow CR before LF
                    if (listener.handle(sb.toString())) {
                        numLines++;
                    }
                    sb.setLength(0);
                    rePos = pos + 1;
                    break;
                case '\r':
                    if (seenCR) {
                        sb.append('\r');
                    }
                    seenCR = true;
                    break;
                default:
                    if (seenCR) {
                        seenCR = false; // swallow final CR
                        if (listener.handle(sb.toString())) {
                            numLines++;
                        }
                        sb.setLength(0);
                        rePos = pos + 1;
                    }
                    sb.append((char) ch); // add character, not its ascii value
            }

            pos = reader.getFilePointer();
        }

        reader.seek(rePos); // Ensure we can re-read if necessary
        position = rePos;

        return numLines;
    }

}

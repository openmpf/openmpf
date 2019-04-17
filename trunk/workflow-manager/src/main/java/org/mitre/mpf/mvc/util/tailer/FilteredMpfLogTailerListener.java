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

package org.mitre.mpf.mvc.util.tailer;

import java.util.LinkedList;
import java.util.List;

/**
 * Listener for events from a {@link MpfLogTailer}.
 */
public class FilteredMpfLogTailerListener extends MpfLogTailerListenerAdapter {

    /**
     * Ordered list of filtered log lines found by MpfLogTailer.
     */
    private List<String> lines = null;

    /**
     * Ordered list of filtered log entries found by MpfLogTailer.
     */
    // private List<String> entries = null;

    /**
     * Current log entry. An entry might consist of multiple lines
     * following a <TIMESTAMP><LEVEL> header.
     */
    // private StringBuffer entryBuffer = null;

    /**
     * Desired log filter level.
     */
    private MpfLogLevel filterLevel = null;

    /**
     * Log level of last line found by MpfLogTailer.
     */
    private MpfLogLevel lastLineLevel = null;

    /**
     * Constructor.
     *
     * @param filterLevel ALL, TRACE, DEBUG, INFO, WARN, ERROR, or FATAL
     */
    public FilteredMpfLogTailerListener(MpfLogLevel filterLevel) {
        this(filterLevel, null);
    }

    /**
     * Constructor.
     *
     * @param filterLevel ALL, TRACE, DEBUG, INFO, WARN, ERROR, or FATAL
     * @param lastLineLevel log level of the last line found by MpfLogTailer
     */
    public FilteredMpfLogTailerListener(MpfLogLevel filterLevel, MpfLogLevel lastLineLevel) {
        this.filterLevel = filterLevel;
        this.lastLineLevel = lastLineLevel;
        lines = new LinkedList<String>();
        // entries = new LinkedList<String>();
        // entryBuffer = new StringBuffer();
    }

    public MpfLogLevel getLastLineLevel() {
        return lastLineLevel;
    }

    public synchronized List<String> purgeLines() {
        List<String> tmp = new LinkedList<String>(lines);
        lines.clear();
        return tmp;
    }

    /*
    public synchronized String purgeEntriesAsString() {
        storeEntry(); // might be incomplete

        StringBuffer buff = new StringBuffer();
        for(String entry: entries) {
            if (buff.length() > 0) {
                buff.append('\n');
            }
            buff.append(entry);
        }

        entries.clear();
        return buff.toString();
    }
    */

    /*
    public synchronized List<String> purgeEntriesAsList() {
        storeEntry(); // might be incomplete
        List<String> tmp = new LinkedList<String>(entries);
        entries.clear();
        return tmp;
    }
    */

    @Override
    public synchronized boolean handle(String line) {

        // handle Java logs
        MpfLogLevel lineLevel = getLevel(line, " ", 2);

        // handle C++ logs
        if (lineLevel == null) {
            lineLevel = getLevel(line, " ", 3);
        }

        if (lineLevel == null) {
            // not all log lines start with a valid <TIMESTAMP><LEVEL> header;
            // those lines inherit the level of the last line with a valid header
            lineLevel = lastLineLevel;

        } else {
            // store the old entry
            // storeEntry();

            // make way for a new entry
            lastLineLevel = lineLevel;
        }

        if (lineLevel != null &&
                lineLevel.isGreaterOrEqual(filterLevel)) {

            lines.add(line);

            // if (entryBuffer.length() > 0) {
            //    entryBuffer.append('\n');
            // }
            // entryBuffer.append(line);

            return true;
        }

        return false;
    }

    private MpfLogLevel getLevel(String line, String delimiter, int index) {
        String[] tokens = line.split(delimiter);
        if (index >= tokens.length) {
            return null;
        } else {
            return MpfLogLevel.toLevel(tokens[index], null);
        }
    }

    /*
    private void storeEntry() {
        if (entryBuffer.length() > 0) {
            entries.add(entryBuffer.toString());
            entryBuffer.delete(0, entryBuffer.length()); // clear
        }
    }
    */
}

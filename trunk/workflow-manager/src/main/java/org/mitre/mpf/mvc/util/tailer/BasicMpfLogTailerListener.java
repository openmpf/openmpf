/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

/**
 * Listener for events from a {@link MpfLogTailer}.
 */
public class BasicMpfLogTailerListener extends MpfLogTailerListenerAdapter {

    private StringBuffer logBuffer = null;

    public BasicMpfLogTailerListener() {
        logBuffer = new StringBuffer();
    }

    public synchronized String purgeBuffer() {
        String text = logBuffer.toString();
        logBuffer.delete(0, logBuffer.length()); // clear
        return text;
    }

    @Override
    public synchronized boolean handle(String line) {
        if (logBuffer.length() > 0) {
            logBuffer.append('\n');
        }
        logBuffer.append(line);
        return true;
    }

}

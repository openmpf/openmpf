/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2016 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2016 The MITRE Corporation                                       *
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

import org.javasimon.Split;
import org.javasimon.StopwatchSample;
import org.javasimon.callback.CallbackSkeleton;
import org.slf4j.LoggerFactory;

public class DefaultCallbackSkeleton extends CallbackSkeleton {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(JniLoader.class);

    public DefaultCallbackSkeleton() { }

    @Override
    public void onStopwatchStart(Split split) {
        if(log.isTraceEnabled()) {
            log.trace("Stopwatch '{}' has started.", split.getStopwatch().getName());
        }
    }

    @Override
    public void onStopwatchStop(Split split, StopwatchSample sample) {
        if(log.isTraceEnabled()) {
            log.trace("Stopwatch '{}' has stopped. ({})", split.getStopwatch().getName(), split.presentRunningFor());
        }
    }
}

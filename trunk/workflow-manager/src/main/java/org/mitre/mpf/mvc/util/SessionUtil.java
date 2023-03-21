/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

package org.mitre.mpf.mvc.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpSession;

public class SessionUtil {

    private static final String SESSION_JOBS = "SESSION_JOBS";

    private SessionUtil() {
    }

    public static void addJob(HttpSession session, long jobId) {
        synchronized (session) {
            var jobs = (Collection<Long>) session.getAttribute(SESSION_JOBS);
            if (jobs == null) {
                jobs = new HashSet<Long>();
                session.setAttribute(SESSION_JOBS, jobs);
            }
            jobs.add(jobId);
        }
    }

    public static boolean containsJob(HttpSession session, long jobId) {
        synchronized (session) {
            var jobs = (Collection<Long>) session.getAttribute(SESSION_JOBS);
            if (jobs == null) {
                return false;
            }
            else {
                return jobs.contains(jobId);
            }
        }
    }

    public static Set<Long> getJobs(HttpSession session) {
        synchronized (session) {
            var jobs = (Collection<Long>) session.getAttribute(SESSION_JOBS);
            if (jobs == null) {
                return Set.of();
            }
            else {
                return new HashSet<>(jobs);
            }
        }
    }
}

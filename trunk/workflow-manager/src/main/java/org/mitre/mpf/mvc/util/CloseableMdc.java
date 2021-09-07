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


package org.mitre.mpf.mvc.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Class to temporarily change the MDC context. When the object is created, the calling thread's
 * existing MDC context is stored. Then, the calling thread's MDC context is set to the constructor
 * parameter. When close is called, the thread's MDC context is restored to its original value.
 * Any changes made to the MDC context between construction and close are discarded.
 */
public class CloseableMdc implements AutoCloseable {

    private final Map<String, String> _existingCtx;

    private CloseableMdc(Map<String, String> context) {
        _existingCtx = MDC.getCopyOfContextMap();
        MDC.setContextMap(context);
    }

    /**
     * Restore the MDC context back to what it was when this object was constructed.
     */
    @Override
    public void close() {
        MDC.setContextMap(_existingCtx);
    }

    /**
     * Temporarily change the entire MDC context to the given context.
     * @param context A new MDC context to replace the current MDC context.
     * @return A CloseableMdc that when closed discards any changes made to the MDC context since
     *          this method was originally called.
     */
    public static CloseableMdc all(Map<String, String> context) {
        return new CloseableMdc(context);
    }


    /**
     * Capture the entire MDC context so that it can later be restored discarding any changes
     * made since this method was called.
     * @return A CloseableMdc that when closed discards any changes made to the MDC context since
     *          this method was originally called.
     */
    public static CloseableMdc all() {
        return new CloseableMdc(MDC.getCopyOfContextMap());
    }


    /**
     * Temporarily add the given jobId to the current context.
     * @param jobId The job id to add to the MDC context.
     * @return A CloseableMdc that when closed removes the job id and discards any other changes
     *          made to the MDC context since this method was originally called.
     */
    public static CloseableMdc job(long jobId) {
        // MDC.putCloseable does something very similar, except that it deletes the key at the end
        // instead of restoring the original value.
        var ctx = MDC.getCopyOfContextMap();
        ctx.put("jobId", String.valueOf(jobId));
        return new CloseableMdc(ctx);
    }
}

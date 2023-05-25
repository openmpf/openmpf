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


package org.mitre.mpf.mvc.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Utility methods to make using CloseableMdc less verbose when it only needs to be set
 * for a single method call that does not throw checked exceptions.
 */
public class MdcUtil {

    private MdcUtil() {
    }

    /**
     * Run the specified task. After the task completes, reset the MDC context to its original
     * value.
     * @param task The task to execute
     * @param <T> The task's return type
     * @return The result of calling task.get()
     */
    public static <T> T all(Supplier<T> task) {
        return all(MDC.getCopyOfContextMap(), task);
    }


    /**
     * Sets the MDC context to the given context. Then, execute the task with the modified MDC
     * context. After the task completes, reset the MDC context to its original value.
     * @param context The MDC context to use while running the specified task
     * @param task The task to execute
     * @param <T> The task's return type
     * @return The result of calling task.get()
     */
    public static <T> T all(Map<String, String> context, Supplier<T> task) {
        try (var mdc = CloseableMdc.all(context)) {
            return task.get();
        }
    }


    /**
     * Run the specified task. After the task completes, reset the MDC context to its original
     * value.
     * @param task The task to execute
     */
    public static void all(Runnable task) {
        all(MDC.getCopyOfContextMap(), task);
    }


    /**
     * Sets the MDC context to the given context. Then, execute the task with the modified MDC
     * context. After the task completes, reset the MDC context to its original value.
     * @param context The MDC context to use while running the specified task
     * @param task The task to execute
     */
    public static void all(Map<String, String> context, Runnable task) {
        all(context, asSupplier(task));
    }


    /**
     * Adds the given job id to MDC context. Then, execute the task with the modified MDC context.
     * After the task completes, reset the MDC context to its original value.
     * @param jobId The job id to be added to the MDC context
     * @param task The task to execute
     * @param <T> The task's return type
     * @return The result of calling task.get()
     */
    public static <T> T job(long jobId, Supplier<T> task) {
        try (var mdc = CloseableMdc.job(jobId)) {
            return task.get();
        }
    }


    /**
     * Adds the given job id to MDC context. Then, execute the task with the modified MDC context.
     * After the task completes, reset the MDC context to its original value.
     * @param jobId The job id to be added to the MDC context
     * @param task The task to execute
     */
    public static void job(long jobId, Runnable task) {
        job(jobId, asSupplier(task));
    }


    private static Supplier<Void> asSupplier(Runnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }
}

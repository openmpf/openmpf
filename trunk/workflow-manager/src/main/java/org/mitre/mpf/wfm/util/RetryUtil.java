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

package org.mitre.mpf.wfm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RetryUtil.class);

    private RetryUtil() {
    }

    @FunctionalInterface
    public interface RetryFunction<T, E extends Exception> {
        T execute() throws E;
    }

    @FunctionalInterface
    public interface RetryAction<E extends Exception> {
        void execute() throws E;
    }


    public static <E extends Exception> void execute(
            int numRetries,
            Class<? extends Exception> retryableException,
            String description,
            RetryAction<E> operation) throws E {

        execute(numRetries, retryableException, description, () -> {
            operation.execute();
            return null;
        });

    }

    public static <T, E extends Exception> T execute(
            int numRetries,
            Class<? extends Exception> retryableException,
            String description,
            RetryFunction<T, E> operation) throws E {

        int remainingAttempts = numRetries + 1;
        long sleepTime = 100;
        while (remainingAttempts > 0) {
            remainingAttempts--;
            try {
                return operation.execute();
            }
            catch (Exception e) {
                if (remainingAttempts <= 0 || !retryableException.isInstance(e)) {
                    throw e;
                }
                sleepTime = Math.min(sleepTime * 2, 30_000);
                LOG.warn(
                        "{} failed due to: \"{}\". There are {} attempts remaining and the"
                            + " next attempt will begin in {} ms.",
                        description, e, remainingAttempts, sleepTime);
            }
            try {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException();
    }
}

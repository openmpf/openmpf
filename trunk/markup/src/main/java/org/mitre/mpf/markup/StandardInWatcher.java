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

package org.mitre.mpf.markup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardInWatcher implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(StandardInWatcher.class);

    private volatile boolean _quitReceived = false;

    private final Connection _amqConnection;

    private final Thread _messageProcessingThread;


    public static StandardInWatcher start(
            Connection amqConnection, Thread messageProcessingThread) {
        var watcher = new StandardInWatcher(amqConnection, messageProcessingThread);
        var thread = new Thread(watcher, "StandardInWatcher");
        thread.setDaemon(true);
        thread.start();
        return watcher;
    }


    private StandardInWatcher(
            Connection amqConnection, Thread messageProcessingThread) {
        _amqConnection = amqConnection;
        _messageProcessingThread = messageProcessingThread;
    }

    public boolean quitReceived() {
        return _quitReceived;
    }

    @Override
    public void run() {
        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOG.info("Received input on stdin: \"{}\"", line);
                if (line.startsWith("q")) {
                    _quitReceived = true;
                    shutdown();
                    return;
                }
            }
            LOG.info("Standard in was closed. Must use a signal to exit.");
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void shutdown() {
        try {
            LOG.info("Closing ActiveMQ connection...");
            // Closing the connection should cause the message processing thread to exit.
            // If the message processing thread is waiting for a message, the receive method will
            // return null. If the message processing thread is currently processing a message, an
            // exception will be thrown the next time it calls a JMS method.
            _amqConnection.close();
            LOG.info("ActiveMQ connection closed.");
        }
        catch (JMSException e) {
            LOG.error("An error occurred while trying to close ActiveMQ connection: " + e, e);
        }

        try {
            _messageProcessingThread.join(1_000);
            if (_messageProcessingThread.isAlive()) {
                LOG.info("Message processing thread did not exit when connection closed, attempting interrupt.");
                _messageProcessingThread.interrupt();
            }
        }
        catch (InterruptedException ignored) {
            // Already shutting down
            Thread.currentThread().interrupt();
        }
    }
}

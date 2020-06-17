/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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
package org.mitre.mpf.nms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class OutputShredder implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(OutputShredder.class);
    public static final int DEFAULT_BUFFER_SIZE = 2048;

    private InputStream stream = null;
    private String outputName = "undef";

    private Thread thread;
    private final Boolean runningMutex = new Boolean(true);
    private volatile boolean running = false;

    OutputReader reader = null;
    OutputReceiver receiver = null;

    private int bufferSize = DEFAULT_BUFFER_SIZE;

    /**
     * Do it yourself here. Make sure the reader and receiver are set prior to
     * calling start...
     */
    public OutputShredder() {
    }

    /**
     *
     * @param stream
     * @param outputName
     */
    public OutputShredder(InputStream stream, String outputName, OutputReader reader, OutputReceiver receiver) {
        this.stream = stream;
        this.outputName = outputName;
        this.reader = reader;
        this.receiver = receiver;

        start();
    }

    public InputStream getTheStream() {
        return stream;
    }

    public void setTheStream(InputStream theStream) {
        this.stream = theStream;
    }

    public String getOutputName() {
        return outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public void setReader(OutputReader reader) {
        this.reader = reader;
    }

    public void setReceiver(OutputReceiver receiver) {
        this.receiver = receiver;
    }

    public boolean isRunning() {
        return running;
    }
    
    /**
     * Waits until running is false
     * @param timeout
     * @return true if done (not running) else false (time expired)
     * @throws java.lang.InterruptedException
     */
    public boolean waitTillDone(long timeout) throws InterruptedException {
        synchronized (this.runningMutex) {
            if (!this.running)
                return true;
            this.runningMutex.wait(timeout);
            return !this.running;
        }
    }

    public Thread getThread() {
        return thread;
    }
    
    /**
     *
     */
    public void start() {
        if (stream != null) {
            running = true;
            thread = new Thread(this);
            thread.start();
        }
    }

    /**
     * stop processing of the output stream
     */
    public void stop() {
        if (running) {
            running = false;
            //thread.interrupt();
            
        }
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used to
     * create a thread, starting the thread causes the object's <code>run</code>
     * method to be called in that separately executing thread.
     * <p/>
     * The general contract of the method <code>run</code> is that it may take
     * any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        String inLine;
        String outLine;
        BufferedReader rdr = new BufferedReader(new InputStreamReader(stream));

        while (running) {
            try {
                inLine = rdr.readLine();
                while (inLine != null) {
                    if (null != reader) {
                        outLine = reader.readOutput(outputName, inLine);
                    } else {
                        outLine = inLine;
                    }
                    if (null != receiver && null != outLine) {
                        receiver.receiveOutput(outputName, outLine);
                    }
                    inLine = rdr.readLine();
                }
            } catch (IOException ex) {
                LOG.debug("IOException reading from {}: ", outputName, ex);
                break;  // an exception prolly means I/O closed or done, so we're done
            }
        }
        synchronized (this.runningMutex) {
            this.runningMutex.notifyAll();
        }
        running = false;
    }
}

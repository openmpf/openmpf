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

package org.mitre.mpf.helloWorld;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import java.io.IOException;

/**
 * Created by mpf on 9/28/15.
 */
public class HelloMain {

    public static String ACTIVEMQHOST = "tcp://localhost:61616";
    private static final Logger LOG = LoggerFactory.getLogger(org.mitre.mpf.helloWorld.HelloMain.class);

    public static void main(String[] args) {

        //TODO cmd line args

        HelloMessenger messenger = new HelloMessenger();
        boolean isMessengerAvailable = true;

        if (args.length > 0) {
            ACTIVEMQHOST = args[0];
        } else if (System.getenv("ACTIVE_MQ_HOST") != null && !System.getenv("ACTIVE_MQ_HOST").isEmpty()) {
            ACTIVEMQHOST = System.getenv("ACTIVE_MQ_HOST");
        }
        LOG.info("ACTIVE_MQ_HOST = " + ACTIVEMQHOST);

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // print logback's internal status
        StatusPrinter.print(lc);

        System.out.println("Started, now running until 'q' is entered on stdin...");

        //TODO queue name as cmd line arg
        try {
            messenger.createConnection("MPF.DETECTION_JAVAHELLOWORLD_REQUEST");
        } catch (JMSException e) {
            e.printStackTrace();
        }

        while (isMessengerAvailable) {
            try {
                if (System.in.available() != 0) {
                    if ((char) (System.in.read()) == 'q') break;
                }
            } catch (IOException e) {
                LOG.error("Error reading keyboard input due to an Exception ", e);
                e.printStackTrace(System.err);
            }
            try {
                Thread.sleep(1000);                 //1000 milliseconds is one second.
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Stopping...");
        messenger.shutdown();
        System.exit(0);

    }

}

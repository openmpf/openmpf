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

package org.mitre.mpf.component.executor.detection;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import org.mitre.mpf.component.api.detection.MPFDetectionComponentBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.jms.JMSException;
import java.io.IOException;

public class MPFDetectionMain {

    public static String ACTIVEMQHOST = "tcp://localhost:61616";

    private static final Logger LOG = LoggerFactory.getLogger(MPFDetectionMain.class);

    public static void main(String[] args) {

        MPFDetectionMessenger detectionMessenger = null;
        MPFDetectionComponentBase componentBase = null;
        String msgQueueName = null;

        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext.xml");

        componentBase = (MPFDetectionComponentBase) applicationContext.getBean("component");
        LOG.info("Found component class {}", componentBase.getClass().getName());

        if (args.length >0 ) {
            msgQueueName = args[0];
        } else  {
            LOG.error("Must provide message queue name as the first argument.");
        }
        if (args.length > 1) {
            ACTIVEMQHOST = args[1];
        } else if (System.getenv("ACTIVE_MQ_HOST") != null && !System.getenv("ACTIVE_MQ_HOST").isEmpty()) {
            ACTIVEMQHOST = System.getenv("ACTIVE_MQ_HOST");
        }
        LOG.info("ACTIVE_MQ_HOST = " + ACTIVEMQHOST);

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        // print logback's internal status
        StatusPrinter.print(lc);
        System.out.println("Started, now running until 'q' is entered on stdin...");

        try {
            if (componentBase!=null && msgQueueName!=null) {
                //TODO: Add a parameter for run directory and set it here.
                componentBase.init();
                detectionMessenger = new MPFDetectionMessenger(componentBase, msgQueueName);

                LOG.info("Created messenger");
            } else {
                LOG.error("Could not create detection messenger.");
            }
        } catch (JMSException e) {
            LOG.error("Failed to initialize the component for " + msgQueueName + " due to an Exception ", e);
            e.printStackTrace();
        }

        if (detectionMessenger!=null) {
            boolean keepRunning = true;
            while (keepRunning) {
                try {
                    if (System.in.available() != 0) {
                        if ((char) (System.in.read()) == 'q') keepRunning=false;
                    }
                } catch (IOException e) {
                    LOG.error("Error reading keyboard input due to an Exception ", e);
                    e.printStackTrace(System.err);
                }
                try {
                    Thread.sleep(1000);                 //1000 milliseconds is one second.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
		
        LOG.info("Stopping...");
        if (componentBase != null) {
            componentBase.close();
        }
        if (detectionMessenger != null) {
            detectionMessenger.shutdown();
        }
        System.exit(0);

    }

}

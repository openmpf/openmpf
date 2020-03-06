/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.connection.CachingConnectionFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MarkupMain {
    private static final Logger LOG = LoggerFactory.getLogger(org.mitre.mpf.markup.MarkupRequestConsumer.class);
    public static String ACTIVEMQHOST = "tcp://localhost:61616";

    /**
     * Main method that starts MarkupMain by loading the application context and initializing the LevelDB instances
     *
     * @param args Command line arguments, should be empty
     * @throws InterruptedException
     */
    public static void main(String[] args) {
        LOG.info("Beginning markup initialization");

        if (args.length > 0) {
            ACTIVEMQHOST = args[0];
        } else if (System.getenv("ACTIVE_MQ_BROKER_URI") != null && !System.getenv("ACTIVE_MQ_BROKER_URI").isEmpty()) {
            ACTIVEMQHOST = System.getenv("ACTIVE_MQ_BROKER_URI");
        }
        LOG.trace("ACTIVE_MQ_BROKER_URI=" + ACTIVEMQHOST);

        var context = new ClassPathXmlApplicationContext("classpath:appConfig.xml");
        context.registerShutdownHook();

        var connection = context.getBean("jmsFactory", CachingConnectionFactory.class);

        // Shutdown hook is required in order to shutdown when signal received.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(connection, context)));

        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOG.info("Received input on stdin: \"{}\"", line);
                if (line.startsWith("q")) {
                    shutdown(connection, context);
                    return;
                }
            }

            LOG.info("Standard in was closed. Must use a signal to exit.");
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }


    private static void shutdown(CachingConnectionFactory connection, ConfigurableApplicationContext context) {
        connection.destroy();
        context.close();
    }
}

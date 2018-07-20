/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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
    public static void main(String[] args) throws InterruptedException {
        LOG.info("Beginning markup initialization");

        if (args.length > 0) {
            ACTIVEMQHOST = args[0];
        } else if (System.getenv("ACTIVE_MQ_HOST") != null && !System.getenv("ACTIVE_MQ_HOST").isEmpty()) {
            ACTIVEMQHOST = System.getenv("ACTIVE_MQ_HOST");
        }
        LOG.trace("ACTIVE_MQ_HOST=" + ACTIVEMQHOST);

        try (ClassPathXmlApplicationContext context
                     = new ClassPathXmlApplicationContext("classpath:appConfig.xml")) {

            context.registerShutdownHook();
            CachingConnectionFactory connection = context.getBean("jmsFactory", CachingConnectionFactory.class);

            System.out.println("Enter 'q' to quit:");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.info("Received input on stdin: \"{}\"", line);
                    if (line.startsWith("q")) {
                        break;
                    }
                }
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
            connection.destroy();
        }
    }
}

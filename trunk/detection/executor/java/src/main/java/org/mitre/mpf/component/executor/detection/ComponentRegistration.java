/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComponentRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(ComponentRegistration.class);


    private ComponentRegistration() {
    }

    public static void register(Session session) throws JMSException, IOException {
        if (shouldSkipRegistration()) {
            return;
        }

        TemporaryQueue responseQueue = null;
        MessageConsumer responseConsumer = null;
        MessageProducer requestProducer = null;
        try {
            LOG.info("Starting component registration.");
            responseQueue = session.createTemporaryQueue();
            responseConsumer = session.createConsumer(responseQueue);
            requestProducer = session.createProducer(
                    session.createQueue("MPF.DETECTION_COMPONENT_REGISTRATION"));

            var message = session.createTextMessage(loadDescriptor());
            message.setJMSReplyTo(responseQueue);
            requestProducer.send(message);
            session.commit();

            var response = responseConsumer.receive();
            session.commit();
            checkResponse(response);
        }
        finally {
            if (requestProducer != null) {
                requestProducer.close();
            }
            if (responseConsumer != null) {
                responseConsumer.close();
            }
            if (responseQueue != null) {
                responseQueue.delete();
            }
        }
    }


    private static boolean shouldSkipRegistration() {
        return Optional.ofNullable(System.getenv("DISABLE_COMPONENT_REGISTRATION"))
            .filter(e -> !e.isEmpty() && !e.equalsIgnoreCase("false"))
            .isPresent();
    }


    private static void checkResponse(Message response) {
        boolean wasSuccessful;
        try {
            wasSuccessful = response.getBooleanProperty("success");
        }
        catch (JMSException e) {
            wasSuccessful = false;
        }

        String details;
        try {
            details = response.getStringProperty("detail");
        }
        catch (JMSException e) {
            details = null;
        }

        if (wasSuccessful) {
            if (details == null) {
                LOG.info("Successfully registered component.");
            }
            else {
                LOG.info("Successfully registered component. Response from server: {}", details);
            }
        }
        else {
            if (details == null) {
                throw new IllegalStateException("Registration failed with no details.");
            }
            else {
                throw new IllegalStateException("Registration failed with response: " + details);
            }
        }
    }

    private static String loadDescriptor() throws IOException {
        return Files.readString(getDescriptorPath());
    }


    private static Path getDescriptorPath() {
        return getDescriptorPathFromEnvVar()
            .or(() -> getDescriptorPathFromComponentName())
            .orElseGet(() -> findOnlyDescriptor());
    }

    private static Optional<Path> getDescriptorPathFromEnvVar() {
        var envPath = System.getenv("DESCRIPTOR_PATH");
        if (envPath == null || envPath.isBlank()) {
            return Optional.empty();
        }
        var path = Path.of(envPath);
        if (Files.exists(path)) {
            return Optional.of(path);
        }
        throw new IllegalStateException(
                "The \"DESCRIPTOR_PATH\" environment variable was set to \"%s\", but that file does not exist."
                .formatted(envPath));
    }

    private static Optional<Path> getDescriptorPathFromComponentName() {
        var componentName = System.getenv("COMPONENT_NAME");
        if (componentName == null) {
            return Optional.empty();
        }

        var path = getMpfHome()
                .resolve("plugins")
                .resolve(componentName)
                .resolve("descriptor/descriptor.json");
        if (Files.exists(path)) {
            return Optional.of(path);
        }
        else {
            return Optional.empty();
        }
    }

    private static Path findOnlyDescriptor() {
        var pluginsDir = getMpfHome().resolve("plugins");
        try (var dirStream = Files.newDirectoryStream(
                    pluginsDir, "*/descriptor/descriptor.json")) {
            var dirIter = dirStream.iterator();
            if (!dirIter.hasNext()) {
                throw new IllegalStateException(
                        "Could not find a descriptor file. Set the \"DESCRIPTOR_PATH\" "
                        + "environment variable to the path of the descriptor file that should be used.");
            }
            var firstMatch = dirIter.next();
            while (dirIter.hasNext()) {
                if (!Files.isSameFile(firstMatch, dirIter.next())) {
                    throw new IllegalStateException(
                            "Multiple descriptor files were found. Set the \"DESCRIPTOR_PATH\" " +
                            "environment variable to the path of the descriptor that should be used. ");
                }
            }
            return firstMatch;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path getMpfHome() {
        return Optional.ofNullable(System.getenv("MPF_HOME"))
            .filter(s -> !s.isBlank())
            .map(Path::of)
            .orElseGet(() -> Path.of("/opt/mpf"));
    }
}

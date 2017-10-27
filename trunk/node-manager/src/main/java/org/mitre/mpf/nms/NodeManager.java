/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2017 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2017 The MITRE Corporation                                       *
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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.URI;

@Component
public class NodeManager implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(NodeManager.class);

    private final NodeManagerProperties properties;

    private final ChildNodeStateManager nodeStateManager;


    @Autowired
    public NodeManager(NodeManagerProperties properties, ChildNodeStateManager nodeStateManager) {
        this.properties = properties;
        this.nodeStateManager = nodeStateManager;
    }

    @Override
    public void run() {
        nodeStateManager.startReceiving(ChannelReceiver.NodeTypes.NodeManager, "NodeManager");
        initHttpServer();
        nodeStateManager.run();
        shutdown();
    }


    private void initHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(properties.getNodeStatusHttpPort()), 0);
            server.createContext("/", this::handle);
            server.start();
        }
        catch (IOException e) {
            LOG.error("Could not setup HTTP debug service: ", e);
        }
    }


    private void handle(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();

        // Special case for handling a command request
        URI uri = exchange.getRequestURI();
        if (uri.getPath().compareTo("/shutdown") == 0 && uri.getQuery() != null) {
            handleHttpShutdownRequest(exchange);
            return;
        }

        headers.set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, 0);

        StatusViewContext viewContext = new StatusViewContext(nodeStateManager, properties.getNodeStatusHttpPort());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(exchange.getResponseBody()))) {
            renderMustacheView(writer, viewContext);
        }
    }


    private void handleHttpShutdownRequest(HttpExchange exchange) throws IOException {
        String simpleResponse;
        String tv[] = exchange.getRequestURI().getQuery().split("=");  // simple one-entry split - don't go overboard
        if (tv[0].compareTo("id") == 0) {
            nodeStateManager.shutdown(nodeStateManager.getServiceTable().get(tv[1]), false, false);
            simpleResponse = "Shutdown " + tv[1] + "\nUse back button to return to main page";
        }
        else {
            simpleResponse = "Shutdown requests must be of the form /shutdown/id=ServiceName";
        }

        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, 0);
        try (Writer writer = new OutputStreamWriter(exchange.getResponseBody())) {
            writer.append(simpleResponse);
        }
    }



    private static Mustache COMPILED_TEMPLATE;

    private static void renderMustacheView(Writer writer, Object viewContext) {
        if (COMPILED_TEMPLATE == null) {
            MustacheFactory mf = new DefaultMustacheFactory();
            COMPILED_TEMPLATE = mf.compile("node-status-view.mustache");
        }
        COMPILED_TEMPLATE.execute(writer, viewContext);
    }


    private void shutdown() {
        nodeStateManager.shutdown();
    }


    public static void main(String[] args) {
        LOG.info("NodeManager Started");

        // Log that we are being shutdown, but more hooks are found during process launches in BaseNodeLauncher
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> LOG.info("Service shutdown")));

        try (ClassPathXmlApplicationContext context
                     = new ClassPathXmlApplicationContext("applicationContext.xml", NodeManager.class)) {
            context.getBean(NodeManager.class).run();
        }
    }
}

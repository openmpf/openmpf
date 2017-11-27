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
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;

@Component
public class NodeStatusHttpServer {

	private static final Logger LOG = LoggerFactory.getLogger(NodeStatusHttpServer.class);

	private final NodeManagerProperties properties;

	private final ChildNodeStateManager nodeStateManager;


	@Autowired
	public NodeStatusHttpServer(NodeManagerProperties properties, ChildNodeStateManager nodeStateManager) {
		this.properties = properties;
		this.nodeStateManager = nodeStateManager;
	}


	public void start() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(properties.getNodeStatusHttpPort()), 0);
			server.createContext("/", this::handle);
			server.start();
			LOG.info("Started node status debug page on port {}", server.getAddress().getPort());
		}
		catch (IOException e) {
			LOG.error("Could not setup HTTP debug service: ", e);
		}
	}


	private void handle(HttpExchange exchange) throws IOException {
		Headers headers = exchange.getResponseHeaders();
		headers.set("Content-Type", "text/html");
		exchange.sendResponseHeaders(200, 0);

		StatusViewContext viewContext = new StatusViewContext(nodeStateManager, properties.getNodeStatusHttpPort());

		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(exchange.getResponseBody()))) {
			renderMustacheView(writer, viewContext);
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
}

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Properties;

public class NodeManager implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(NodeManager.class);

    private final ChildNodeStateManager nodeStateManager;

    private static int HTTP_PORT;


    /**
     * Constructor
     *
     * @param jgroupsConfigXML
     * @param channelName
     * @param description Our JGroups logical entity name will include our
     * hostname and this description (can be null)
     */
    public NodeManager(InputStream jgroupsConfigXML, String channelName, String description) {
        nodeStateManager = new ChildNodeStateManager();
        nodeStateManager.startReceiving(jgroupsConfigXML, channelName, ChannelReceiver.NodeTypes.NodeManager,
                                        description);
    }

    @Override
    public void run() {
        initHttpServer();
        nodeStateManager.run();
    }


    private void initHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
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

        StatusViewContext test = new StatusViewContext(
                nodeStateManager.getMessageChannel().getAddress(),
                nodeStateManager.getMessageChannel().getChannel().getView().getMembers(),
                nodeStateManager.getNodeTable().values(),
                nodeStateManager.getServiceTable().values());


        exchange.sendResponseHeaders(200, 0);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(exchange.getResponseBody()))) {
            renderMustacheView(writer, test);
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


    public static int getHttpPort() {
        return HTTP_PORT;
    }


    public void shutdown() {
        nodeStateManager.shutdown();
    }

    /**
     * MAIN.
     *
     * @param args
     */
    public static void main(String[] args) {

        LOG.info("NodeManager Started");
    	
    	String resourcePath = "properties/nm.properties";
    	Resource resource = new ClassPathResource(resourcePath);
    	try {
			Properties props = PropertiesLoaderUtils.loadProperties(resource);
			int minServiceTimeupMillis = Integer.parseInt(props.getProperty("min.service.timeup.millis", "60000"));
            ChildNodeStateManager.setMinServiceTimeUpMillis(minServiceTimeupMillis);
            HTTP_PORT = Integer.parseInt(props.getProperty("node.status.http.port", "8008"));
		}
		catch (IOException e) {
			LOG.error("Failed to retrieve node manager properties from resource path '{}' with exception {} {}.", resourcePath, e.getMessage(), e);
		}
    	
        // Log that we are being shutdown, but more hooks are found during process launches in BaseNodeLauncher
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> LOG.info("Service shutdown")));

        String host = System.getenv("THIS_MPF_NODE");
        LOG.debug("Hostname is: '{}'.", host);
        if(host == null) {
            LOG.error("Could not determine the hostname, NodeManager will exit.");
            System.exit(1);
        }

        // TODO: find a better place or pull from Props
        String jgConfig = System.getProperty(NodeManagerConstants.JGROUPS_CONFIG_PARAM, "jGroupsTCPConfig.xml");
        InputStream jgIs = NodeManager.class.getClassLoader().getResourceAsStream(jgConfig);
        String channel = NodeManagerConstants.DEFAULT_CHANNEL;

        String workingDir = System.getProperty("user.dir");
        LOG.debug("Working dir is {}", workingDir);

        if (args.length >= 1 && "master".equals(args[0])) {
            try {
                LOG.info("I am going to be a test master now");

                LOG.debug("Reading config file");
                String activeMqHostname = System.getProperty("ACTIVE_MQ_HOST");
                if (activeMqHostname == null) {
                    activeMqHostname = "failover://(tcp://localhost:61616)?jms.prefetchPolicy.all=1&startupMaxReconnectAttempts=1";
                }

                InputStream nodeManagerConfig = null;
                if (args.length >= 2) {
                    LOG.debug("Reading config file: %s\n", args[1]);
                    nodeManagerConfig = new FileInputStream(new File(args[1]));
                } else {
                    LOG.error("Please specify a config file!");
                    System.exit(-1);
                }

                //masterNode.loadConfigFile(nodeManagerConfig, activeMqHostname);
                MasterNode masterNode = new MasterNode(nodeManagerConfig, activeMqHostname, jgIs, NodeManagerConstants.DEFAULT_CHANNEL, "MasterTest");
                // Provide a sample callback interface/class so we can monitor changes. In real life, handle this
                // in your class directly, we are powerless here in main()...
                SampleCallback cb = new SampleCallback();
                masterNode.setCallback(cb);

                LOG.debug("Launching and waiting for completion");
                masterNode.launchAllNodes();

                /**
                 * System.out.println("Sleeping 10 seconds before shutdown");
                 * Thread.sleep(10000); masterNode.shutdownAllNodes();
                 */
                LOG.info("Sleeping forever: Hit Return to shutdown system or Ctrl-C to exit leaving nodes running...");
                try {
                    System.in.read();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    LOG.error("There was an error. {} {}", e.getMessage(), e);
                }
                LOG.debug("Shutting down...");
                masterNode.shutdown();
            } catch (Exception e) {
                LOG.error("There was an error. {} {}", e.getMessage(), e);
            }

            LOG.info("Goodbye!");
            System.exit(0);
        } else {
            LOG.info("Starting up as a NodeManager on " + host);
            // DEFAULT MODE: Run as a NodeManager
            //String name = NodeManagerConstants.NODE_MANAGER_TAG + ":" + host;

            // instantiate, startup jgroups, wait for something to happen
            NodeManager mgr = new NodeManager(jgIs, channel, "NodeManager");
            try {
                jgIs.close();
            } catch (IOException e) {
                LOG.error("There was an error. {} {}", e.getMessage(), e);
            }
            mgr.run();
            mgr.shutdown();
        }
    }
}

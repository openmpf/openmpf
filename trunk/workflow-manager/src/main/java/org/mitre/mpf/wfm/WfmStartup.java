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

package org.mitre.mpf.wfm;

import org.apache.activemq.broker.jmx.BrokerViewMBean;
import org.apache.activemq.broker.jmx.QueueViewMBean;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateStreamingJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.SystemMessage;
import org.mitre.mpf.wfm.service.MpfService;
import org.mitre.mpf.wfm.service.component.StartupComponentRegistrationService;
import org.mitre.mpf.wfm.service.ServerMediaService;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.servlet.ServletContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class WfmStartup implements ApplicationListener<ApplicationEvent> {
	private static final Logger log = LoggerFactory.getLogger(WfmStartup.class);

	@Autowired
	@Qualifier(HibernateJobRequestDaoImpl.REF)
	private HibernateJobRequestDao jobRequestDao;

	@Autowired
	@Qualifier(HibernateStreamingJobRequestDaoImpl.REF)
	private HibernateStreamingJobRequestDao streamingJobRequestDao;

	@Autowired
	private MpfService mpfService;

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private StartupComponentRegistrationService startupRegistrationService;

	@Autowired
	private ServerMediaService serverMediaService;

	// used to prevent the initialization behaviors from being executed more than once
	private static boolean applicationRefreshed = false;

	private ExecutorService executorService = null;

	@Override
	public void onApplicationEvent(ApplicationEvent event) {

		if (event instanceof ContextRefreshedEvent) {
			// this callback will be invoked at least twice: once for the root /workflow-manager, and
			// once for /workflow-manager/appServlet

			ContextRefreshedEvent contextRefreshedEvent = (ContextRefreshedEvent) event;
			ApplicationContext appContext = contextRefreshedEvent.getApplicationContext();

			if (!applicationRefreshed) {
				log.info("onApplicationEvent: " + appContext.getDisplayName() + " " + appContext.getId()); // DEBUG

				log.info("Marking any remaining running batch jobs as CANCELLED.");
				jobRequestDao.cancelJobsInNonTerminalState();

        log.info("Marking any remaining running streaming jobs as CANCELLED.");
        streamingJobRequestDao.cancelJobsInNonTerminalState();

				if (propertiesUtil.isAmqBrokerEnabled()) {
					try {
						log.info("Purging MPF-owned ActiveMQ queues...");
						purgeQueues();
					} catch (Exception exception) {
						throw new RuntimeException("Failed to purge the MPF ActiveMQ queues.", exception);
					}
				}

				purgeServerStartupSystemMessages();
				startFileIndexing(appContext);
				startupRegistrationService.registerUnregisteredComponents();
				applicationRefreshed = true;
			}
		} else if (event instanceof ContextClosedEvent) {
			stopFileIndexing();
		}
	}

	private void startFileIndexing(ApplicationContext appContext)  {
		if (appContext instanceof WebApplicationContext) {
			WebApplicationContext webContext = (WebApplicationContext) appContext;
			ServletContext servletContext = webContext.getServletContext();
			executorService = Executors.newSingleThreadExecutor();
			executorService.execute(() -> serverMediaService.getFiles(propertiesUtil.getServerMediaTreeRoot(), servletContext, true, true));
			executorService.shutdown(); // will run all tasks before shutdown
		}
	}

	private void stopFileIndexing() {
		if (executorService != null) {
			executorService.shutdownNow();
		}
	}

	/** purge system messages that are set to be removed on server startup */
	private void purgeServerStartupSystemMessages() {
		log.info("WfmStartup.purgeServerStartupSystemMessages()");
		List<SystemMessage> msgs = mpfService.getSystemMessagesByRemoveStrategy("atServerStartup");
		for (SystemMessage m : msgs) {
			long id = m.getId();
			mpfService.deleteSystemMessage(id);
			log.info("removed System Message #" + id + ": '" + m.getMsg() + "'");
		}
	}

	private void purgeQueues() throws Exception {
		Map<String, Object> map = new HashMap<>();
		map.put(JMXConnector.CREDENTIALS, new String[]{propertiesUtil.getAmqBrokerAdminUsername(), propertiesUtil.getAmqBrokerAdminPassword()});
		JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(propertiesUtil.getAmqBrokerUri()));
		connector.connect();
		MBeanServerConnection mBeanServerConnection = connector.getMBeanServerConnection();
		ObjectName activeMQ = new ObjectName("org.apache.activemq:brokerName=localhost,type=Broker");
		BrokerViewMBean mbean = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, activeMQ, BrokerViewMBean.class, true);
		Set<String> whitelist = propertiesUtil.getAmqBrokerPurgeWhiteList();
		log.debug("Whitelist contains {} queues: {}", whitelist.size(), whitelist);
		for (ObjectName name : mbean.getQueues()) {
			QueueViewMBean queueMbean = MBeanServerInvocationHandler.newProxyInstance(mBeanServerConnection, name, QueueViewMBean.class, true);
			if(!whitelist.contains(queueMbean.getName()) &&
					queueMbean.getName().startsWith("MPF.")) {
				log.info("Purging {}", queueMbean.getName());
				queueMbean.purge();
			}
		}
	}
}
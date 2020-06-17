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

package org.mitre.mpf.mvc.model;

/** channels for the Atmosphere-based server side push service
 *  In the comments below, the events for each channel is listed under the channel name.
 *  For more info on how Atmosphere is used in workflow-manager, see the documentation for AtmosphereController.
 *  NOTE: be sure to add a dispatcher in the client (in services.js' request.onMessage())
 *  	for each type described in the enum
 */
public enum AtmosphereChannel {
	/** SSPC_ATMOSPHERE: initial handshake, etc. 
	 * 	Event(s):
	 * 		OnConnected - when Atmosphere first connects
	 * 		OnCancelled - when unexpected disconnection happens
	 * 		OnClosedByClient - when client closes connection */
	SSPC_ATMOSPHERE,

	/** SSPC_COMPONENT: component related events
	 *  notifications for completing component uploads, registration, and removal/unregistration
	 *  Event(s):
	 * 	OnComponentStateChanged - went from state 's' to new state something different than 's' with no errors
	 *  */
	SSPC_COMPONENT,

	/** SSPC_HEARTBEAT: heartbeat signal from org.atmosphere.interceptor.HeartbeatInterceptor
	 *  Note that this is sent through the interceptor, and so the content does not follow the format
	 *  of other channels.  On the client side, the content is conformed to the format of other
	 *  channels for consistency of processing
	 * 	Event(s):	None, since it is not within our control */
	SSPC_HEARTBEAT,
	
	/** SSPC_JOBSTATUS: job related 
	 * 	Event(s):
	 * 		OnStatusChanged - when the status of the job changed */
	SSPC_JOBSTATUS,

	/** SSPC_NODE: node related events
	 *  note this differs from previous event notifications where SSPC_NODE and SSPC_SERVICE were all part of a single web service
	 *  Events(s):
	 * 		OnNewManager - when a node manager is started
	 * 		OnManagerDown - when a node manager is stopped
	 * 	Event(s) not yet implemented:
	 * 		OnNodeConfigurationChanged - ???
	 */
	SSPC_NODE,

	/** SSPC_SERVICE: services (which runs in nodes) related events
	 *  note this differs from previous event notifications where SSPC_NODE and SSPC_SERVICE were all part of a single web service
	 *  	all events are defined in mpf/wfm/nodeManager/NodeManagerStatus
	 * 	Event(s):
	 * 		OnNewService - when a new service is configured, started, and ready to handle tasks (this is "new" in the sense that it is "on" as opposed to "off", not necessarily that it is a newly installed service)
	 * 		OnServiceChange - when the state of a service has changed (***defined, but not observed in the wild***)
	 * 		OnServiceDown - when a service has been stopped
	 * 		OnServiceReadyToRemove - when a service is ready to be removed from the NodeManager (i.e., when it is removed from the node configuration)
	 */
	SSPC_SERVICE,

	/** SSPC_SESSION: session-related events
	 *  Event(s):
	 *  	OnSessionAboutToTimeout - when session is about to time out
	 *  	OnSessionExtendedByUser - when session is extended by user
	 *  	OnSessionExpired - when session has expired
	 */
	SSPC_SESSION,

	/** SSPC_SYSTEMMESSAGE: SystemMessage notification events
	 * 	Event(s):
	 * 		OnSystemMessagesChanged - when SystemMessages are created/updated/deleted
	 * 									Note that for efficiency, this event only returns the type of the messages chagned, and not the list.
	 * 									You can think of the System Message type as queues, and this essentially tells you which queue
	 * 									has changed.  The client then needs to do a /rest/system-message/{typeFilter} to get the new list
	 * 									of system messages of that type
	 */
	SSPC_SYSTEMMESSAGE,

	SSPC_PROPERTIES_CHANGED
}

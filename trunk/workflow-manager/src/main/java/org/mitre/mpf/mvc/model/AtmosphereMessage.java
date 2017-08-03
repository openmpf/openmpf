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

package org.mitre.mpf.mvc.model;

import java.util.Date;
import java.util.HashMap;

/** Generic Message class for server-side push using Atmosphere.  For more information on 
 *  how Atmosphere is used in workflow-manager, please see the documentation for AtmosphereController */
public class AtmosphereMessage {
	
	/** channel name - see AtmosphereChannel.java for details */
	private AtmosphereChannel channel;
	
	/** event name - see AtmosphereChannel.java for details */
	private String event;
	
	/** timestamp for when the message is created, which may be different than when the event happened or when 
	 * it is broadcasted) */
	private Date timestamp;
	
	/** the JSON content of the message, stored as a String representation of JSON */
	private HashMap<String,Object> content;
	
	protected AtmosphereMessage( AtmosphereChannel channel, String event )  {
		this.channel = channel;
		this.event = event;
		this.timestamp = new Date();
	}	

	public AtmosphereMessage( AtmosphereChannel channel, String event, HashMap dataMap ) {
		this( channel, event );
		this.setContent( dataMap );
	}

	public AtmosphereChannel getChannel() {
		return channel;
	}

	public String getEvent() {
		return event;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public HashMap<String,Object> getContent() {
		return content;
	}

	public void setContent(HashMap<String,Object> dataMap) {
		this.content = dataMap;
	}
	
	@Override
	public String toString() {
		return "AtmosphereMessage [channel=" + channel + ", event=" + event + ", timestamp=" + timestamp + ", content="
				+ content + "]";
	}

}

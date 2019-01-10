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

package org.mitre.mpf.mvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.atmosphere.config.managed.Encoder;
import org.mitre.mpf.mvc.model.AtmosphereMessage;
import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;

//import org.mitre.mpf.mvc.controller.Message;

//can encode and decode atmosphere websocket traffic when configured
public class JacksonEncoder implements Encoder<AtmosphereMessage, String>/*, Decoder<String, AtmosphereMessage>*/ {

    @Inject
    private ObjectMapper mapper; //TODO: ObjectMapper not working - not injecting :(
    
    private final Logger logger = LoggerFactory.getLogger(JacksonEncoder.class);

    @Override
    public String encode(AtmosphereMessage m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (IOException e) {
            String s = "Error encoding JSON: " + e.getMessage();
            logger.error("Error: {} {}", s, e);
            return s;
        }
    }

//	@Override
//	public AtmosphereMessage decode(String s) {
//        try {
//            return mapper.readValue(s, AtmosphereMessage.class);
//        } catch (IOException e) {
//        	logger.error("Error decoding JSON: {} {}", e.getMessage(), e);
//        	return null;
//        }
//	}
	
	@PostConstruct //solves the current injection issue
	private void init() {
		if(mapper == null) {
			mapper = ObjectMapperFactory.customObjectMapper();
		}
	}
}

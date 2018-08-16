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

package org.mitre.mpf.wfm.camel.operations.mediaretrieval;

import org.apache.camel.Exchange;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.WfmProcessor;
import org.mitre.mpf.wfm.data.Redis;
import org.mitre.mpf.wfm.data.RedisImpl;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** This processor downloads a file from a remote URI to the local filesystem. */
@Component(RemoteMediaProcessor.REF)
public class RemoteMediaProcessor extends WfmProcessor {
	public static final String REF = "remoteMediaProcessor";
	private static final Logger log = LoggerFactory.getLogger(RemoteMediaProcessor.class);

	@Autowired
	@Qualifier(RedisImpl.REF)
	private Redis redis;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		assert exchange.getIn().getBody() != null : "The body must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body must be convertible to String.";

		TransientMedia transientMedia = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TransientMedia.class);
		// log.debug("Retrieving Media {} and storing it at '{}'.", transientMedia.getId(), transientMedia.getLocalPath());

		switch(transientMedia.getUriScheme()) {
			case FILE:
			case HTTP:
			case HTTPS:
				/*
				File localFile = null;
				try {
					localFile = new File(transientMedia.getLocalPath());
					FileUtils.copyURLToFile(new URL(transientMedia.getUri()), localFile);
					log.debug("Successfully retrieved Media {} and saved it to '{}'.", transientMedia.getId(), transientMedia.getLocalPath());
					transientMedia.setFailed(false);
				} catch (Exception exception) {
					log.warn("Failed to retrieve Media {}.", transientMedia.getId());
					transientMedia.setFailed(true);
					transientMedia.setMessage("Error writing media to temp file - " + exception.toString());

					// Try to delete the local file, but do not throw an exception if this operation fails.
					deleteOrLeakFile(localFile);
				}
				*/
				// Do nothing.
				break;
			default:
				log.warn("The UriScheme '{}' was not expected at this time.");
				transientMedia.setFailed(true);
				transientMedia.setMessage(String.format("The scheme '%s' was not expected or does not have a handler associated with it.", transientMedia.getUriScheme()));
				break;
		}

		redis.persistMedia(exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class), transientMedia);

		exchange.getOut().getHeaders().put(MpfHeaders.CORRELATION_ID, exchange.getIn().getHeader(MpfHeaders.CORRELATION_ID));
		exchange.getOut().getHeaders().put(MpfHeaders.SPLIT_SIZE, exchange.getIn().getHeader(MpfHeaders.SPLIT_SIZE));
		exchange.getOut().getHeaders().put(MpfHeaders.JMS_PRIORITY, exchange.getIn().getHeader(MpfHeaders.JMS_PRIORITY));
		exchange.getOut().setBody(jsonUtils.serialize(transientMedia));
	}

	/*
	private void deleteOrLeakFile(File file) {
		try {
			if(file != null) {
				file.delete();
			}
		} catch(Exception exception) {
			log.warn("Failed to delete the local file '{}'. If it exists, it must be deleted manually.", file);
		}
	}
	*/
}

/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel.routes;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.mitre.mpf.wfm.ActiveMQConfiguration;
import org.mitre.mpf.wfm.camel.JobCompleteProcessorImpl;
import org.mitre.mpf.wfm.camel.operations.mediaretrieval.RemoteMediaProcessor;
import org.mitre.mpf.wfm.camel.operations.mediaretrieval.RemoteMediaSplitter;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.springframework.stereotype.Component;

@Component
public class MediaRetrieverRouteBuilder extends RouteBuilder {
	public static final String ENTRY_POINT = "activemq:MPF.MEDIA_RETRIEVAL";
	public static final String EXIT_POINT = MediaInspectionRouteBuilder.ENTRY_POINT;
	public static final String ROUTE_ID = "Media Retriever Route";

	private final String entryPoint, exitPoint;

	/** Default constructor (for use in production). */
	public MediaRetrieverRouteBuilder() {
		this.entryPoint = ENTRY_POINT;
		this.exitPoint = EXIT_POINT;
	}

	/** This constructor may be used during testing to control the source and destination of messages. */
	public MediaRetrieverRouteBuilder(String entryPoint, String exitPoint) {
		this.entryPoint = entryPoint;
		this.exitPoint = exitPoint;
	}

	@Override
	public void configure() {
        from(entryPoint)
            .routeId(ROUTE_ID)
            .setExchangePattern(ExchangePattern.InOnly)
            .split().method(RemoteMediaSplitter.REF, "split")
                .parallelProcessing()
                .executorServiceRef(ActiveMQConfiguration.SPLITTER_THREAD_POOL_REF)
                .streaming()
                .process(RemoteMediaProcessor.REF)
            .end()
            .choice()
                .when(exchangeProperty(MpfHeaders.SPLITTING_ERROR))
                    .process(JobCompleteProcessorImpl.REF)
                .otherwise()
                    .to(exitPoint)
            .endChoice();
	}
}

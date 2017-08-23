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

package org.mitre.mpf.wfm.camel.operations.markup;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mitre.mpf.wfm.buffers.Markup;
import org.springframework.stereotype.Component;

@Component(MarkupCancellationProcessor.REF)
public class MarkupCancellationProcessor implements Processor {
    public static final String REF = "markupCancellationProcessor";

    public void process(Exchange exchange) throws Exception {
        // Copy the headers from the incoming message to the outgoing message.
        exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());

        Markup.MarkupRequest request = Markup.MarkupRequest.parseFrom(exchange.getIn().getBody(byte[].class));

        exchange.getOut().setBody(
            Markup.MarkupResponse.newBuilder()
                .setMediaIndex(request.getMediaIndex())
                .setTaskIndex(request.getTaskIndex())
                .setActionIndex(request.getActionIndex())
                .setMediaId(request.getMediaId())
                .setRequestId(request.getRequestId())
                .setHasError(true)
                .setErrorMessage("This request was cancelled.")
                .build()
                .toByteArray()
        );
    }

}

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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionSplitter;
import org.mitre.mpf.wfm.data.entities.transients.TransientJob;
import org.mitre.mpf.wfm.data.entities.transients.TransientStage;
import org.mitre.mpf.wfm.enums.ActionType;
import org.mitre.mpf.wfm.camel.operations.markup.MarkupStageSplitter;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Given that all actions in a stage/task share a common operation type (e.g., DETECTION), this class is used to
 * call the correct operation-specific splitter. If an unrecognized operation is associated with the stage, this
 * splitter will return an empty split so that the job will continue without hanging or throwing an exception.
 */
@Component(DefaultStageSplitter.REF)
public class DefaultStageSplitter extends WfmSplitter implements StageSplitter {
	private static final Logger log = LoggerFactory.getLogger(DefaultStageSplitter.class);
	public static final String REF = "defaultStageSplitter";

	@Autowired
	@Qualifier(DetectionSplitter.REF)
	private StageSplitter detectionStageSplitter;

	@Autowired
	@Qualifier(MarkupStageSplitter.REF)
	private StageSplitter markupStageSplitter;

	@Autowired
	private JsonUtils jsonUtils;

	@Override
	public String getSplitterName() { return REF; }

	public List<Message> performSplit(TransientJob transientJob, TransientStage transientStage) throws Exception {
		log.warn("[Job {}|{}|*] Stage {} calls an unsupported operation '{}'. No work will be performed in this stage.",
				transientJob.getId(), transientJob.getCurrentStage(), transientJob.getCurrentStage(), transientStage.getName());
		return new ArrayList<Message>(0);
	}

	@Override
	public final List<Message> wfmSplit(Exchange exchange) throws Exception {
		assert exchange.getIn().getBody() != null : "The body of the message must not be null.";
		assert exchange.getIn().getBody(byte[].class) != null : "The body of the message must be a byte[].";

		TransientJob transientJob = jsonUtils.deserialize(exchange.getIn().getBody(byte[].class), TransientJob.class);
		TransientStage transientStage = transientJob.getPipeline().getStages().get(transientJob.getCurrentStage());
		log.info("[Job {}|{}|*] Stage {}/{} - Operation: {} - ActionType: {}.",
				transientJob.getId(),
				transientJob.getCurrentStage(),
				transientJob.getCurrentStage() + 1,
				transientJob.getPipeline().getStages().size(),
				transientJob.getPipeline().getStages().get(transientJob.getCurrentStage()).getActionType(),
				transientStage.getActionType().name());

		if(transientJob.isCancelled()) {
			// Check if this job has been cancelled prior to performing the split. If it has been, do not produce any work units.
			log.warn("[Job {}|{}|*] This job has been cancelled. No work will be performed in this stage.",
					transientJob.getId(), transientJob.getCurrentStage(), transientJob.getCurrentStage(), transientStage.getName());
			return new ArrayList<>(0);
		} else {
			ActionType actionType = transientStage.getActionType();
			switch (actionType) {
				case DETECTION:
					return detectionStageSplitter.performSplit(transientJob, transientStage);
				case MARKUP:
					return markupStageSplitter.performSplit(transientJob, transientStage);
				default:
					return performSplit(transientJob, transientStage);
			}
		}
	}
}

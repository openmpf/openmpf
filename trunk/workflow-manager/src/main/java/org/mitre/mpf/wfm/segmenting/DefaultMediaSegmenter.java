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

package org.mitre.mpf.wfm.segmenting;

import org.apache.camel.Message;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.camel.operations.detection.DetectionContext;
import org.mitre.mpf.wfm.data.entities.transients.TransientMedia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * This segmenter returns an empty message collection and warns that the provided {@link org.mitre.mpf.wfm.data.entities.transients.TransientMedia}
 * does not have a type that is supported.
 */
@Component(DefaultMediaSegmenter.REF)
public class DefaultMediaSegmenter implements MediaSegmenter {
	private static final Logger log = LoggerFactory.getLogger(DefaultMediaSegmenter.class);

	public static final String REF = "defaultMediaSegmenter";

	@Override
	public List<Message> createDetectionRequestMessages(TransientMedia transientMedia, DetectionContext detectionContext) throws WfmProcessingException {
		log.warn("[Job {}|{}|{}] Media {} is of the unsupported type '{}' and will not be processed.",
				detectionContext.getJobId(),
				detectionContext.getStageIndex(),
				detectionContext.getActionIndex(),
				transientMedia.getId(),
				transientMedia.getType());
		return new ArrayList<Message>(0);
	}
}

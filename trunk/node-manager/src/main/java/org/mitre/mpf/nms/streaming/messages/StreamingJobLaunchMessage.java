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

package org.mitre.mpf.nms.streaming.messages;

import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.io.Serializable;
import java.util.List;

@SuppressWarnings("PublicField")
public class StreamingJobLaunchMessage implements StreamingJobMessage, Serializable {

	public final long jobId;

	public final FrameReaderLaunchMessage frameReaderLaunchMessage;

	public final VideoWriterLaunchMessage videoWriterLaunchMessage;

	public final List<ComponentLaunchMessage> componentLaunchMessages;


	public StreamingJobLaunchMessage(long jobId, FrameReaderLaunchMessage frameReaderLaunchMessage,
	                                 VideoWriterLaunchMessage videoWriterLaunchMessage,
	                                 List<ComponentLaunchMessage> componentLaunchMessages) {
		this.jobId = jobId;
		this.frameReaderLaunchMessage = frameReaderLaunchMessage;
		this.videoWriterLaunchMessage = videoWriterLaunchMessage;
		this.componentLaunchMessages = componentLaunchMessages;
	}


	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, new RecursiveToStringStyle());
	}
}

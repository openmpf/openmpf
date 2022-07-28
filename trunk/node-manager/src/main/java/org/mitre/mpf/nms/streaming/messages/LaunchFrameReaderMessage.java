/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2022 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2022 The MITRE Corporation                                       *
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

import java.io.Serializable;

//TODO: For future use. Untested.
public class LaunchFrameReaderMessage implements Serializable {

	public final long jobId;

	public final String streamUri;

	public final int segmentSize;

	public final int frameDataBufferSize;

	public final long stallTimeout;

	public final long stallAlertThreshold;

	public final String messageBrokerUri;

	public final String segmentOutputQueue;

	public final String componentFrameQueue;

	public final String videoWriterFrameQueue;

	public final String releaseFrameQueue;

	public final String stallAlertQueue;


	public LaunchFrameReaderMessage(
			long jobId,
			String streamUri,
			int segmentSize,
			int frameDataBufferSize,
			long stallTimeout,
			long stallAlertThreshold,
			String messageBrokerUri,
			String segmentOutputQueue,
			String componentFrameQueue,
			String videoWriterFrameQueue,
			String releaseFrameQueue,
			String stallAlertQueue) {

		this.jobId = jobId;
		this.streamUri = streamUri;
		this.segmentSize = segmentSize;
		this.frameDataBufferSize = frameDataBufferSize;
		this.stallTimeout = stallTimeout;
		this.stallAlertThreshold = stallAlertThreshold;
		this.messageBrokerUri = messageBrokerUri;
		this.segmentOutputQueue = segmentOutputQueue;
		this.componentFrameQueue = componentFrameQueue;
		this.videoWriterFrameQueue = videoWriterFrameQueue;
		this.releaseFrameQueue = releaseFrameQueue;
		this.stallAlertQueue = stallAlertQueue;
	}
}

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

package org.mitre.mpf.nms.streaming;

import com.google.common.collect.ImmutableMap;
import org.mitre.mpf.nms.streaming.messages.*;

import java.util.Arrays;
import java.util.Collections;

public class StreamingJobTestUtil {

	public static final String TEST_PROCESS_PATH = "src/test/resources/test-process/test-streaming-proc.py";


	private StreamingJobTestUtil() {
	}


	public static StreamingJobLaunchMessage createLaunchMessage() {
		return createLaunchMessage(1234);
	}


	public static StreamingJobLaunchMessage createLaunchMessage(long jobId) {
		FrameReaderLaunchMessage frameReaderLaunch = new FrameReaderLaunchMessage(
				jobId,
				"stream://theStream",
				10,
				40,
				-1,
				"failover://(tcp://localhost.localdomain:61616)?jms.prefetchPolicy.all=1&startupMaxReconnectAttempts=1",
				String.format("MPF.Job_%s__Segments_Stage_0", jobId),
				String.format("MPF.%s__Frames_Stage_0", jobId),
				String.format("MPF.%s__VideoWriter_Frame_Input", jobId),
				String.format("MPF.%s__RELEASE_FRAME", jobId),
				"MPF.WFM_STREAMING_JOB_STALLED");

		VideoWriterLaunchMessage videoWriterLaunchMessage = new VideoWriterLaunchMessage(
				jobId,
				"fake-path/output",
				String.format("MPF.Job_%s__VideoWriter_Frame_Input", jobId),
				"MPF.DONE_WITH_FRAME",
				"MPF.WFM_SUMMARY_REPORTS");


		ImmutableMap<String, String> firstStageProps = ImmutableMap.of(
				"firstStageProp1Key", "firstStageProp1Value",
				"firstStageProp2Key", "firstStageProp2Value");

		ComponentLaunchMessage firstStageMessage = new ComponentLaunchMessage(
				jobId,
				"MyComponent",
				1,
				"my-lib-path/lib/libmyComponent.so",
				Collections.emptyMap(),
				1,
				firstStageProps,
				String.format("MPF.Job_%s__Segments_Stage_0", jobId),
				String.format("MPF.Job_%s__Frames_Stage_0", jobId),
				String.format("MPF.Job_%s__Frames_Stage_1", jobId));


		ImmutableMap<String, String> lastStageProps = ImmutableMap.of(
				"lastStageProp1Key", "lastStageProp1Value",
				"lastStageProp2Key", "lastStageProp2Value");

		LastStageComponentLaunchMessage lastStageMessage = new LastStageComponentLaunchMessage(
				jobId,
				"MyComponent2",
				2,
				"my-lib-path/lib/libmyComponent2.so",
				Collections.emptyMap(),
				1,
				lastStageProps,
				String.format("MPF.Job_%s__Segments_Stage_1", jobId),
				String.format("MPF.Job_%s__Frames_Stage_1", jobId),
				"MPF.DONE_WITH_FRAME",
				"MPF.WFM_NEW_TRACK_ALERTS",
				"MPF.WFM_SUMMARY_REPORTS");

		StreamingJobLaunchMessage jobLaunchMessage = new StreamingJobLaunchMessage(
				jobId, frameReaderLaunch, videoWriterLaunchMessage,
				Arrays.asList(firstStageMessage, lastStageMessage));

		return jobLaunchMessage;
	}
}

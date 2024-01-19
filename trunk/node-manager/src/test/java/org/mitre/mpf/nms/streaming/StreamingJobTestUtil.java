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

package org.mitre.mpf.nms.streaming;

import com.google.common.collect.ImmutableMap;
import org.mitre.mpf.nms.streaming.messages.LaunchStreamingJobMessage;

import java.util.Collections;

public class StreamingJobTestUtil {

	public static final String TEST_PROCESS_PATH = "src/test/resources/test-process/test-streaming-proc.py";


	private StreamingJobTestUtil() {
	}


	//TODO: For future use. Untested.
//	public static LaunchStreamingJobMessage createLaunchMessage() {
//		return createLaunchMessage(1234);
//	}
//
//
//	public static LaunchStreamingJobMessage createLaunchMessage(long jobId) {
//		LaunchFrameReaderMessage frameReaderLaunch = new LaunchFrameReaderMessage(
//				jobId,
//				"stream://theStream",
//				10,
//				40,
//				-1,
//				"failover:(tcp://localhost.localdomain:61616)?jms.prefetchPolicy.all=0&startupMaxReconnectAttempts=1",
//				String.format("MPF.Job_%s__Segments_Stage_0", jobId),
//				String.format("MPF.%s__Frames_Stage_0", jobId),
//				String.format("MPF.%s__VideoWriter_Frame_Input", jobId),
//				String.format("MPF.%s__RELEASE_FRAME", jobId),
//              "MPF.WFM_STREAMING_JOB_STALLED"));
//
//		LaunchVideoWriterMessage launchVideoWriterMessage = new LaunchVideoWriterMessage(
//				jobId,
//				"fake-path/output",
//				String.format("MPF.Job_%s__VideoWriter_Frame_Input", jobId),
//				"MPF.DONE_WITH_FRAME",
//				"MPF.WFM_SUMMARY_REPORTS");
//
//
//		ImmutableMap<String, String> firstStageProps = ImmutableMap.of(
//				"firstStageProp1Key", "firstStageProp1Value",
//				"firstStageProp2Key", "firstStageProp2Value");
//
//		LaunchComponentMessage firstStageMessage = new LaunchComponentMessage(
//				jobId,
//				"MyComponent",
//				1,
//				"my-lib-path/lib/libmyComponent.so",
//				Collections.emptyMap(),
//				1,
//				firstStageProps,
//				String.format("MPF.Job_%s__Segments_Stage_0", jobId),
//				String.format("MPF.Job_%s__Frames_Stage_0", jobId),
//				String.format("MPF.Job_%s__Frames_Stage_1", jobId));
//
//
//		ImmutableMap<String, String> lastStageProps = ImmutableMap.of(
//				"lastStageProp1Key", "lastStageProp1Value",
//				"lastStageProp2Key", "lastStageProp2Value");
//
//		LaunchLastStageComponentMessage lastStageMessage = new LaunchLastStageComponentMessage(
//				jobId,
//				"MyComponent2",
//				2,
//				"my-lib-path/lib/libmyComponent2.so",
//				Collections.emptyMap(),
//				1,
//				lastStageProps,
//				String.format("MPF.Job_%s__Segments_Stage_1", jobId),
//				String.format("MPF.Job_%s__Frames_Stage_1", jobId),
//				"MPF.DONE_WITH_FRAME",
//				"MPF.WFM_NEW_TRACK_ALERTS",
//				"MPF.WFM_SUMMARY_REPORTS");
//
//		LaunchStreamingJobMessage jobLaunchMessage = new LaunchStreamingJobMessage(
//				jobId, frameReaderLaunch, launchVideoWriterMessage,
//				Arrays.asList(firstStageMessage, lastStageMessage));
//
//		return jobLaunchMessage;
//	}


	public static LaunchStreamingJobMessage createLaunchMessage() {
		return createLaunchMessage(1234);
	}


	public static LaunchStreamingJobMessage createLaunchMessage(long jobId) {

		ImmutableMap<String, String> firstStageProps = ImmutableMap.of(
				"firstStageProp1Key", "firstStageProp1Value",
				"firstStageProp2Key", "firstStageProp2Value");

		ImmutableMap<String, String> mediaProperties = ImmutableMap.of(
				"streamProp1", "streamVal1",
				"streamProp2", "streamVal2");

		LaunchStreamingJobMessage launchMessage = new LaunchStreamingJobMessage(
				jobId,
				"stream://theStream",
				10,
				5,
				10,
				"MyComponent",
				"my-lib-path/lib/libmyComponent.so",
				Collections.emptyMap(),
				firstStageProps,
				mediaProperties,
				"failover:(tcp://localhost.localdomain:61616)?jms.prefetchPolicy.all=0&startupMaxReconnectAttempts=1",
				"MPF.WFM_STREAMING_JOB_STATUS",
				"MPF.WFM_STREAMING_JOB_ACTIVITY",
				"MPF.WFM_STREAMING_JOB_SUMMARY_REPORT");

		return launchMessage;
	}
}

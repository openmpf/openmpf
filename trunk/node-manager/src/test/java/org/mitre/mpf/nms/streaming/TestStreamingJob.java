/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
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

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestStreamingJob {


	//TODO: For future use.
//	@Test
//	public void testShutdownOrder() throws InterruptedException {
//		JobIniFiles jobIniFiles = mock(JobIniFiles.class);
//
//		StreamingProcess frameReader = mock(StreamingProcess.class);
//		ProcessController frameReaderCtrl = setupMockProcess(frameReader);
//
//		StreamingProcess videoWriter = mock(StreamingProcess.class);
//		ProcessController videoWriterCtrl = setupMockProcess(videoWriter);
//
//
//		StreamingProcess component1 = mock(StreamingProcess.class);
//		ProcessController componentCtrl1 = setupMockProcess(component1);
//
//		StreamingProcess component2 = mock(StreamingProcess.class);
//		ProcessController componentCtrl2 = setupMockProcess(component2);
//
//		StreamingJob job = new StreamingJob(1, jobIniFiles, frameReader, videoWriter,
//		                                    Arrays.asList(component1, component2));
//
//		job.startJob();
//
//		verifyStarted(frameReader, videoWriter, component1, component2);
//
//
//		// Job will begin to shutdown the processes, but the processes won't exit until allowExit() is manually called.
//		CompletableFuture<Void> jobCompleteFuture = job.stopJob();
//
//		assertFalse("Job completed before all processes exited.", jobCompleteFuture.isDone());
//
//		verifyQuitCommandSent(videoWriter, component1, component2);
//		verify(frameReader)
//				.pause();
//
//		verifyQuitNotSent(frameReader); // FrameReader should not exit until all other processes have exited.
//
//
//		// Make sure FrameReader doesn't exit until after all non-FrameReader processes exit.
//		componentCtrl1.allowExit();
//		verifyQuitNotSent(frameReader);
//
//		videoWriterCtrl.allowExit();
//		verifyQuitNotSent(frameReader);
//
//		componentCtrl2.allowExit();
//
//		verifyQuitCommandSent(frameReader);
//
//		assertFalse("Job completed before FrameReader exited", jobCompleteFuture.isDone());
//
//		verify(jobIniFiles, never())
//				.deleteIniFiles();
//
//		frameReaderCtrl.allowExit();
//
//		assertTrue(jobCompleteFuture.isDone());
//
//		verify(jobIniFiles)
//				.deleteIniFiles();
//
//	    assertFalse(jobCompleteFuture.isCompletedExceptionally());
//	}



//	@Test
//	public void testShutdownException() {
//		JobIniFiles jobIniFiles = mock(JobIniFiles.class);
//
//		StreamingProcess frameReader = mock(StreamingProcess.class);
//		ProcessController frameReaderCtrl = setupMockProcess(frameReader);
//
//		StreamingProcess videoWriter = mock(StreamingProcess.class);
//		ProcessController videoWriterCtrl = setupMockProcess(videoWriter);
//
//
//		StreamingProcess component = mock(StreamingProcess.class);
//		ProcessController componentCtrl = setupMockProcess(component);
//
//		StreamingJob job = new StreamingJob(1, jobIniFiles, frameReader, videoWriter,
//		                                    Collections.singletonList(component));
//		job.startJob();
//
//
//		verifyStarted(frameReader, videoWriter, component);
//
//		CompletableFuture<Void> jobCompleteFuture = job.stopJob();
//
//
//		videoWriterCtrl.causeException();
//
//		verifyQuitNotSent(frameReader);
//
//		componentCtrl.allowExit();
//
//	    verifyQuitCommandSent(frameReader);
//
//		assertFalse("Job completed before FrameReader exited", jobCompleteFuture.isDone());
//
//		verify(jobIniFiles, never())
//				.deleteIniFiles();
//
//		frameReaderCtrl.allowExit();
//
//		assertTrue(jobCompleteFuture.isDone());
//
//		verify(jobIniFiles)
//				.deleteIniFiles();
//
//	    assertTrue(jobCompleteFuture.isCompletedExceptionally());
//	}


	@Test
	public void testShutdownOrder() {
		JobIniFiles jobIniFiles = mock(JobIniFiles.class);

		StreamingProcess streamingProcess = mock(StreamingProcess.class);
		ProcessController processCtrl = setupMockProcess(streamingProcess);

		StreamingJob job = new StreamingJob(1, jobIniFiles, streamingProcess);

		job.startJob();

		verifyStarted(streamingProcess);

		// Job will begin to shutdown the processes, but the processes won't exit until allowExit() is manually called.
		CompletableFuture<Void> jobCompleteFuture = job.stopJob();

		verifyQuitCommandSent(streamingProcess);

		assertFalse("Job completed before all processes exited.", jobCompleteFuture.isDone());
		verify(jobIniFiles, never())
				.deleteIniFiles();


		processCtrl.allowExit();


		assertTrue(jobCompleteFuture.isDone());

		verify(jobIniFiles)
				.deleteIniFiles();

		assertFalse(jobCompleteFuture.isCompletedExceptionally());
	}



	@Test
	public void testShutdownException() {
		JobIniFiles jobIniFiles = mock(JobIniFiles.class);

		StreamingProcess streamingProcess = mock(StreamingProcess.class);
		ProcessController processCtrl = setupMockProcess(streamingProcess);

		StreamingJob job = new StreamingJob(1, jobIniFiles, streamingProcess);

		job.startJob();


		verifyStarted(streamingProcess);

		// Job will begin to shutdown the processes, but the processes won't exit until allowExit() is manually called.
		CompletableFuture<Void> jobCompleteFuture = job.stopJob();

		verifyQuitCommandSent(streamingProcess);

		assertFalse("Job completed before all processes exited.", jobCompleteFuture.isDone());
		verify(jobIniFiles, never())
				.deleteIniFiles();

		processCtrl.causeException();

		assertTrue(jobCompleteFuture.isDone());

		verify(jobIniFiles)
				.deleteIniFiles();

		assertTrue(jobCompleteFuture.isCompletedExceptionally());
	}


	private static void verifyStarted(StreamingProcess... processes) {
		for (StreamingProcess process : processes) {
			verify(process)
					.start();
		}
	}

	private static void verifyQuitCommandSent(StreamingProcess... processes) {
		for (StreamingProcess process : processes) {
			verify(process)
					.quit();
		}
	}


	private static void verifyQuitNotSent(StreamingProcess process) {
		verify(process, never())
				.quit();
	}


	private static ProcessController setupMockProcess(StreamingProcess process) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		when(process.start())
				.thenReturn(future);

		return new ProcessController() {
			public void allowExit() {
				future.complete(null);
			}

			public void causeException() {
				future.completeExceptionally(new CompletionException(new IllegalStateException("Intentional Error")));
			}
		};
	}



	private static interface ProcessController {
		void allowExit();

		void causeException();
	}
}

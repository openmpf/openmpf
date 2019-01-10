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

package org.mitre.mpf.nms.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class StreamingJob {

	private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

	private final long _jobId;

	private final JobIniFiles _jobIniFiles;

	private final StreamingProcess _jobProcess;

//	private final StreamingProcess _frameReaderProcess;
//
//	private final StreamingProcess _videoWriterProcess;
//
//	private final List<StreamingProcess> _componentProcesses;

	private CompletableFuture<Void> _jobCompleteFuture;



	public StreamingJob(long jobId, JobIniFiles jobIniFiles, StreamingProcess jobProcess) {
		_jobId = jobId;
		_jobIniFiles = jobIniFiles;
		_jobProcess = jobProcess;
	}



	public CompletableFuture<Void> startJob() {
		_jobCompleteFuture = _jobProcess.start()
				.whenComplete((none, error) -> onAllProcessesExit(error));
		return _jobCompleteFuture;
	}


	public CompletableFuture<Void> stopJob() {
		_jobProcess.quit();
		return _jobCompleteFuture;
	}

	private void onAllProcessesExit(Throwable error) {
		LOG.info("Job {}: All processes have exited.", _jobId);
		_jobIniFiles.deleteIniFiles();
	}


	//TODO: For future use. Untested.
//	public StreamingJob(
//			long jobId,
//			JobIniFiles jobIniFiles,
//			StreamingProcess frameReader,
//			StreamingProcess videoWriter,
//			List<StreamingProcess> components) {
//		_jobId = jobId;
//		_jobIniFiles = jobIniFiles;
//		_frameReaderProcess = frameReader;
//		_videoWriterProcess = videoWriter;
//		_componentProcesses = components;
//	}


//	public CompletableFuture<Void> startJob() {
//		CompletableFuture<Void> frameReaderFuture = _frameReaderProcess.start();
//
//		CompletableFuture<Void> videoWriterAndComponentFuture = _videoWriterProcess.start();
//
//		for (StreamingProcess componentProcess : _componentProcesses) {
//			CompletableFuture<Void> componentFuture = componentProcess.start();
//			videoWriterAndComponentFuture = CompletableFuture.allOf(videoWriterAndComponentFuture, componentFuture);
//		}
//
//		_jobCompleteFuture = videoWriterAndComponentFuture
//				// Schedule tasks that need to be run after video writer and all component processes exit.
//				.whenComplete((none, error) -> onVideoWriterAndComponentExit(error))
//				// Schedule tasks that need to run after all processes exit and previous clean up stage completes.
//				.runAfterBoth(frameReaderFuture,
//				              () -> { /* Pass through so clean up tasks run even if there is an error. */ })
//				.whenComplete((none, error) -> onAllProcessesExit(error));
//		return _jobCompleteFuture;
//	}

//	public CompletableFuture<Void> stopJob() {
//		_frameReaderProcess.pause();
//		_videoWriterProcess.quit();
//		_componentProcesses.forEach(StreamingProcess::quit);
//		return _jobCompleteFuture;
//	}



//	private void onVideoWriterAndComponentExit(Throwable error) {
//		LOG.info("Job {}: VideoWriter and all component processes have exited", _jobId);
//		_frameReaderProcess.quit();
//
//		if (error != null) {
//			handleStreamingProcessException(error);
//		}
//	}

//	private void onAllProcessesExit(Throwable error) {
//		LOG.info("Job {}: All processes have exited.", _jobId);
//		_jobIniFiles.deleteIniFiles();
//
//		if (error != null) {
//			handleFrameReaderProcessException(error);
//		}
//	}



//	private static void handleStreamingProcessException(Throwable error) {
//		LOG.error("Streaming Process error: ", error);
//		throw new IllegalStateException(error);
//	}

//	private static void handleFrameReaderProcessException(Throwable error) {
//		LOG.error("Frame Reader error: ", error);
//		throw new IllegalStateException(error);
//	}
}

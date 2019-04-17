/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamingProcess {

	private static final Logger LOG = LoggerFactory.getLogger(StreamingProcess.class);

	private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

	private final String _executable;

	private final ProcessBuilder _processBuilder;

	private final int _maxNumRestarts;


	private final SynchronizedOperations _syncOps = new SynchronizedOperations();


	public StreamingProcess(String executable, ProcessBuilder processBuilder, int maxNumRestarts) {
		_executable = executable;
		_processBuilder = processBuilder;
		_maxNumRestarts = maxNumRestarts;
	}


	public CompletableFuture<Void> start() {
		Process process = createProcess();
		return CompletableFuture.runAsync(() -> restartUntilDone(process), THREAD_POOL);
	}



	private void restartUntilDone(Process originalProcess) {
		int exitCode = awaitExit(originalProcess);
		if (exitCode == StreamingProcessExitReason.CANCELLED.exitCode) {
			return;
		}

		for (int i = 0; i < _maxNumRestarts; i++) {
			LOG.warn("Restarting the {} process. The process has been started {} times.", _executable, i + 1);
			Process restartedProcess = createProcess();
			if (restartedProcess == null) {
				if (_syncOps.isStopRequested()) {
					return;
				}
				throw new StreamingProcessExitException(exitCode);
			}
			exitCode = awaitExit(restartedProcess);
			if (exitCode == StreamingProcessExitReason.CANCELLED.exitCode) {
				return;
			}
		}
		throw new StreamingProcessExitException(exitCode);
	}


	//TODO: For future use. Untested.
//	public void pause() {
//		_syncOps.pause();
//	}


	public void quit() {
		_syncOps.quit();
	}


	private Process createProcess() {
		return _syncOps.createProcess(_processBuilder);
	}


	private int awaitExit(Process process) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			reader.lines()
					.forEach(line -> LOG.info("[{}]: {}", _executable, line));
		}
		catch (IOException e) {
			throw new UncheckedIOException(String.format("Exception while running process: %s", _executable), e);
		}

		try {
			int exitCode = process.waitFor();

			// It is technically possible to receive a stop request after the process exits,
			// but before _syncOps.onProcessExit() is called. In that case, the stop command will be written
			// to the process's standard in after the process has exited, which does not cause an exception.
			// The process itself will never process that stop request, however the _syncOps._stopRequested flag
			// will be set preventing the service from being restarted.
			_syncOps.onProcessExit();

			LOG.info("Process: {} exited with exit code {}", _executable, exitCode);
			if (exitCode == StreamingProcessExitReason.STREAM_STALLED.exitCode) {
				// Throw exception to prevent restarting when process exits due to a stall.
				throw new StreamingProcessExitException(StreamingProcessExitReason.STREAM_STALLED);
			}
			return exitCode;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}



	// Class to hold mutually exclusive operations.
	private static final class SynchronizedOperations {
		private PrintWriter _processStdIn;

		private boolean _stopRequested;

		private boolean _processIsRunning;


		// Synchronized to make sure process is not currently restarting.
		public synchronized void quit() {
			_stopRequested = true;
			sendCommand("quit");
		}

		//TODO: For future use. Untested.
		// Synchronized to make sure process is not currently restarting.
//		public synchronized void pause() {
//			_stopRequested = true;
//			sendCommand("pause");
//		}


		private void sendCommand(String command) {
			if (_processIsRunning) {
				_processStdIn.println(command);
				_processStdIn.flush();
			}
		}


		// Synchronized to make sure that any stop requests are either executed before or after the process has
		// finished restarting.
		// If the stop request is processed before the process begins to restart,
		// then the process will not be restarted.
		// If the stop request is received while the process is restarting, then the stop request will block until
		// the process finishes restarting. Once the process is done restarting, the stop request will be processed
		// which will cause the newly created process to exit.
		public synchronized Process createProcess(ProcessBuilder processBuilder) {
			try {
				if (_stopRequested) {
					return null;
				}

				Process process = processBuilder.start();
				_processStdIn = new PrintWriter(process.getOutputStream(), true);
				_processIsRunning = true;
				return process;
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}


		public synchronized void onProcessExit() {
			_processIsRunning = false;
			_processStdIn.close();
			_processStdIn = null;
		}


		public synchronized boolean isStopRequested() {
			return _stopRequested;
		}
	}
}

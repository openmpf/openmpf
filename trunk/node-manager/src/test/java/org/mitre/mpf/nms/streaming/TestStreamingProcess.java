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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class TestStreamingProcess {

	@Rule
	public TemporaryFolder _tempDir = new TemporaryFolder();


	@Test
	public void videoWriterRunsUntilQuitRequested() throws InterruptedException, ExecutionException, IOException {
		StreamingProcess videoWriter = createProcess("VideoWriter", 0.5);
		Future<Void> future = videoWriter.start();

		assertFalse(future.isDone());

		videoWriter.quit();
		assertFalse(future.isDone());

		Thread.sleep(1000);

		assertProcessExitedSuccessfully(future);
	}


	@Test
	public void frameReaderRunsUntilQuitRequested() throws InterruptedException, ExecutionException, IOException {
		StreamingProcess frameReader = createProcess("FrameReader", 0);
		Future<Void> future = frameReader.start();

		frameReader.pause();

		Thread.sleep(200);
		assertFalse(future.isDone());

		frameReader.quit();
		Thread.sleep(200);

		assertProcessExitedSuccessfully(future);
	}



	@Test
	public void testRestartLimit() throws IOException, InterruptedException {
		testRestartCount(0);
		testRestartCount(1);
		testRestartCount(3);
	}

	private void testRestartCount(int restartLimit) throws IOException, InterruptedException {
		Path countFile = _tempDir.newFile().toPath();

		String[] cmdline = {"python", StreamingJobTestUtil.TEST_PROCESS_PATH, "VideoWriter", "fake-path", "0",
				countFile.toAbsolutePath().toString()};
		ProcessBuilder builder = new ProcessBuilder(cmdline)
				.redirectErrorStream(true);

		StreamingProcess process = new StreamingProcess("VideoWriter", builder, restartLimit);
		CompletableFuture<Void> future = process.start();

		try {
			future.get();
			fail("Expected ExecutionException");
		}
		catch (ExecutionException e) {
		}

		long actualStartCount = Files.lines(countFile)
				.flatMapToInt(CharSequence::chars)
				.filter(ch -> ch == 'x')
				.count();

		assertEquals(restartLimit + 1, actualStartCount);
	}



	private static void assertProcessExitedSuccessfully(Future<?> processFuture) throws ExecutionException, InterruptedException {
		assertTrue(processFuture.isDone());
		processFuture.get();  // Throws exception if process fails
	}


	private StreamingProcess createProcess(String name, double stopDelay) throws IOException {
		File file = _tempDir.newFile("test.ini");

		String[] cmdline = {"python", StreamingJobTestUtil.TEST_PROCESS_PATH, name,
				file.getAbsolutePath(), String.valueOf(stopDelay)};

		ProcessBuilder builder = new ProcessBuilder(cmdline)
				.redirectErrorStream(true);
		return new StreamingProcess(name, builder, 0);
	}
}


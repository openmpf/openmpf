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


package org.mitre.mpf.wfm.util;

import java.io.*;
import java.util.concurrent.*;

public class PipeStream extends InputStream {

    @FunctionalInterface
    public interface PipeWriter {
        public void writeTo(OutputStream outputStream) throws IOException;
    }

    private final PipedOutputStream _pipedOutputStream;

    private final PipedInputStream _pipedInputStream;

    private final CompletableFuture<Void> _writeDoneFuture;


    public PipeStream(int bufferSize, PipeWriter writer) throws IOException {
        this(new PipedInputStream(bufferSize), writer);
    }

    public PipeStream(PipeWriter writer) throws IOException {
        this(new PipedInputStream(), writer);
    }

    private PipeStream(PipedInputStream pipedInputStream, PipeWriter writer) throws IOException {
        _pipedOutputStream = new PipedOutputStream(pipedInputStream);
        _pipedInputStream = pipedInputStream;
        _writeDoneFuture = beginWriting(writer);
    }


    private CompletableFuture<Void> beginWriting(PipeWriter writer) {
        return ThreadUtil.runAsync(() -> {
            try (OutputStream out = _pipedOutputStream) {
                writer.writeTo(out);
            }
        });
    }

    private void checkError() throws IOException {
        try {
            // Rethrow exception from writer.
            _writeDoneFuture.getNow(null);
        }
        catch (CompletionException e) {
            unwrapAndRethrow(e);
        }
    }

    private static void unwrapAndRethrow(CompletionException completionException) throws IOException {
        String errorMessage = "An exception was thrown by the pipe writer.";
        Throwable up = completionException.getCause();
        if (up == null) {
            throw new IllegalStateException(errorMessage, completionException);
        }
        try {
            throw up;
        }
        catch (IOException | Error | RuntimeException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }


    @Override
    public int read() throws IOException {
        checkError();
        return _pipedInputStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        checkError();
        return _pipedInputStream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkError();
        return _pipedInputStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        checkError();
        return _pipedInputStream.skip(n);
    }

    @Override
    public boolean markSupported() {
        return _pipedInputStream.markSupported();
    }

    @Override
    public void mark(int readLimit) {
        _pipedInputStream.mark(readLimit);
    }


    @Override
    public void reset() throws IOException {
        checkError();
        _pipedInputStream.reset();
    }

    @Override
    public int available() throws IOException {
        checkError();
        return _pipedInputStream.available();
    }

    @Override
    public void close() {
        IoUtils.closeQuietly(_pipedInputStream);
    }
}

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


package org.mitre.mpf.wfm.util;

import java.util.concurrent.*;
import java.util.function.*;

public class ThreadUtil {

    public static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private ThreadUtil() {
    }


    public static CustomCompletableFuture<Void> runAsync(ThrowingRunnable task) {
        return callAsync(() -> {
            task.run();
            return null;
        });
    }


    public static <T> CustomCompletableFuture<T> callAsync(Callable<T> task) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        Future<T> cancellableFuture = THREAD_POOL.submit(() -> {
            try {
                T result = task.call();
                completableFuture.complete(result);
                return result;
            }
            catch (Throwable e) {
                completableFuture.completeExceptionally(e);
                throw e;
            }
        });
        return new CustomCompletableFuture<>(completableFuture, cancellableFuture, THREAD_POOL);
    }



    @FunctionalInterface
    public interface ThrowingRunnable {
        public void run() throws Exception;
    }


    public static void shutdown() {
        try {
            THREAD_POOL.shutdown();
            THREAD_POOL.awaitTermination(1, TimeUnit.SECONDS);
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            // No need to do anything, already about to exit.
        }
        finally {
            THREAD_POOL.shutdownNow();
        }
    }



    // Regular CompletableFuture runs tasks on ForkJoinPool.commonPool() by default.
    // ForkJoinPool.commonPool() is for non-blocking CPU bound tasks.
    // This version runs tasks on ThreadUtil.THREAD_POOL by default.
    public static class CustomCompletableFuture<T> extends CompletableFuture<T> {
        private final CompletableFuture<T> _inner;
        private final Future<?> _cancellableFuture;
        private final Executor _defaultExecutor;

        public CustomCompletableFuture(CompletableFuture<T> inner, Future<?> cancellableFuture,
                                       Executor defaultExecutor) {
            _inner = inner;
            _cancellableFuture = cancellableFuture;
            _defaultExecutor = defaultExecutor;
        }

        public CustomCompletableFuture(CompletableFuture<T> inner, Executor defaultExecutor) {
            this(inner, null, defaultExecutor);
        }

        public CustomCompletableFuture(CompletableFuture<T> inner) {
            this(inner, getDefaultExecutor());
        }

        public CustomCompletableFuture(Executor defaultExecutor) {
            this(new CompletableFuture<>(), defaultExecutor);
        }

        public CustomCompletableFuture() {
            this(new CompletableFuture<>());
        }

        private static Executor getDefaultExecutor() {
            return THREAD_POOL;
        }

        public static <U> CustomCompletableFuture<U> supplyAsync(Supplier<U> supplier) {
            return supplyAsync(supplier, getDefaultExecutor());
        }

        public static <U> CustomCompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
            return new CustomCompletableFuture<>(CompletableFuture.supplyAsync(supplier, executor));
        }

        public static CustomCompletableFuture<Void> runAsync(Runnable runnable) {
            return runAsync(runnable, getDefaultExecutor());
        }

        public static CustomCompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
            return new CustomCompletableFuture<>(CompletableFuture.runAsync(runnable, executor));
        }

        public static <U> CustomCompletableFuture<U> completedFuture(U value) {
            return new CustomCompletableFuture<>(CompletableFuture.completedFuture(value));
        }

        private <U> CustomCompletableFuture<U> wrap(CompletableFuture<U> future) {
            return new CustomCompletableFuture<>(future, _cancellableFuture, _defaultExecutor);
        }

        @Override
        public boolean isDone() {
            return _inner.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return _inner.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return _inner.get(timeout, unit);
        }

        @Override
        public T join() {
            return _inner.join();
        }

        @Override
        public T getNow(T valueIfAbsent) {
            return _inner.getNow(valueIfAbsent);
        }

        @Override
        public boolean complete(T value) {
            return _inner.complete(value);
        }

        @Override
        public boolean completeExceptionally(Throwable ex) {
            return _inner.completeExceptionally(ex);
        }

        @Override
        public <U> CustomCompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
            return wrap(_inner.thenApply(fn));
        }

        @Override
        public <U> CustomCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
            return wrap(_inner.thenApplyAsync(fn, _defaultExecutor));
        }

        @Override
        public <U> CustomCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
            return wrap(_inner.thenApplyAsync(fn, executor));
        }

        @Override
        public CustomCompletableFuture<Void> thenAccept(Consumer<? super T> action) {
            return wrap(_inner.thenAccept(action));
        }

        @Override
        public CustomCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
            return wrap(_inner.thenAcceptAsync(action, _defaultExecutor));
        }

        @Override
        public CustomCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
            return wrap(_inner.thenAcceptAsync(action, executor));
        }

        @Override
        public CustomCompletableFuture<Void> thenRun(Runnable action) {
            return wrap(_inner.thenRun(action));
        }

        @Override
        public CustomCompletableFuture<Void> thenRunAsync(Runnable action) {
            return wrap(_inner.thenRunAsync(action, _defaultExecutor));
        }

        @Override
        public CustomCompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
            return wrap(_inner.thenRunAsync(action, executor));
        }

        @Override
        public <U, V> CustomCompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
                                                             BiFunction<? super T, ? super U, ? extends V> fn) {
            return wrap(_inner.thenCombine(other, fn));
        }

        @Override
        public <U, V> CustomCompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                                  BiFunction<? super T, ? super U, ? extends V> fn) {
            return wrap(_inner.thenCombineAsync(other, fn, _defaultExecutor));
        }

        @Override
        public <U, V> CustomCompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                                  BiFunction<? super T, ? super U, ? extends V> fn,
                                                                  Executor executor) {
            return wrap(_inner.thenCombineAsync(other, fn, executor));
        }

        @Override
        public <U> CustomCompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                                BiConsumer<? super T, ? super U> action) {
            return wrap(_inner.thenAcceptBoth(other, action));
        }

        @Override
        public <U> CustomCompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                                     BiConsumer<? super T, ? super U> action) {
            return wrap(_inner.thenAcceptBothAsync(other, action, _defaultExecutor));
        }

        @Override
        public <U> CustomCompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                                     BiConsumer<? super T, ? super U> action,
                                                                     Executor executor) {
            return wrap(_inner.thenAcceptBothAsync(other, action, executor));
        }

        @Override
        public CustomCompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
            return wrap(_inner.runAfterBoth(other, action));
        }

        @Override
        public CustomCompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
            return wrap(_inner.runAfterBothAsync(other, action, _defaultExecutor));
        }

        @Override
        public CustomCompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action,
                                                               Executor executor) {
            return wrap(_inner.runAfterBothAsync(other, action, executor));
        }

        @Override
        public <U> CustomCompletableFuture<U> applyToEither(CompletionStage<? extends T> other,
                                                            Function<? super T, U> fn) {
            return wrap(_inner.applyToEither(other, fn));
        }

        @Override
        public <U> CustomCompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                                 Function<? super T, U> fn) {
            return wrap(_inner.applyToEitherAsync(other, fn, _defaultExecutor));
        }

        @Override
        public <U> CustomCompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                                 Function<? super T, U> fn,
                                                                 Executor executor) {
            return wrap(_inner.applyToEitherAsync(other, fn, executor));
        }

        @Override
        public CustomCompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
            return wrap(_inner.acceptEither(other, action));
        }

        @Override
        public CustomCompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                               Consumer<? super T> action) {
            return wrap(_inner.acceptEitherAsync(other, action, _defaultExecutor));
        }

        @Override
        public CustomCompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                               Consumer<? super T> action, Executor executor) {
            return wrap(_inner.acceptEitherAsync(other, action, executor));
        }

        @Override
        public CustomCompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
            return wrap(_inner.runAfterEither(other, action));
        }

        @Override
        public CustomCompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
            return wrap(_inner.runAfterEitherAsync(other, action, _defaultExecutor));
        }

        @Override
        public CustomCompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action,
                                                                 Executor executor) {
            return wrap(_inner.runAfterEitherAsync(other, action, executor));
        }

        @Override
        public <U> CustomCompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
            return wrap(_inner.thenCompose(fn));
        }

        @Override
        public <U> CustomCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
            return wrap(_inner.thenComposeAsync(fn, _defaultExecutor));
        }

        @Override
        public <U> CustomCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn,
                                                               Executor executor) {
            return wrap(_inner.thenComposeAsync(fn, executor));
        }

        @Override
        public CustomCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            return wrap(_inner.whenComplete(action));
        }

        @Override
        public CustomCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
            return wrap(_inner.whenCompleteAsync(action, _defaultExecutor));
        }

        @Override
        public CustomCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
                                                            Executor executor) {
            return wrap(_inner.whenCompleteAsync(action, executor));
        }

        @Override
        public <U> CustomCompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
            return wrap(_inner.handle(fn));
        }

        @Override
        public <U> CustomCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
            return wrap(_inner.handleAsync(fn, _defaultExecutor));
        }

        @Override
        public <U> CustomCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn,
                                                          Executor executor) {
            return wrap(_inner.handleAsync(fn, executor));
        }

        @Override
        public CustomCompletableFuture<T> toCompletableFuture() {
            return this;
        }

        @Override
        public CustomCompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
            return wrap(_inner.exceptionally(fn));
        }

        public static CustomCompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
            return new CustomCompletableFuture<>(CompletableFuture.allOf(cfs));
        }

        public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
            return new CustomCompletableFuture<>(CompletableFuture.anyOf(cfs));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (_cancellableFuture != null) {
                _cancellableFuture.cancel(mayInterruptIfRunning);
            }
            return _inner.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return _inner.isCancelled();
        }

        @Override
        public boolean isCompletedExceptionally() {
            return _inner.isCompletedExceptionally();
        }

        @Override
        public void obtrudeValue(T value) {
            _inner.obtrudeValue(value);
        }

        @Override
        public void obtrudeException(Throwable ex) {
            _inner.obtrudeException(ex);
        }

        @Override
        public int getNumberOfDependents() {
            return _inner.getNumberOfDependents();
        }

        @Override
        public String toString() {
            return _inner.toString();
        }

    }

}

package com.yovo.lib_ai.internal.probe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoCompletions;
import com.yovo.lib_ai.api.YoLatencyProbe;
import com.yovo.lib_ai.completion.YoCompletionEvent;
import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;
import com.yovo.lib_ai.completion.YoCompletionTurn;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.probe.YoProbeListener;
import com.yovo.lib_ai.probe.YoProbePlan;
import com.yovo.lib_ai.probe.YoProbeResult;
import com.yovo.lib_ai.probe.YoProbeSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测速探针。每个模型走一次统一补全的流式调用，记录第一段内容和结束的时间点。
 * 传进来的补全入口由装配根用"不重试"的传输构造：重试的退避等待会算进耗时，数据就失真了。
 */
public final class LatencyProbeResource implements YoLatencyProbe {

    private static final int SAMPLE_CHARS = 80;
    private static final int MESSAGE_CHARS = 240;

    private final YoCompletions completions;
    private final Executor network;
    private final OrderedCallbacks callbacks;
    private final CallRegistry registry;

    public LatencyProbeResource(
            YoCompletions completions,
            Executor network,
            OrderedCallbacks callbacks,
            CallRegistry registry) {
        this.completions = completions;
        this.network = network;
        this.callbacks = callbacks;
        this.registry = registry;
    }

    @NonNull
    @Override
    public YoAiCall run(@NonNull YoProbePlan plan, @NonNull YoProbeListener listener) {
        if (plan.protocol() != null && !completions.protocols().contains(plan.protocol())) {
            throw new IllegalArgumentException("Platform does not provide " + plan.protocol().id());
        }
        CancelToken token = new CancelToken();
        registry.register(token);
        Run run = new Run(plan, listener, token);
        try {
            network.execute(run::start);
        } catch (RejectedExecutionException exception) {
            registry.unregister(token);
            throw new IllegalStateException("Probe executor is closed", exception);
        }
        return token::cancel;
    }

    private void post(Runnable task) {
        try {
            callbacks.submit(task);
        } catch (RejectedExecutionException ignored) {
            // 客户端已关闭，回调执行器不再接收任务。
        }
    }

    private final class Run {
        private final YoProbePlan plan;
        private final YoProbeListener listener;
        private final CancelToken token;
        private final Queue<String> pending;
        private final Object lock = new Object();
        private final List<YoProbeResult> results = new ArrayList<>();
        private final Set<Flight> active = ConcurrentHashMap.newKeySet();
        private final AtomicInteger workers = new AtomicInteger();

        Run(YoProbePlan plan, YoProbeListener listener, CancelToken token) {
            this.plan = plan;
            this.listener = listener;
            this.token = token;
            this.pending = new ConcurrentLinkedQueue<>(plan.models());
        }

        void start() {
            int count = Math.min(plan.concurrency(), plan.models().size());
            workers.set(count);
            token.onCancel(this::abortAll);
            for (int i = 0; i < count; i++) {
                try {
                    network.execute(this::work);
                } catch (RejectedExecutionException exception) {
                    workerDone();
                }
            }
        }

        private void work() {
            try {
                String model;
                while (!token.isCancelled() && (model = pending.poll()) != null) {
                    probe(model);
                }
            } finally {
                workerDone();
            }
        }

        private void workerDone() {
            if (workers.decrementAndGet() > 0) {
                return;
            }
            YoProbeSummary summary;
            synchronized (lock) {
                summary = new YoProbeSummary(results, token.isCancelled());
            }
            registry.unregister(token);
            post(() -> listener.onComplete(summary));
        }

        private void abortAll() {
            for (Flight flight : active) {
                flight.abort();
            }
        }

        private void probe(String model) {
            post(() -> {
                if (!token.isCancelled()) {
                    listener.onStarted(model);
                }
            });
            Flight flight = new Flight(System.nanoTime());
            active.add(flight);
            try {
                if (token.isCancelled()) {
                    return;
                }
                try {
                    flight.call = completions.stream(request(model), flight);
                } catch (RuntimeException exception) {
                    emit(failure(model, flight, exception));
                    return;
                }
                if (token.isCancelled()) {
                    flight.abort();
                }
                boolean finished = flight.done.await(plan.timeoutMillis(), TimeUnit.MILLISECONDS);
                if (flight.aborted) {
                    return;
                }
                if (!finished) {
                    flight.call.cancel();
                    emit(YoProbeResult.timeout(model, plan.timeoutMillis()));
                    return;
                }
                emit(flight.error != null ? failure(model, flight, flight.error) : success(model, flight));
            } catch (InterruptedException exception) {
                flight.abort();
                Thread.currentThread().interrupt();
            } finally {
                active.remove(flight);
            }
        }

        private YoCompletionRequest request(String model) {
            return YoCompletionRequest.builder()
                    .model(model)
                    .protocol(plan.protocol())
                    .addTurn(YoCompletionTurn.user(plan.prompt()))
                    .maxOutputTokens(plan.maxOutputTokens())
                    .build();
        }

        private void emit(YoProbeResult result) {
            synchronized (lock) {
                results.add(result);
            }
            post(() -> {
                if (!token.isCancelled()) {
                    listener.onResult(result);
                }
            });
        }
    }

    private static YoProbeResult success(String model, Flight flight) {
        long total = flight.millisAt(flight.endNanos);
        long first = flight.firstNanos < 0 ? total : flight.millisAt(flight.firstNanos);
        return YoProbeResult.ok(model, first, total, flight.sample(), flight.outputTokens());
    }

    private static YoProbeResult failure(String model, Flight flight, Throwable error) {
        YoAiErrorKind kind = YoAiErrors.kindOf(error);
        int status = error instanceof YoAiHttpException http ? http.statusCode() : 0;
        long elapsed = flight.millisAt(flight.endNanos < 0 ? System.nanoTime() : flight.endNanos);
        return YoProbeResult.failed(model, elapsed, kind, truncate(error.getMessage()), status);
    }

    @Nullable
    private static String truncate(@Nullable String message) {
        if (message == null || message.length() <= MESSAGE_CHARS) {
            return message;
        }
        return message.substring(0, MESSAGE_CHARS);
    }

    /** 一个模型的一次在途请求：既是流式监听器，也是等待它结束的闩。 */
    private static final class Flight implements YoCompletionListener {
        final long startNanos;
        final CountDownLatch done = new CountDownLatch(1);
        volatile YoAiCall call;
        volatile boolean aborted;
        volatile long firstNanos = -1L;
        volatile long endNanos = -1L;
        volatile YoCompletionResult result;
        volatile YoAiException error;
        private final StringBuilder sample = new StringBuilder();

        Flight(long startNanos) {
            this.startNanos = startNanos;
        }

        long millisAt(long nanos) {
            return TimeUnit.NANOSECONDS.toMillis(nanos - startNanos);
        }

        /** 输出 token 数：平台没回报 usage 时返回 -1，界面据此退回显示总时长。 */
        int outputTokens() {
            YoCompletionResult finished = result;
            if (finished == null || finished.usage() == null) {
                return -1;
            }
            int tokens = finished.usage().outputTokens();
            return tokens > 0 ? tokens : -1;
        }

        synchronized String sample() {
            String text = sample.toString().strip();
            return text.isEmpty() ? null : text;
        }

        void abort() {
            aborted = true;
            YoAiCall current = call;
            if (current != null) {
                current.cancel();
            }
            done.countDown();
        }

        @Override
        public void onEvent(@NonNull YoCompletionEvent event) {
            if (firstNanos < 0) {
                firstNanos = System.nanoTime();
            }
            if (event.type() == YoCompletionEvent.Type.TEXT) {
                synchronized (this) {
                    if (sample.length() < SAMPLE_CHARS) {
                        sample.append(event.delta());
                    }
                }
            }
        }

        @Override
        public void onComplete(@NonNull YoCompletionResult completion) {
            result = completion;
            endNanos = System.nanoTime();
            done.countDown();
        }

        @Override
        public void onFailure(@NonNull YoAiException failure) {
            error = failure;
            endNanos = System.nanoTime();
            done.countDown();
        }
    }
}

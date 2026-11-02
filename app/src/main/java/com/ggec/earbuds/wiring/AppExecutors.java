package com.ggec.earbuds.wiring;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程级线程语义。每条串行队列只承担一类工作，避免设置写盘、聊天、音频与历史读取互相排队。
 */
public final class AppExecutors {

    private final Executor settingsPersistence;
    private final Executor chatState;
    private final Executor voiceState;
    private final Executor historyIo;
    private final Executor settingsOperation;
    private final Executor realtimeFetch;
    private final Executor aiCallbacks;
    public AppExecutors() {
        settingsPersistence = serial("ai-settings-persist");
        chatState = serial("ai-chat");
        voiceState = serial("ai-voice");
        historyIo = serial("ai-history");
        settingsOperation = fixed("ai-settings-operation", 2);
        realtimeFetch = serial("ai-chatgpt-realtime");
        aiCallbacks = serial("ai-callbacks");
    }

    @NonNull public Executor settingsPersistence() {
        return settingsPersistence;
    }

    @NonNull public Executor chatState() {
        return chatState;
    }

    @NonNull public Executor voiceState() {
        return voiceState;
    }

    @NonNull public Executor historyIo() {
        return historyIo;
    }

    @NonNull public Executor settingsOperation() {
        return settingsOperation;
    }

    @NonNull public Executor realtimeFetch() {
        return realtimeFetch;
    }

    @NonNull public Executor aiCallbacks() {
        return aiCallbacks;
    }

    @NonNull
    private static Executor serial(String name) {
        return fixed(name, 1);
    }

    @NonNull
    private static Executor fixed(String name, int threads) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}

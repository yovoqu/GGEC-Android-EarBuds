package com.ggec.earbuds.wiring;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程级线程语义。每条串行队列只承担一类工作，避免设置写盘、聊天、音频与历史读取互相排队。
 *
 * <p>定时任务只有语音会话在用，因此单独给一条 {@link ScheduledExecutorService}：
 * 会话只取消自己排的任务，线程池本身随进程退出回收。</p>
 */
public final class AppExecutors {

    private final Executor settingsPersistence;
    private final Executor chatState;
    private final Executor voiceState;
    private final Executor historyIo;
    private final Executor settingsOperation;
    private final Executor realtimeFetch;
    private final Executor aiCallbacks;
    private final Executor deviceStore;
    private final ScheduledExecutorService voiceTimer;

    public AppExecutors() {
        settingsPersistence = serial("ai-settings-persist");
        chatState = serial("ai-chat");
        voiceState = serial("ai-voice");
        historyIo = serial("ai-history");
        settingsOperation = fixed("ai-settings-operation", 2);
        realtimeFetch = serial("ai-chatgpt-realtime");
        aiCallbacks = serial("ai-callbacks");
        deviceStore = serial("device-store");
        voiceTimer = scheduled("ai-voice-timer");
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

    /** 设备台账落盘：单写者，合并写入。 */
    @NonNull public Executor deviceStore() {
        return deviceStore;
    }

    /** 语音会话的定时器：播放排空延时与 THINKING 超时共用一条线程。 */
    @NonNull public ScheduledExecutorService voiceTimer() {
        return voiceTimer;
    }

    @NonNull
    private static Executor serial(String name) {
        return fixed(name, 1);
    }

    @NonNull
    private static Executor fixed(String name, int threads) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threadFactory(name));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @NonNull
    private static ScheduledExecutorService scheduled(String name) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, threadFactory(name));
        // 取消掉的任务立刻出队：语音会话每次打断都会取消上一轮定时，队列不能攒着它们。
        executor.setRemoveOnCancelPolicy(true);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @NonNull
    private static ThreadFactory threadFactory(String name) {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}

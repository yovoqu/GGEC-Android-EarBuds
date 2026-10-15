package com.yovo.lib_bluetooth.internal.classic.spp.io;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接异步写器，在专用线程中顺序写入 OutputStream。
 */

public final class YoCbtSppWriter {

    public interface Listener {
        void onWriteCompleted(@NonNull YoCbtSppConnectionId connectionId, @NonNull byte[] data);
        void onWriteError(@NonNull YoCbtSppConnectionId connectionId, @NonNull YoCbtFailure failure);
    }

    private static final String TAG = "YoCbtSppWriter";
    private static final long POLL_TIMEOUT_MILLIS = 1000L;

    private final YoCbtSppConnectionId connectionId;
    private final OutputStream outputStream;
    private final YoCbtRuntime runtime;
    private final Listener listener;
    private final YoCbtSppWriteQueue writeQueue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Thread> writerThread = new AtomicReference<>(null);

    public YoCbtSppWriter(@NonNull YoCbtSppConnectionId connectionId,
                          @NonNull OutputStream outputStream,
                          @NonNull YoCbtRuntime runtime,
                          @NonNull Listener listener) {
        this(connectionId, outputStream, runtime, listener, Integer.MAX_VALUE);
    }

    public YoCbtSppWriter(@NonNull YoCbtSppConnectionId connectionId,
                          @NonNull OutputStream outputStream,
                          @NonNull YoCbtRuntime runtime,
                          @NonNull Listener listener,
                          int queueCapacity) {
        this.connectionId = connectionId;
        this.outputStream = outputStream;
        this.runtime = runtime;
        this.listener = listener;
        this.writeQueue = new YoCbtSppWriteQueue(outputStream, queueCapacity);
    }

    /**
     * 将数据入队写入并立即返回。数据将在写入器专用消费线程上异步写出。
     *
     * @return 若数据已入队则返回 YoResult.success(null)，
     *         未运行或队列已满则返回 YoResult.failure(...)。
     */
    @NonNull
    public com.yovo.lib_bluetooth.internal.common.result.YoResult<Void> write(@NonNull byte[] data) {
        if (!running.get()) {
            return com.yovo.lib_bluetooth.internal.common.result.YoResult.failure(
                    new YoCbtFailure(
                            YoCbtErrorCode.YO_CBT_SPP_WRITE_FAILED,
                            "SPP writer is not running for " + connectionId,
                            null,
                            false,
                            true,
                            "spp",
                            null,
                            null,
                            connectionId,
                            null,
                            "Start the connection before writing"
                    )
            );
        }
        if (data.length == 0) {
            return com.yovo.lib_bluetooth.internal.common.result.YoResult.success(null);
        }
        boolean offered = writeQueue.offer(Arrays.copyOf(data, data.length));
        if (!offered) {
            return com.yovo.lib_bluetooth.internal.common.result.YoResult.failure(
                    new YoCbtFailure(
                            YoCbtErrorCode.YO_CBT_SPP_WRITE_FAILED,
                            "SPP write queue is full for " + connectionId,
                            null,
                            false,
                            true,
                            "spp",
                            null,
                            null,
                            connectionId,
                            null,
                            "Reduce write volume or increase queue capacity"
                    )
            );
        }
        return com.yovo.lib_bluetooth.internal.common.result.YoResult.success(null);
    }

    /**
     * 在专用线程上启动写入器。每个连接拥有独立的写入线程，
     * 以实现正确的连接隔离。
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            YoBluetoothLogger.w(TAG, "写入器已在运行，连接 " + connectionId);
            return;
        }
        String threadName = "yo-cbt-spp-writer-" + connectionId;
        Thread thread = new Thread(this::runLoop, threadName);
        thread.setDaemon(true);
        writerThread.set(thread);
        thread.start();
        YoBluetoothLogger.d(TAG, "写入器已启动，连接 " + connectionId + "，线程 " + threadName);
    }

    /**
     * 停止写入器。队列中待写入的数据将不再处理。
     * 若需在停止前排空剩余写入，可先调用 {@link #pendingCount()} 并在外部处理。
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        YoBluetoothLogger.d(TAG, "正在停止写入器，连接 " + connectionId);
        Thread thread = writerThread.getAndSet(null);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        writeQueue.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 返回当前已入队、等待发送的写入数量。
     */
    public int pendingCount() {
        return writeQueue.size();
    }

    private void runLoop() {
        Thread currentThread = Thread.currentThread();
        YoBluetoothLogger.d(TAG, "写入循环已开始，连接 " + connectionId);

        try {
            while (running.get() && !currentThread.isInterrupted()) {
                byte[] data;
                try {
                    data = writeQueue.poll(POLL_TIMEOUT_MILLIS);
                } catch (InterruptedException e) {
                    currentThread.interrupt();
                    break;
                }

                if (data == null) {
                    continue;
                }

                try {
                    outputStream.write(data);
                    outputStream.flush();
                } catch (IOException e) {
                    if (!running.get() || currentThread.isInterrupted()) {
                        break;
                    }
                    YoBluetoothLogger.e(TAG, "写入出错，连接 " + connectionId, e);
                    YoCbtFailure failure = new YoCbtFailure(
                            YoCbtErrorCode.YO_CBT_SPP_WRITE_FAILED,
                            "SPP write failed: " + e.getMessage(),
                            e,
                            false,
                            true,
                            "spp",
                            null,
                            null,
                            connectionId,
                            null,
                            "Check Bluetooth connection and retry"
                    );
                    final YoCbtFailure captured = failure;
                    runtime.callback(() -> listener.onWriteError(connectionId, captured));
                    continue;
                }

                // 在回调线程上通知成功
                final byte[] written = data;
                if (running.get()) {
                    runtime.callback(() -> {
                        if (running.get()) {
                            listener.onWriteCompleted(connectionId, written);
                        }
                    });
                }
            }
        } finally {
            running.set(false);
            writerThread.compareAndSet(currentThread, null);
            writeQueue.clear();
            YoBluetoothLogger.d(TAG, "写入循环已结束，连接 " + connectionId);
        }
    }
}

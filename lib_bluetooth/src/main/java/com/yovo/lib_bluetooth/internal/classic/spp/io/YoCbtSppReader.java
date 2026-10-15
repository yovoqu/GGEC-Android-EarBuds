package com.yovo.lib_bluetooth.internal.classic.spp.io;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接异步读线程，从 InputStream 读取数据并回调上层。
 */

public final class YoCbtSppReader implements Runnable {

    public interface Listener {
        void onDataReceived(@NonNull YoCbtSppConnectionId connectionId, @NonNull byte[] data);
        void onReadError(@NonNull YoCbtSppConnectionId connectionId, @NonNull YoCbtFailure failure);
        void onReadComplete(@NonNull YoCbtSppConnectionId connectionId);
    }

    private static final String TAG = "YoCbtSppReader";
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private final YoCbtSppConnectionId connectionId;
    private final InputStream inputStream;
    private final YoCbtRuntime runtime;
    private final Listener listener;
    private final int bufferSize;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Thread> readerThread = new AtomicReference<>(null);

    public YoCbtSppReader(@NonNull YoCbtSppConnectionId connectionId,
                          @NonNull InputStream inputStream,
                          @NonNull YoCbtRuntime runtime,
                          @NonNull Listener listener,
                          int bufferSize) {
        this.connectionId = connectionId;
        this.inputStream = inputStream;
        this.runtime = runtime;
        this.listener = listener;
        this.bufferSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
    }

    public YoCbtSppReader(@NonNull YoCbtSppConnectionId connectionId,
                          @NonNull InputStream inputStream,
                          @NonNull YoCbtRuntime runtime,
                          @NonNull Listener listener) {
        this(connectionId, inputStream, runtime, listener, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 在专用线程上启动读取器。线程命名以标识所服务的连接，
     * 确保连接间正确隔离。
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            YoBluetoothLogger.w(TAG, "读取器已在运行，连接 " + connectionId);
            return;
        }
        String threadName = "yo-cbt-spp-reader-" + connectionId;
        Thread thread = new Thread(this, threadName);
        thread.setDaemon(true);
        readerThread.set(thread);
        thread.start();
        YoBluetoothLogger.d(TAG, "读取器已启动，连接 " + connectionId + "，线程 " + threadName);
    }

    /**
     * 通知读取循环停止。线程可能在 read() 上阻塞，
     * 直到有数据到达或发生 IOException；外部关闭 InputStream 可解除阻塞。
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        YoBluetoothLogger.d(TAG, "正在停止读取器，连接 " + connectionId);
        Thread thread = readerThread.getAndSet(null);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        YoBluetoothLogger.d(TAG, "读取循环已开始，连接 " + connectionId);

        byte[] buffer = new byte[bufferSize];
        ByteArrayOutputStream batch = new ByteArrayOutputStream(bufferSize);

        try {
            while (running.get() && !currentThread.isInterrupted()) {
                int len;
                try {
                    len = inputStream.read(buffer);
                } catch (IOException e) {
                    if (!running.get() || currentThread.isInterrupted()) {
                        break;
                    }
                    if (isRemoteCloseException(e)) {
                        dispatchBatch(batch);
                        runtime.callback(() -> listener.onReadComplete(connectionId));
                        break;
                    }
                    throw e;
                }

                if (len < 0) {
                    dispatchBatch(batch);
                    if (running.get()) {
                        runtime.callback(() -> listener.onReadComplete(connectionId));
                    }
                    break;
                }

                batch.write(buffer, 0, len);
                drainAvailable(inputStream, buffer, batch);
                dispatchBatch(batch);
            }
        } catch (Throwable e) {
            YoBluetoothLogger.e(TAG, "读取出错，连接 " + connectionId, e);
            if (running.get()) {
                YoCbtFailure failure = new YoCbtFailure(
                        YoCbtErrorCode.YO_CBT_SPP_READ_FAILED,
                        "SPP read failed: " + e.getMessage(),
                        e,
                        false,
                        false,
                        "spp",
                        null,
                        null,
                        connectionId,
                        null,
                        "Check Bluetooth connection and retry"
                );
                final YoCbtFailure capturedFailure = failure;
                runtime.callback(() -> listener.onReadError(connectionId, capturedFailure));
            }
        } finally {
            running.set(false);
            readerThread.compareAndSet(currentThread, null);
            YoBluetoothLogger.d(TAG, "读取循环已结束，连接 " + connectionId);
        }
    }

    private void dispatchBatch(ByteArrayOutputStream batch) {
        if (batch.size() == 0 || !running.get()) {
            return;
        }
        final byte[] captured = batch.toByteArray();
        batch.reset();
        runtime.callback(() -> {
            if (running.get()) {
                listener.onDataReceived(connectionId, captured);
            }
        });
    }

    private static void drainAvailable(InputStream inputStream,
                                       byte[] buffer,
                                       ByteArrayOutputStream batch) throws IOException {
        while (inputStream.available() > 0) {
            int avail = inputStream.available();
            int toRead = Math.min(avail, buffer.length);
            int read = inputStream.read(buffer, 0, toRead);
            if (read <= 0) {
                break;
            }
            batch.write(buffer, 0, read);
        }
    }

    private static boolean isRemoteCloseException(@NonNull IOException e) {
        String message = e.getMessage();
        if (message == null) return false;
        String text = message.toLowerCase();
        return text.contains("bt socket closed")
                || text.contains("socket closed")
                || text.contains("socket might closed")
                || text.contains("connection reset")
                || text.contains("connection abort")
                || text.contains("broken pipe");
    }
}

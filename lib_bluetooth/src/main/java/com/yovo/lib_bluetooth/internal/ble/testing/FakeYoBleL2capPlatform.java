package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.channel.YoBleL2capPlatform;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * BLE L2CAP CoC 面向连接 Socket 可控 Fake 平台。
 */
public final class FakeYoBleL2capPlatform implements YoBleL2capPlatform {

    private static final byte[] EOF_MARKER = new byte[0];

    public static final class FakeSocket implements SocketConnection {
        private final BlockingQueue<byte[]> inboundQueue = new LinkedBlockingQueue<>();
        private final List<byte[]> writtenChunks = Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean closed = new AtomicBoolean(false);
        @Nullable
        private volatile IOException readException;
        @Nullable
        private volatile IOException writeException;

        public void pushInbound(@NonNull byte[] frame) {
            if (frame.length > 0) {
                inboundQueue.offer(frame.clone());
            }
        }

        public void pushEof() {
            inboundQueue.offer(EOF_MARKER);
        }

        public void failNextWrite(@NonNull IOException ex) {
            this.writeException = ex;
        }

        public void failRead(@NonNull IOException ex) {
            this.readException = ex;
            inboundQueue.offer(EOF_MARKER);
        }

        @NonNull
        public List<byte[]> writtenChunks() {
            synchronized (writtenChunks) {
                return new ArrayList<>(writtenChunks);
            }
        }

        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public int read(@NonNull byte[] buffer) throws IOException {
            try {
                byte[] next = inboundQueue.take();
                IOException err = readException;
                if (err != null) {
                    throw err;
                }
                if (next == EOF_MARKER || closed.get()) {
                    return -1;
                }
                int len = Math.min(buffer.length, next.length);
                System.arraycopy(next, 0, buffer, 0, len);
                return len;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }

        @Override
        public void write(@NonNull byte[] data) throws IOException {
            if (closed.get()) {
                throw new IOException("Socket closed");
            }
            IOException err = writeException;
            if (err != null) {
                writeException = null;
                throw err;
            }
            writtenChunks.add(data.clone());
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                inboundQueue.offer(EOF_MARKER);
            }
        }
    }

    @Nullable
    private IOException openFailure;
    @Nullable
    private FakeSocket lastSocket;
    private int lastPsm = -1;
    private boolean lastSecure = false;
    private boolean blockOpenUntilHookClosed = false;
    @Nullable
    private CountDownLatch openAttemptStartedLatch;
    private final AtomicBoolean lastOpenHookClosed = new AtomicBoolean(false);

    public FakeYoBleL2capPlatform setOpenFailure(@Nullable IOException openFailure) {
        this.openFailure = openFailure;
        return this;
    }

    public FakeYoBleL2capPlatform setBlockOpenUntilHookClosed(boolean block,
                                                              @Nullable CountDownLatch startedLatch) {
        this.blockOpenUntilHookClosed = block;
        this.openAttemptStartedLatch = startedLatch;
        return this;
    }

    public boolean isLastOpenHookClosed() {
        return lastOpenHookClosed.get();
    }

    @Nullable
    public FakeSocket lastSocket() {
        return lastSocket;
    }

    public int lastPsm() {
        return lastPsm;
    }

    public boolean lastSecure() {
        return lastSecure;
    }

    @NonNull
    @Override
    public SocketConnection openClientChannel(@NonNull BleDeviceId deviceId, int psm, boolean secure)
            throws IOException {
        return openClientChannel(deviceId, psm, secure, null);
    }

    @NonNull
    @Override
    public SocketConnection openClientChannel(@NonNull BleDeviceId deviceId,
                                              int psm,
                                              boolean secure,
                                              @Nullable Consumer<Closeable> cancelHookConsumer)
            throws IOException {
        if (blockOpenUntilHookClosed) {
            this.lastPsm = psm;
            this.lastSecure = secure;
            lastOpenHookClosed.set(false);
            CountDownLatch unblockLatch = new CountDownLatch(1);
            if (cancelHookConsumer != null) {
                cancelHookConsumer.accept(() -> {
                    lastOpenHookClosed.set(true);
                    unblockLatch.countDown();
                });
            }
            if (openAttemptStartedLatch != null) {
                openAttemptStartedLatch.countDown();
            }
            try {
                unblockLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Fake L2CAP open aborted via close hook");
        }
        if (openFailure != null) {
            IOException ex = openFailure;
            openFailure = null;
            throw ex;
        }
        this.lastPsm = psm;
        this.lastSecure = secure;
        this.lastSocket = new FakeSocket();
        return this.lastSocket;
    }
}

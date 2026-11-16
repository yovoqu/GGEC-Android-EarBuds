package com.yovo.biz_ggec_record.internal.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * H.264 / H.265 Annex B 裸码流硬解器。
 * BLE 回调线程 {@link #enqueue} 按起始码切完整 NAL 入队；解码线程喂 {@link MediaCodec}。
 */
public final class VideoStreamDecoder {

    private static final String TAG = "VideoStreamDecoder";
    private static final int MAX_QUEUE_BYTES = 4 * 1024 * 1024;
    private static final long INPUT_TIMEOUT_US = 10_000L;

    public interface Listener {
        void onDecoderError(@NonNull String message);

        void onVideoSizeChanged(int width, int height);
    }

    @Nullable
    private volatile Listener listener;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
    private int queueBytes;

    private final Object enqueueLock = new Object();
    private byte[] acc = new byte[256 * 1024];
    private int accLen;
    private boolean streamStarted;

    public final AtomicLong droppedChunks = new AtomicLong();
    public final AtomicLong decodedFrames = new AtomicLong();
    public final AtomicLong startCodeCount = new AtomicLong();

    private final AtomicLong totalEnqueued = new AtomicLong();
    private final AtomicLong nalLogged = new AtomicLong();

    private volatile boolean running;
    @Nullable
    private Thread decodeThread;

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void enqueue(@NonNull byte[] data) {
        long pos = totalEnqueued.getAndAdd(data.length);
        if (pos < 256) {
            int n = Math.min(data.length, (int) (256 - pos));
            Log.i(TAG, "HEAD " + pos + ".." + (pos + n - 1) + ": " + hex(data, 0, n));
        }
        synchronized (enqueueLock) {
            splitNals(data);
        }
    }

    public int queuedBytes() {
        lock.lock();
        try {
            return queueBytes;
        } finally {
            lock.unlock();
        }
    }

    public void start(@NonNull Surface surface, @NonNull String mime) {
        stop();
        clearQueue();
        synchronized (enqueueLock) {
            accLen = 0;
            streamStarted = false;
        }
        running = true;
        decodeThread = new Thread(() -> decodeLoop(surface, mime), "video-decode");
        decodeThread.start();
    }

    public void stop() {
        running = false;
        lock.lock();
        try {
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
        Thread thread = decodeThread;
        if (thread != null) {
            try {
                thread.join(1_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        decodeThread = null;
    }

    public boolean isRunning() {
        return running;
    }

    private void splitNals(@NonNull byte[] data) {
        ensureAccCapacity(accLen + data.length);
        System.arraycopy(data, 0, acc, accLen, data.length);
        accLen += data.length;

        if (accLen > MAX_QUEUE_BYTES) {
            accLen = 0;
            streamStarted = false;
            return;
        }

        if (!streamStarted) {
            int p = 0;
            while (p + 4 <= accLen && scLenAt(acc, p, accLen) == 0) {
                p++;
            }
            if (p + 4 > accLen) {
                if (accLen > 3) {
                    System.arraycopy(acc, accLen - 3, acc, 0, 3);
                    accLen = 3;
                }
                return;
            }
            System.arraycopy(acc, p, acc, 0, accLen - p);
            accLen -= p;
            streamStarted = true;
        }

        while (true) {
            int firstSc = scLenAt(acc, 0, accLen);
            if (firstSc == 0) {
                accLen = 0;
                streamStarted = false;
                return;
            }
            int p = firstSc;
            while (p + 4 <= accLen && scLenAt(acc, p, accLen) == 0) {
                p++;
            }
            if (p + 4 > accLen) {
                return;
            }

            byte[] frame = copyOfRange(acc, 0, p);
            pushFrame(frame);
            startCodeCount.incrementAndGet();
            logNalHeader(frame, scLenAt(frame, 0, frame.length));

            System.arraycopy(acc, p, acc, 0, accLen - p);
            accLen -= p;
        }
    }

    private void pushFrame(@NonNull byte[] frame) {
        lock.lock();
        try {
            while (queueBytes + frame.length > MAX_QUEUE_BYTES && !chunks.isEmpty()) {
                byte[] old = chunks.removeFirst();
                queueBytes -= old.length;
                droppedChunks.incrementAndGet();
            }
            chunks.addLast(frame);
            queueBytes += frame.length;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    private void clearQueue() {
        lock.lock();
        try {
            chunks.clear();
            queueBytes = 0;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    private byte[] takeChunk(long timeoutMs) {
        lock.lock();
        try {
            if (chunks.isEmpty()) {
                notEmpty.await(timeoutMs, TimeUnit.MILLISECONDS);
            }
            byte[] chunk = chunks.pollFirst();
            if (chunk == null) {
                return null;
            }
            queueBytes -= chunk.length;
            return chunk;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    private byte[] pollChunk() {
        lock.lock();
        try {
            byte[] chunk = chunks.pollFirst();
            if (chunk == null) {
                return null;
            }
            queueBytes -= chunk.length;
            return chunk;
        } finally {
            lock.unlock();
        }
    }

    private void decodeLoop(@NonNull Surface surface, @NonNull String mime) {
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createDecoderByType(mime);
            MediaFormat format = MediaFormat.createVideoFormat(mime, 1280, 720);
            codec.configure(format, surface, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] pending = null;
            long fedBytes = 0L;
            long lastStatLogMs = 0L;

            while (running) {
                if (pending == null) {
                    pending = takeChunk(20);
                }
                if (pending != null) {
                    int inIdx = codec.dequeueInputBuffer(INPUT_TIMEOUT_US);
                    if (inIdx >= 0) {
                        java.nio.ByteBuffer buf = codec.getInputBuffer(inIdx);
                        if (buf != null) {
                            buf.clear();
                            int filled = 0;
                            byte[] c = pending;
                            while (c != null) {
                                if (c.length > buf.remaining()) {
                                    if (filled == 0) {
                                        int part = buf.remaining();
                                        buf.put(c, 0, part);
                                        c = copyOfRange(c, part, c.length);
                                        filled += part;
                                    }
                                    break;
                                }
                                buf.put(c);
                                filled += c.length;
                                c = pollChunk();
                            }
                            pending = c;
                            if (filled > 0) {
                                fedBytes += filled;
                                codec.queueInputBuffer(
                                        inIdx, 0, filled, System.nanoTime() / 1000, 0);
                            }
                        }
                    }
                }

                int outIdx = codec.dequeueOutputBuffer(info, 0);
                while (outIdx >= 0) {
                    if (info.size > 0) {
                        decodedFrames.incrementAndGet();
                    }
                    codec.releaseOutputBuffer(outIdx, true);
                    outIdx = codec.dequeueOutputBuffer(info, 0);
                }
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = codec.getOutputFormat();
                    int w = f.getInteger(MediaFormat.KEY_WIDTH);
                    int h = f.getInteger(MediaFormat.KEY_HEIGHT);
                    int cropRight = f.containsKey("crop-right") ? f.getInteger("crop-right") : w - 1;
                    int cropBottom = f.containsKey("crop-bottom") ? f.getInteger("crop-bottom") : h - 1;
                    int cropLeft = f.containsKey("crop-left") ? f.getInteger("crop-left") : 0;
                    int cropTop = f.containsKey("crop-top") ? f.getInteger("crop-top") : 0;
                    w = cropRight - cropLeft + 1;
                    h = cropBottom - cropTop + 1;
                    Log.i(TAG, "输出格式变化: " + w + "x" + h);
                    Listener current = listener;
                    if (current != null) {
                        current.onVideoSizeChanged(w, h);
                    }
                }

                long nowMs = System.currentTimeMillis();
                if (nowMs - lastStatLogMs > 5_000) {
                    lastStatLogMs = nowMs;
                    Log.i(TAG, "stat: fed=" + fedBytes + "B out=" + decodedFrames.get()
                            + "帧 queue=" + (queuedBytes() / 1024) + "KB drop="
                            + droppedChunks.get());
                }
            }
        } catch (Exception e) {
            if (running) {
                Log.e(TAG, "解码器异常", e);
                Listener current = listener;
                if (current != null) {
                    current.onDecoderError("解码器异常: " + e.getMessage());
                }
            }
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                try {
                    codec.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int scLenAt(@NonNull byte[] b, int p, int limit) {
        if (p + 4 <= limit
                && b[p] == 0 && b[p + 1] == 0 && b[p + 2] == 0 && b[p + 3] == 1) {
            return 4;
        }
        if (p + 3 <= limit
                && b[p] == 0 && b[p + 1] == 0 && b[p + 2] == 1) {
            return 3;
        }
        return 0;
    }

    private void ensureAccCapacity(int needed) {
        if (needed <= acc.length) {
            return;
        }
        int cap = acc.length * 2;
        while (cap < needed) {
            cap *= 2;
        }
        byte[] next = new byte[cap];
        System.arraycopy(acc, 0, next, 0, accLen);
        acc = next;
    }

    private void logNalHeader(@NonNull byte[] nal, int hdrStart) {
        if (nalLogged.get() >= 32) {
            return;
        }
        long logged = nalLogged.incrementAndGet();
        if (logged > 32) {
            return;
        }
        int end = Math.min(nal.length, hdrStart + 8);
        if (hdrStart >= end) {
            return;
        }
        Log.i(TAG, "NAL #" + logged + " hdr: " + hex(nal, hdrStart, end - hdrStart));
    }

    @NonNull
    private static byte[] copyOfRange(@NonNull byte[] src, int from, int to) {
        int len = to - from;
        byte[] out = new byte[len];
        System.arraycopy(src, from, out, 0, len);
        return out;
    }

    @NonNull
    private static String hex(@NonNull byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length * 3);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02x", data[offset + i] & 0xff));
        }
        return sb.toString();
    }
}

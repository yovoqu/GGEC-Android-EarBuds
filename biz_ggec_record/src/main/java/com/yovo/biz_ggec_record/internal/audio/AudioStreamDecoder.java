package com.yovo.biz_ggec_record.internal.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_record.model.RecordAudioCodec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 非阻塞 BLE 音频队列 + MediaCodec 解码 + AudioTrack 播放 + 16 kHz 识别 PCM 输出。
 */
public final class AudioStreamDecoder {

    private static final String TAG = "AudioStreamDecoder";
    private static final int MAX_QUEUE_BYTES = 1024 * 1024;
    private static final long INPUT_TIMEOUT_US = 10_000L;

    public static final class Config {
        @NonNull
        public final RecordAudioCodec codec;
        public final int sampleRate;
        public final int channels;

        public Config(@NonNull RecordAudioCodec codec, int sampleRate, int channels) {
            this.codec = codec;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    public interface Listener {
        void onAudioSamples(@NonNull float[] samples);

        void onAudioError(@NonNull String message);
    }

    @Nullable
    private volatile Listener listener;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
    private int queueBytes;
    private final PcmNormalizer normalizer = new PcmNormalizer();

    private volatile boolean running;
    private volatile boolean muted;
    @Nullable
    private Thread decodeThread;
    @Nullable
    private AudioTrack audioTrack;

    public final AtomicLong droppedChunks = new AtomicLong();

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public int queuedBytes() {
        lock.lock();
        try {
            return queueBytes;
        } finally {
            lock.unlock();
        }
    }

    public void setMuted(boolean value) {
        muted = value;
    }

    public void enqueue(@NonNull byte[] data) {
        if (!running || data.length == 0) {
            return;
        }
        lock.lock();
        try {
            while (queueBytes + data.length > MAX_QUEUE_BYTES && !chunks.isEmpty()) {
                queueBytes -= chunks.removeFirst().length;
                droppedChunks.incrementAndGet();
            }
            if (data.length <= MAX_QUEUE_BYTES) {
                chunks.addLast(data.clone());
                queueBytes += data.length;
                notEmpty.signal();
            } else {
                droppedChunks.incrementAndGet();
            }
        } finally {
            lock.unlock();
        }
    }

    public void start(@NonNull Config config) {
        stop();
        clearQueue();
        normalizer.reset();
        running = true;
        decodeThread = new Thread(() -> decodeLoop(config), "audio-decode");
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
        clearQueue();
    }

    public boolean isRunning() {
        return running;
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
            if (chunk != null) {
                queueBytes -= chunk.length;
            }
            return chunk;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    private void decodeLoop(@NonNull Config config) {
        try {
            if (config.codec == RecordAudioCodec.PCM) {
                startAudioTrack(config.sampleRate, config.channels);
                pcmLoop(config);
            } else {
                compressedLoop(config);
            }
        } catch (Exception e) {
            if (running) {
                Log.e(TAG, "音频解码异常", e);
                Listener current = listener;
                if (current != null) {
                    current.onAudioError("音频解码异常: " + e.getMessage());
                }
            }
        } finally {
            running = false;
            AudioTrack track = audioTrack;
            if (track != null) {
                try {
                    track.stop();
                } catch (Exception ignored) {
                }
                track.release();
            }
            audioTrack = null;
        }
    }

    private void pcmLoop(@NonNull Config config) {
        int carry = -1;
        while (running) {
            byte[] chunk = takeChunk(20);
            if (chunk == null) {
                continue;
            }
            byte[] data = chunk;
            if (carry >= 0) {
                data = new byte[chunk.length + 1];
                data[0] = (byte) carry;
                System.arraycopy(chunk, 0, data, 1, chunk.length);
                carry = -1;
            }
            int evenSize = data.length - (data.length % 2);
            if (evenSize < data.length) {
                carry = data[data.length - 1] & 0xff;
            }
            if (evenSize > 0) {
                byte[] even = new byte[evenSize];
                System.arraycopy(data, 0, even, 0, evenSize);
                emitPcm(even, config.sampleRate, config.channels);
            }
        }
    }

    private void compressedLoop(@NonNull Config config) throws Exception {
        String mime = config.codec == RecordAudioCodec.AAC
                ? MediaFormat.MIMETYPE_AUDIO_AAC
                : "audio/opus";
        MediaFormat format = MediaFormat.createAudioFormat(mime, 48_000, config.channels);
        if (config.codec == RecordAudioCodec.OPUS) {
            configureOpus(format, config);
        }
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        try {
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            AdtsParser adts = new AdtsParser();
            byte[] pending = null;
            int outputRate = 48_000;
            int outputChannels = config.channels;
            while (running) {
                if (pending == null) {
                    if (config.codec == RecordAudioCodec.AAC) {
                        pending = adts.nextFrame();
                        while (pending == null && running) {
                            byte[] chunk = takeChunk(20);
                            if (chunk == null) {
                                break;
                            }
                            adts.append(chunk);
                            pending = adts.nextFrame();
                        }
                    } else {
                        pending = takeChunk(20);
                    }
                }
                if (pending != null) {
                    int inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(inputIndex);
                        if (buffer != null && pending.length <= buffer.remaining()) {
                            buffer.clear();
                            buffer.put(pending);
                            codec.queueInputBuffer(
                                    inputIndex, 0, pending.length, System.nanoTime() / 1000, 0);
                        } else {
                            Listener current = listener;
                            if (current != null) {
                                current.onAudioError("音频包超过解码器输入容量: " + pending.length + " B");
                            }
                        }
                        pending = null;
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 0);
                while (outputIndex >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(outputIndex);
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        byte[] pcm = new byte[info.size];
                        buffer.get(pcm);
                        emitPcm(pcm, outputRate, outputChannels);
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                    outputIndex = codec.dequeueOutputBuffer(info, 0);
                }
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat changed = codec.getOutputFormat();
                    outputRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    outputChannels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    startAudioTrack(outputRate, outputChannels);
                    normalizer.reset();
                    Log.i(TAG, "音频输出格式: " + outputRate + " Hz, " + outputChannels + " 声道");
                }
            }
        } finally {
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            codec.release();
        }
    }

    private void configureOpus(@NonNull MediaFormat format, @NonNull Config config) {
        int preSkip = 312;
        ByteBuffer header = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        header.put("OpusHead".getBytes(StandardCharsets.US_ASCII));
        header.put((byte) 1);
        header.put((byte) config.channels);
        header.putShort((short) preSkip);
        header.putInt(config.sampleRate);
        header.putShort((short) 0);
        header.put((byte) 0);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(header.array()));

        ByteBuffer csd1 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        csd1.putLong(preSkip);
        csd1.flip();
        format.setByteBuffer("csd-1", csd1);

        ByteBuffer csd2 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        csd2.putLong(0);
        csd2.flip();
        format.setByteBuffer("csd-2", csd2);
    }

    private void emitPcm(@NonNull byte[] pcm, int sampleRate, int channels) {
        if (!muted) {
            AudioTrack track = audioTrack;
            if (track != null) {
                track.write(pcm, 0, pcm.length);
            }
        }
        float[] samples = normalizer.normalize(pcm, sampleRate, channels);
        if (samples.length > 0) {
            Listener current = listener;
            if (current != null) {
                current.onAudioSamples(samples);
            }
        }
    }

    private void startAudioTrack(int sampleRate, int channels) {
        AudioTrack old = audioTrack;
        if (old != null) {
            old.release();
        }
        int channelMask = channels == 1
                ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;
        int minBuffer = AudioTrack.getMinBufferSize(
                sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, sampleRate * channels * 2 / 10);
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channelMask)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        audioTrack.play();
    }

    /** 可从任意 Notify 切片中恢复完整 ADTS 帧。 */
    private static final class AdtsParser {
        private static final int MAX_BUFFER_BYTES = 512 * 1024;

        private byte[] buffer = new byte[32 * 1024];
        private int length;

        void append(@NonNull byte[] data) {
            if (length + data.length > buffer.length) {
                int capacity = buffer.length;
                while (capacity < length + data.length) {
                    capacity *= 2;
                }
                byte[] next = new byte[capacity];
                System.arraycopy(buffer, 0, next, 0, length);
                buffer = next;
            }
            System.arraycopy(data, 0, buffer, length, data.length);
            length += data.length;
            if (length > MAX_BUFFER_BYTES) {
                int keep = Math.min(length, 8 * 1024);
                System.arraycopy(buffer, length - keep, buffer, 0, keep);
                length = keep;
            }
        }

        @Nullable
        byte[] nextFrame() {
            int offset = 0;
            while (offset + 7 <= length) {
                if ((buffer[offset] & 0xff) != 0xff
                        || (buffer[offset + 1] & 0xf6) != 0xf0) {
                    offset++;
                    continue;
                }
                int frameLength = ((buffer[offset + 3] & 0x03) << 11)
                        | ((buffer[offset + 4] & 0xff) << 3)
                        | ((buffer[offset + 5] & 0xe0) >>> 5);
                int headerLength = (buffer[offset + 1] & 0x01) == 0x01 ? 7 : 9;
                if (frameLength < headerLength) {
                    offset++;
                    continue;
                }
                if (offset + frameLength > length) {
                    break;
                }
                byte[] frame = new byte[frameLength];
                System.arraycopy(buffer, offset, frame, 0, frameLength);
                int remaining = length - offset - frameLength;
                System.arraycopy(buffer, offset + frameLength, buffer, 0, remaining);
                length = remaining;
                return frame;
            }
            if (offset > 0) {
                System.arraycopy(buffer, offset, buffer, 0, length - offset);
                length -= offset;
            }
            return null;
        }
    }
}

package com.yovo.biz_ggec_record.internal.mux;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_record.model.RecordAudioCodec;
import com.yovo.biz_ggec_record.model.RecordVideoCodec;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 旁路封装 BLE 压缩码流为 MP4：视频 Annex-B H.264/H.265 + 可选 AAC-ADTS。
 * 与预览解码并行；不重编码。PTS 使用共享单调时钟原点。
 */
public final class AvRecorder {

    private static final String TAG = "AvRecorder";
    private static final long DEFAULT_CSD_TIMEOUT_MS = 8_000L;
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private static final int MAX_VIDEO_ACC = 2 * 1024 * 1024;
    private static final int MAX_AUDIO_ACC = 256 * 1024;

    public interface Listener {
        void onStarted(@NonNull File file);

        void onFinished(@NonNull File file, long bytes, long durationMs);

        void onFailed(@NonNull String message);
    }

    public static final class Config {
        @NonNull
        public final RecordVideoCodec videoCodec;
        @NonNull
        public final RecordAudioCodec audioCodec;
        public final int width;
        public final int height;
        public final long csdTimeoutMs;

        public Config(@NonNull RecordVideoCodec videoCodec,
                      @NonNull RecordAudioCodec audioCodec,
                      int width,
                      int height,
                      long csdTimeoutMs) {
            this.videoCodec = Objects.requireNonNull(videoCodec, "videoCodec");
            this.audioCodec = Objects.requireNonNull(audioCodec, "audioCodec");
            this.width = width > 0 ? width : DEFAULT_WIDTH;
            this.height = height > 0 ? height : DEFAULT_HEIGHT;
            this.csdTimeoutMs = csdTimeoutMs > 0 ? csdTimeoutMs : DEFAULT_CSD_TIMEOUT_MS;
        }

        @NonNull
        public static Config of(@NonNull RecordVideoCodec videoCodec,
                               @NonNull RecordAudioCodec audioCodec) {
            return new Config(videoCodec, audioCodec, DEFAULT_WIDTH, DEFAULT_HEIGHT,
                    DEFAULT_CSD_TIMEOUT_MS);
        }
    }

    private enum Phase {
        IDLE, WAITING_CSD, MUXING, STOPPING
    }

    @Nullable
    private volatile Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Nullable
    private HandlerThread thread;
    @Nullable
    private Handler handler;

    @NonNull
    private Phase phase = Phase.IDLE;
    @Nullable
    private File outputFile;
    @Nullable
    private Config config;
    @Nullable
    private MediaMuxer muxer;
    private int videoTrack = -1;
    private int audioTrack = -1;
    private boolean muxerStarted;
    private boolean wantAudio;
    private boolean videoTrackReady;
    private boolean audioTrackReady;

    @Nullable
    private byte[] vps;
    @Nullable
    private byte[] sps;
    @Nullable
    private byte[] pps;

    private byte[] videoAcc = new byte[64 * 1024];
    private int videoAccLen;
    private boolean videoStreamStarted;

    private byte[] audioAcc = new byte[16 * 1024];
    private int audioAccLen;

    private long t0Ns;
    private long lastVideoPtsUs = -1L;
    private long audioSamplesWritten;
    private int audioSampleRate = 48_000;
    private int audioChannels = 1;
    private long startedAtElapsedMs;
    private long bytesWritten;
    @Nullable
    private Runnable csdTimeout;

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public boolean isRecording() {
        return running.get();
    }

    public void start(@NonNull File output, @NonNull Config config) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(config, "config");
        stopInternal(false, null);
        running.set(true);
        thread = new HandlerThread("av-recorder");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(() -> beginLocked(output, config));
    }

    public void feedVideo(@NonNull byte[] data) {
        if (!running.get() || data.length == 0) {
            return;
        }
        Handler h = handler;
        if (h == null) {
            return;
        }
        byte[] copy = data.clone();
        h.post(() -> onVideoLocked(copy));
    }

    public void feedAudio(@NonNull byte[] data) {
        if (!running.get() || data.length == 0) {
            return;
        }
        Handler h = handler;
        if (h == null) {
            return;
        }
        byte[] copy = data.clone();
        h.post(() -> onAudioLocked(copy));
    }

    public void stop() {
        stopInternal(true, null);
    }

    private void stopInternal(boolean notifyFinished, @Nullable String failMessage) {
        running.set(false);
        Handler h = handler;
        HandlerThread t = thread;
        if (h != null && t != null) {
            h.post(() -> finishLocked(notifyFinished, failMessage));
            t.quitSafely();
            try {
                t.join(1_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
        handler = null;
    }

    private void beginLocked(@NonNull File output, @NonNull Config cfg) {
        try {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                failAndCleanup("无法创建录制目录");
                return;
            }
            if (output.exists() && !output.delete()) {
                Log.w(TAG, "无法覆盖已有文件: " + output);
            }
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            outputFile = output;
            config = cfg;
            // P0：预览可能是 PCM 路径，录制仍尝试从 BLE 字节里切 ADTS 封 AAC；
            // Opus 暂不进 MP4。超时无 AAC 则降级纯视频。
            wantAudio = cfg.audioCodec != RecordAudioCodec.OPUS;
            phase = Phase.WAITING_CSD;
            videoAccLen = 0;
            videoStreamStarted = false;
            audioAccLen = 0;
            vps = null;
            sps = null;
            pps = null;
            videoTrack = -1;
            audioTrack = -1;
            muxerStarted = false;
            videoTrackReady = false;
            audioTrackReady = !wantAudio;
            lastVideoPtsUs = -1L;
            audioSamplesWritten = 0L;
            bytesWritten = 0L;
            startedAtElapsedMs = SystemClock.elapsedRealtime();
            Listener current = listener;
            if (current != null) {
                current.onStarted(output);
            }
            csdTimeout = () -> {
                if (phase != Phase.WAITING_CSD) {
                    return;
                }
                if (videoTrackReady) {
                    // 超时仍无 AAC：降级为纯视频
                    audioTrackReady = true;
                    wantAudio = false;
                    tryStartMuxerLocked();
                } else {
                    failAndCleanup("等待视频参数集超时（未收到 SPS/PPS）");
                }
            };
            Handler h = handler;
            if (h != null) {
                h.postDelayed(csdTimeout, cfg.csdTimeoutMs);
            }
        } catch (IOException e) {
            failAndCleanup("创建 MediaMuxer 失败: " + e.getMessage());
        }
    }

    private void onVideoLocked(@NonNull byte[] data) {
        if (phase != Phase.WAITING_CSD && phase != Phase.MUXING) {
            return;
        }
        ensureCapacity(true, videoAccLen + data.length);
        System.arraycopy(data, 0, videoAcc, videoAccLen, data.length);
        videoAccLen += data.length;
        if (videoAccLen > MAX_VIDEO_ACC) {
            videoAccLen = 0;
            videoStreamStarted = false;
            return;
        }
        drainVideoNalsLocked();
    }

    private void onAudioLocked(@NonNull byte[] data) {
        if (!wantAudio || (phase != Phase.WAITING_CSD && phase != Phase.MUXING)) {
            return;
        }
        ensureCapacity(false, audioAccLen + data.length);
        System.arraycopy(data, 0, audioAcc, audioAccLen, data.length);
        audioAccLen += data.length;
        if (audioAccLen > MAX_AUDIO_ACC) {
            audioAccLen = 0;
            return;
        }
        drainAdtsLocked();
    }

    private void drainVideoNalsLocked() {
        if (!videoStreamStarted) {
            int p = 0;
            while (p + 4 <= videoAccLen && startCodeLen(videoAcc, p, videoAccLen) == 0) {
                p++;
            }
            if (p + 4 > videoAccLen) {
                if (videoAccLen > 3) {
                    System.arraycopy(videoAcc, videoAccLen - 3, videoAcc, 0, 3);
                    videoAccLen = 3;
                }
                return;
            }
            System.arraycopy(videoAcc, p, videoAcc, 0, videoAccLen - p);
            videoAccLen -= p;
            videoStreamStarted = true;
        }
        while (true) {
            int firstSc = startCodeLen(videoAcc, 0, videoAccLen);
            if (firstSc == 0) {
                videoAccLen = 0;
                videoStreamStarted = false;
                return;
            }
            int p = firstSc;
            while (p + 4 <= videoAccLen && startCodeLen(videoAcc, p, videoAccLen) == 0) {
                p++;
            }
            if (p + 4 > videoAccLen) {
                return;
            }
            handleNalLocked(copyRange(videoAcc, 0, p));
            System.arraycopy(videoAcc, p, videoAcc, 0, videoAccLen - p);
            videoAccLen -= p;
        }
    }

    private void handleNalLocked(@NonNull byte[] annexB) {
        int sc = startCodeLen(annexB, 0, annexB.length);
        if (sc <= 0 || annexB.length <= sc) {
            return;
        }
        byte[] nal = copyRange(annexB, sc, annexB.length);
        Config cfg = config;
        if (cfg == null) {
            return;
        }
        boolean hevc = cfg.videoCodec == RecordVideoCodec.H265;
        int nalType = hevc ? ((nal[0] >> 1) & 0x3F) : (nal[0] & 0x1F);

        if (hevc) {
            if (nalType == 32) {
                vps = nal;
                maybeReadyVideoTrackLocked();
                return;
            }
            if (nalType == 33) {
                sps = nal;
                maybeReadyVideoTrackLocked();
                return;
            }
            if (nalType == 34) {
                pps = nal;
                maybeReadyVideoTrackLocked();
                return;
            }
            // VCL: 19/20 IDR, 21 CRA, 0-9 trailing etc. Skip non-VCL 35-40 AUD/SEI etc. for samples? SEI can be written.
            if (nalType == 35 || nalType == 39 || nalType == 40) {
                // AUD / SEI — skip for mux samples
                return;
            }
            if (nalType < 0 || nalType > 31) {
                return;
            }
            boolean key = nalType == 19 || nalType == 20 || nalType == 21;
            writeVideoSampleLocked(nal, key);
        } else {
            if (nalType == 7) {
                sps = nal;
                maybeReadyVideoTrackLocked();
                return;
            }
            if (nalType == 8) {
                pps = nal;
                maybeReadyVideoTrackLocked();
                return;
            }
            if (nalType == 6 || nalType == 9) {
                return;
            }
            if (nalType == 1 || nalType == 5) {
                writeVideoSampleLocked(nal, nalType == 5);
            }
        }
    }

    private void maybeReadyVideoTrackLocked() {
        Config cfg = config;
        MediaMuxer m = muxer;
        if (cfg == null || m == null || videoTrackReady) {
            return;
        }
        boolean hevc = cfg.videoCodec == RecordVideoCodec.H265;
        if (hevc) {
            if (vps == null || sps == null || pps == null) {
                return;
            }
        } else if (sps == null || pps == null) {
            return;
        }
        try {
            String mime = hevc ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
            MediaFormat format = MediaFormat.createVideoFormat(mime, cfg.width, cfg.height);
            if (hevc) {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(concatWithStartCodes(vps, sps, pps)));
            } else {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(withStartCode(sps)));
                format.setByteBuffer("csd-1", ByteBuffer.wrap(withStartCode(pps)));
            }
            videoTrack = m.addTrack(format);
            videoTrackReady = true;
            tryStartMuxerLocked();
        } catch (RuntimeException e) {
            failAndCleanup("添加视频轨失败: " + e.getMessage());
        }
    }

    private void drainAdtsLocked() {
        while (audioAccLen >= 7) {
            int sync = findAdtsSync(audioAcc, 0, audioAccLen);
            if (sync < 0) {
                audioAccLen = 0;
                return;
            }
            if (sync > 0) {
                System.arraycopy(audioAcc, sync, audioAcc, 0, audioAccLen - sync);
                audioAccLen -= sync;
                if (audioAccLen < 7) {
                    return;
                }
            }
            boolean hasCrc = (audioAcc[1] & 0x01) == 0;
            int hdrLen = hasCrc ? 9 : 7;
            if (audioAccLen < hdrLen) {
                return;
            }
            int frameLen = ((audioAcc[3] & 0x03) << 11)
                    | ((audioAcc[4] & 0xFF) << 3)
                    | ((audioAcc[5] & 0xE0) >> 5);
            if (frameLen < hdrLen || frameLen > MAX_AUDIO_ACC) {
                System.arraycopy(audioAcc, 1, audioAcc, 0, audioAccLen - 1);
                audioAccLen -= 1;
                continue;
            }
            if (audioAccLen < frameLen) {
                return;
            }
            byte[] frame = copyRange(audioAcc, 0, frameLen);
            System.arraycopy(audioAcc, frameLen, audioAcc, 0, audioAccLen - frameLen);
            audioAccLen -= frameLen;
            handleAdtsFrameLocked(frame, hdrLen);
        }
    }

    private void handleAdtsFrameLocked(@NonNull byte[] frame, int hdrLen) {
        if (!audioTrackReady) {
            int profile = ((frame[2] & 0xC0) >> 6) + 1;
            int freqIdx = (frame[2] & 0x3C) >> 2;
            int chanCfg = ((frame[2] & 0x01) << 2) | ((frame[3] & 0xC0) >> 6);
            audioSampleRate = adtsSampleRate(freqIdx);
            audioChannels = Math.max(1, chanCfg);
            byte[] asc = new byte[]{
                    (byte) ((profile << 3) | (freqIdx >> 1)),
                    (byte) (((freqIdx & 0x01) << 7) | (chanCfg << 3))
            };
            try {
                MediaMuxer m = muxer;
                if (m == null) {
                    return;
                }
                MediaFormat format = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, audioChannels);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
                format.setByteBuffer("csd-0", ByteBuffer.wrap(asc));
                audioTrack = m.addTrack(format);
                audioTrackReady = true;
                tryStartMuxerLocked();
            } catch (RuntimeException e) {
                failAndCleanup("添加音频轨失败: " + e.getMessage());
                return;
            }
        }
        if (!muxerStarted || audioTrack < 0) {
            return;
        }
        int rawLen = frame.length - hdrLen;
        if (rawLen <= 0) {
            return;
        }
        byte[] raw = copyRange(frame, hdrLen, frame.length);
        long pts = audioSamplesWritten * 1_000_000L / Math.max(1, audioSampleRate);
        audioSamplesWritten += 1024L;
        writeSampleLocked(audioTrack, raw, pts, 0);
    }

    private void tryStartMuxerLocked() {
        if (muxerStarted || !videoTrackReady || !audioTrackReady) {
            return;
        }
        MediaMuxer m = muxer;
        if (m == null) {
            return;
        }
        try {
            m.start();
            muxerStarted = true;
            t0Ns = System.nanoTime();
            phase = Phase.MUXING;
            if (csdTimeout != null && handler != null) {
                handler.removeCallbacks(csdTimeout);
                csdTimeout = null;
            }
            Log.i(TAG, "muxer started videoOnly=" + !wantAudio
                    + " file=" + (outputFile != null ? outputFile.getName() : "?"));
        } catch (RuntimeException e) {
            failAndCleanup("MediaMuxer.start 失败: " + e.getMessage());
        }
    }

    private void writeVideoSampleLocked(@NonNull byte[] nal, boolean keyFrame) {
        if (!muxerStarted || videoTrack < 0) {
            return;
        }
        long nowUs = (System.nanoTime() - t0Ns) / 1000L;
        if (nowUs <= lastVideoPtsUs) {
            nowUs = lastVideoPtsUs + 1;
        }
        lastVideoPtsUs = nowUs;
        int flags = keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
        byte[] avcc = toLengthPrefixed(nal);
        writeSampleLocked(videoTrack, avcc, nowUs, flags);
    }

    private void writeSampleLocked(int track, @NonNull byte[] data, long ptsUs, int flags) {
        MediaMuxer m = muxer;
        if (m == null || !muxerStarted) {
            return;
        }
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.offset = 0;
            info.size = data.length;
            info.presentationTimeUs = Math.max(0L, ptsUs);
            info.flags = flags;
            m.writeSampleData(track, ByteBuffer.wrap(data), info);
            bytesWritten += data.length;
        } catch (RuntimeException e) {
            failAndCleanup("写入 MP4 失败: " + e.getMessage());
        }
    }

    private void finishLocked(boolean notifyFinished, @Nullable String failMessage) {
        if (phase == Phase.IDLE) {
            return;
        }
        phase = Phase.STOPPING;
        Runnable timeout = csdTimeout;
        csdTimeout = null;
        Handler h = handler;
        if (timeout != null && h != null) {
            h.removeCallbacks(timeout);
        }
        File file = outputFile;
        long duration = Math.max(0L, SystemClock.elapsedRealtime() - startedAtElapsedMs);
        long bytes = bytesWritten;
        boolean started = muxerStarted;
        MediaMuxer m = muxer;
        muxer = null;
        muxerStarted = false;
        if (m != null) {
            try {
                if (started) {
                    m.stop();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "muxer.stop: " + e.getMessage());
                if (failMessage == null) {
                    failMessage = "结束录制失败: " + e.getMessage();
                }
            }
            try {
                m.release();
            } catch (RuntimeException ignored) {
            }
        }
        phase = Phase.IDLE;
        running.set(false);
        outputFile = null;
        config = null;
        Listener current = listener;
        if (failMessage != null) {
            if (file != null && file.exists() && !started) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            if (current != null) {
                current.onFailed(failMessage);
            }
            HandlerThread t = thread;
            if (t != null) {
                t.quitSafely();
            }
            return;
        }
        if (notifyFinished && current != null && file != null && started) {
            current.onFinished(file, bytes, duration);
        } else if (notifyFinished && file != null && !started) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            if (current != null) {
                current.onFailed("录制过短，未写入有效媒体");
            }
        }
    }

    private void failAndCleanup(@NonNull String message) {
        Log.w(TAG, message);
        finishLocked(false, message);
    }

    private void ensureCapacity(boolean video, int needed) {
        if (video) {
            if (needed <= videoAcc.length) {
                return;
            }
            videoAcc = Arrays.copyOf(videoAcc, Math.max(needed, videoAcc.length * 2));
        } else {
            if (needed <= audioAcc.length) {
                return;
            }
            audioAcc = Arrays.copyOf(audioAcc, Math.max(needed, audioAcc.length * 2));
        }
    }

    private static int startCodeLen(@NonNull byte[] data, int offset, int end) {
        if (offset + 3 < end
                && data[offset] == 0 && data[offset + 1] == 0 && data[offset + 2] == 1) {
            return 3;
        }
        if (offset + 4 <= end
                && data[offset] == 0 && data[offset + 1] == 0
                && data[offset + 2] == 0 && data[offset + 3] == 1) {
            return 4;
        }
        return 0;
    }

    private static int findAdtsSync(@NonNull byte[] data, int offset, int end) {
        for (int i = offset; i + 1 < end; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xF0) == 0xF0) {
                return i;
            }
        }
        return -1;
    }

    private static int adtsSampleRate(int freqIdx) {
        return switch (freqIdx) {
            case 0 -> 96_000;
            case 1 -> 88_200;
            case 2 -> 64_000;
            case 3 -> 48_000;
            case 4 -> 44_100;
            case 5 -> 32_000;
            case 6 -> 24_000;
            case 7 -> 22_050;
            case 8 -> 16_000;
            case 9 -> 12_000;
            case 10 -> 11_025;
            case 11 -> 8_000;
            default -> 48_000;
        };
    }

    @NonNull
    private static byte[] toLengthPrefixed(@NonNull byte[] nal) {
        byte[] out = new byte[4 + nal.length];
        out[0] = (byte) ((nal.length >>> 24) & 0xFF);
        out[1] = (byte) ((nal.length >>> 16) & 0xFF);
        out[2] = (byte) ((nal.length >>> 8) & 0xFF);
        out[3] = (byte) (nal.length & 0xFF);
        System.arraycopy(nal, 0, out, 4, nal.length);
        return out;
    }

    @NonNull
    private static byte[] withStartCode(@NonNull byte[] nal) {
        byte[] out = new byte[4 + nal.length];
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 1;
        System.arraycopy(nal, 0, out, 4, nal.length);
        return out;
    }

    @NonNull
    private static byte[] concatWithStartCodes(@NonNull byte[]... nals) {
        int total = 0;
        for (byte[] nal : nals) {
            total += 4 + nal.length;
        }
        byte[] out = new byte[total];
        int o = 0;
        for (byte[] nal : nals) {
            out[o++] = 0;
            out[o++] = 0;
            out[o++] = 0;
            out[o++] = 1;
            System.arraycopy(nal, 0, out, o, nal.length);
            o += nal.length;
        }
        return out;
    }

    @NonNull
    private static byte[] copyRange(@NonNull byte[] src, int from, int to) {
        return Arrays.copyOfRange(src, from, to);
    }
}

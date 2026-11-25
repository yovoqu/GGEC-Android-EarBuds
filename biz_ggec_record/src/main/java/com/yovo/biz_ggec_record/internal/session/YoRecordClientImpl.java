package com.yovo.biz_ggec_record.internal.session;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_record.api.RecordConnectionObserver;
import com.yovo.biz_ggec_record.api.RecordSessionObserver;
import com.yovo.biz_ggec_record.api.YoRecordClient;
import com.yovo.biz_ggec_record.internal.audio.AudioStreamDecoder;
import com.yovo.biz_ggec_record.internal.mux.AvRecorder;
import com.yovo.biz_ggec_record.internal.video.VideoStreamDecoder;
import com.yovo.biz_ggec_record.model.RecordAudioCodec;
import com.yovo.biz_ggec_record.model.RecordConnectionState;
import com.yovo.biz_ggec_record.model.RecordLinkStats;
import com.yovo.biz_ggec_record.model.RecordVideoCodec;
import com.yovo.biz_ggec_record.provider.lib_bluetooth.YoBleRecordLink;
import com.yovo.lib_ggec_audio.record.RecordFileName;

import java.io.File;
import java.util.Objects;

/**
 * Record 会话实现：连接后硬解播放 + 旁路 MP4 录制。
 */
public final class YoRecordClientImpl implements YoRecordClient {

    private static final long STATS_INTERVAL_MS = 1_000L;

    private final Context appContext;
    private final YoBleRecordLink link;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final VideoStreamDecoder videoDecoder = new VideoStreamDecoder();
    private final AudioStreamDecoder audioDecoder = new AudioStreamDecoder();
    private final AvRecorder avRecorder = new AvRecorder();
    private final Object pipelineLock = new Object();

    @Nullable
    private volatile RecordConnectionObserver connectionObserver;
    @Nullable
    private volatile RecordSessionObserver sessionObserver;
    @Nullable
    private Surface videoSurface;
    @NonNull
    private RecordVideoCodec videoCodec = RecordVideoCodec.H265;
    @NonNull
    private RecordAudioCodec audioCodec = RecordAudioCodec.PCM;
    private int sampleRateHz = 16_000;
    private int channelCount = 1;
    private boolean muted;
    @NonNull
    private volatile RecordConnectionState state = RecordConnectionState.IDLE;
    /** 录音文件命名用的设备标识（型号/广播名）；连接时写入，空则命名回落 UNKNOWN。 */
    @NonNull
    private volatile String deviceCode = "";

    private long lastVideoBytes;
    private long lastAudioBytes;
    private long lastDecodedFrames;

    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            if (state != RecordConnectionState.READY) {
                return;
            }
            long video = link.videoRxBytes();
            long audio = link.audioRxBytes();
            long frames = videoDecoder.decodedFrames.get();
            double videoKbps = (video - lastVideoBytes) * 8.0 / 1000.0;
            double audioKbps = (audio - lastAudioBytes) * 8.0 / 1000.0;
            double fps = frames - lastDecodedFrames;
            lastVideoBytes = video;
            lastAudioBytes = audio;
            lastDecodedFrames = frames;
            RecordSessionObserver observer = sessionObserver;
            if (observer != null) {
                observer.onLinkStats(new RecordLinkStats(
                        videoKbps,
                        audioKbps,
                        fps,
                        videoDecoder.queuedBytes() / 1024,
                        audioDecoder.queuedBytes() / 1024,
                        videoDecoder.droppedChunks.get(),
                        audioDecoder.droppedChunks.get()));
            }
            mainHandler.postDelayed(this, STATS_INTERVAL_MS);
        }
    };

    public YoRecordClientImpl(@NonNull Context context, @NonNull YoBleRecordLink link) {
        Context app = context.getApplicationContext();
        this.appContext = app != null ? app : context;
        this.link = Objects.requireNonNull(link, "link");
        videoDecoder.setListener(new VideoStreamDecoder.Listener() {
            @Override
            public void onDecoderError(@NonNull String message) {
                notifySessionError(message);
            }

            @Override
            public void onVideoSizeChanged(int width, int height) {
            }
        });
        audioDecoder.setListener(new AudioStreamDecoder.Listener() {
            @Override
            public void onAudioSamples(@NonNull float[] samples) {
            }

            @Override
            public void onAudioError(@NonNull String message) {
                notifySessionError(message);
            }
        });
        avRecorder.setListener(new AvRecorder.Listener() {
            @Override
            public void onStarted(@NonNull File file) {
                RecordSessionObserver session = sessionObserver;
                if (session != null) {
                    String path = file.getAbsolutePath();
                    mainHandler.post(() -> session.onRecordingStarted(path));
                }
            }

            @Override
            public void onFinished(@NonNull File file, long bytes, long durationMs) {
                RecordSessionObserver session = sessionObserver;
                if (session != null) {
                    String path = file.getAbsolutePath();
                    mainHandler.post(() -> session.onRecordingFinished(path, bytes, durationMs));
                }
            }

            @Override
            public void onFailed(@NonNull String message) {
                RecordSessionObserver session = sessionObserver;
                if (session != null) {
                    mainHandler.post(() -> session.onRecordingFailed(message));
                }
            }
        });
        this.link.setListener(new YoBleRecordLink.Listener() {
            @Override
            public void onConnected() {
                setState(RecordConnectionState.CONNECTED);
            }

            @Override
            public void onReady() {
                setState(RecordConnectionState.READY);
                startPipelinesLocked();
                lastVideoBytes = link.videoRxBytes();
                lastAudioBytes = link.audioRxBytes();
                lastDecodedFrames = videoDecoder.decodedFrames.get();
                mainHandler.removeCallbacks(statsTicker);
                mainHandler.post(statsTicker);
            }

            @Override
            public void onDisconnected() {
                stopRecordingQuietly();
                stopPipelines();
                mainHandler.removeCallbacks(statsTicker);
                setState(RecordConnectionState.DISCONNECTED);
            }

            @Override
            public void onFailed(@NonNull String message) {
                stopRecordingQuietly();
                stopPipelines();
                mainHandler.removeCallbacks(statsTicker);
                setState(RecordConnectionState.DISCONNECTED);
                RecordConnectionObserver observer = connectionObserver;
                if (observer != null) {
                    mainHandler.post(() -> observer.onConnectionFailed(message));
                }
                notifySessionError(message);
            }

            @Override
            public void onVideoBytes(@NonNull byte[] payload) {
                videoDecoder.enqueue(payload);
                if (avRecorder.isRecording()) {
                    avRecorder.feedVideo(payload);
                }
            }

            @Override
            public void onAudioBytes(@NonNull byte[] payload) {
                audioDecoder.enqueue(payload);
                if (avRecorder.isRecording()) {
                    avRecorder.feedAudio(payload);
                }
            }
        });
    }

    @Override
    public void connect(@NonNull String macAddress, @Nullable String deviceCode) {
        Objects.requireNonNull(macAddress, "macAddress");
        this.deviceCode = deviceCode == null ? "" : deviceCode;
        setState(RecordConnectionState.CONNECTING);
        link.connect(macAddress);
    }

    @Override
    public void disconnect() {
        mainHandler.removeCallbacks(statsTicker);
        stopRecordingQuietly();
        stopPipelines();
        link.disconnect();
        setState(RecordConnectionState.DISCONNECTED);
    }

    @Override
    public void close() {
        mainHandler.removeCallbacks(statsTicker);
        stopRecordingQuietly();
        stopPipelines();
        connectionObserver = null;
        sessionObserver = null;
        synchronized (pipelineLock) {
            videoSurface = null;
        }
        link.close();
        state = RecordConnectionState.IDLE;
    }

    @Override
    public boolean isReady() {
        return state == RecordConnectionState.READY;
    }

    @Override
    public void setConnectionObserver(@Nullable RecordConnectionObserver observer) {
        this.connectionObserver = observer;
    }

    @Override
    public void setSessionObserver(@Nullable RecordSessionObserver observer) {
        this.sessionObserver = observer;
    }

    @Override
    public void setVideoSurface(@Nullable Surface surface) {
        synchronized (pipelineLock) {
            videoSurface = surface;
            if (state == RecordConnectionState.READY) {
                restartVideoLocked();
            }
        }
    }

    @Override
    public void setVideoCodec(@NonNull RecordVideoCodec codec) {
        stopRecordingQuietly();
        synchronized (pipelineLock) {
            this.videoCodec = Objects.requireNonNull(codec, "codec");
            if (state == RecordConnectionState.READY) {
                restartVideoLocked();
            }
        }
    }

    @Override
    public void setAudioConfig(@NonNull RecordAudioCodec codec, int sampleRateHz, int channelCount) {
        stopRecordingQuietly();
        synchronized (pipelineLock) {
            this.audioCodec = Objects.requireNonNull(codec, "codec");
            this.sampleRateHz = sampleRateHz;
            this.channelCount = channelCount;
            if (state == RecordConnectionState.READY) {
                restartAudioLocked();
            }
        }
    }

    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
        audioDecoder.setMuted(muted);
    }

    @Override
    public void sendText(@NonNull String text) {
        link.writeRxText(Objects.requireNonNull(text, "text"));
    }

    @Override
    public void startRecording() {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) {
            dir = appContext.getFilesDir();
        }
        File out = new File(new File(dir, "record"),
                RecordFileName.build(deviceCode, RecordFileName.TYPE_AV,
                        System.currentTimeMillis(), "mp4"));
        startRecording(out.getAbsolutePath());
    }

    @Override
    public void startRecording(@NonNull String absolutePath) {
        Objects.requireNonNull(absolutePath, "absolutePath");
        if (absolutePath.isEmpty()) {
            notifyRecordingFailed("录制路径无效");
            return;
        }
        if (state != RecordConnectionState.READY) {
            notifyRecordingFailed("设备未就绪，无法开始录制");
            return;
        }
        if (avRecorder.isRecording()) {
            notifyRecordingFailed("已在录制中");
            return;
        }
        RecordVideoCodec video;
        synchronized (pipelineLock) {
            video = videoCodec;
        }
        // 录制封装与预览解码配置解耦：P0 固定尝试 AAC-ADTS 进 MP4。
        avRecorder.start(new File(absolutePath), AvRecorder.Config.of(video, RecordAudioCodec.AAC));
    }

    @Override
    public void stopRecording() {
        if (!avRecorder.isRecording()) {
            return;
        }
        avRecorder.stop();
    }

    @Override
    public boolean isRecording() {
        return avRecorder.isRecording();
    }

    private void stopRecordingQuietly() {
        if (avRecorder.isRecording()) {
            avRecorder.stop();
        }
    }

    private void notifyRecordingFailed(@NonNull String message) {
        RecordSessionObserver session = sessionObserver;
        if (session != null) {
            mainHandler.post(() -> session.onRecordingFailed(message));
        }
    }

    private void startPipelinesLocked() {
        synchronized (pipelineLock) {
            restartAudioLocked();
            restartVideoLocked();
        }
    }

    private void stopPipelines() {
        synchronized (pipelineLock) {
            videoDecoder.stop();
            audioDecoder.stop();
        }
    }

    private void restartVideoLocked() {
        videoDecoder.stop();
        Surface surface = videoSurface;
        if (surface == null || !surface.isValid()) {
            return;
        }
        videoDecoder.start(surface, mimeFor(videoCodec));
    }

    private void restartAudioLocked() {
        audioDecoder.stop();
        audioDecoder.setMuted(muted);
        audioDecoder.start(new AudioStreamDecoder.Config(audioCodec, sampleRateHz, channelCount));
    }

    @NonNull
    private static String mimeFor(@NonNull RecordVideoCodec codec) {
        return codec == RecordVideoCodec.H264
                ? MediaFormat.MIMETYPE_VIDEO_AVC
                : MediaFormat.MIMETYPE_VIDEO_HEVC;
    }

    private void setState(@NonNull RecordConnectionState next) {
        state = next;
        RecordConnectionObserver observer = connectionObserver;
        if (observer != null) {
            mainHandler.post(() -> observer.onConnectionStateChanged(next));
        }
    }

    private void notifySessionError(@NonNull String message) {
        RecordSessionObserver session = sessionObserver;
        if (session != null) {
            mainHandler.post(() -> session.onSessionError(message));
        }
    }
}
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
import com.yovo.biz_ggec_record.internal.asr.AsrStatus;
import com.yovo.biz_ggec_record.internal.asr.SpeechRecognizer;
import com.yovo.biz_ggec_record.internal.audio.AudioStreamDecoder;
import com.yovo.biz_ggec_record.internal.video.VideoStreamDecoder;
import com.yovo.biz_ggec_record.model.RecordAsrModel;
import com.yovo.biz_ggec_record.model.RecordAudioCodec;
import com.yovo.biz_ggec_record.model.RecordConnectionState;
import com.yovo.biz_ggec_record.model.RecordLinkStats;
import com.yovo.biz_ggec_record.model.RecordSubtitleEvent;
import com.yovo.biz_ggec_record.model.RecordVideoCodec;
import com.yovo.biz_ggec_record.provider.lib_bluetooth.YoBleRecordLink;

import java.util.Objects;

/**
 * Record 会话实现。Phase 4：连接后硬解播放 + 端侧 ASR 字幕。
 */
public final class YoRecordClientImpl implements YoRecordClient {

    private static final long STATS_INTERVAL_MS = 1_000L;

    private final YoBleRecordLink link;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final VideoStreamDecoder videoDecoder = new VideoStreamDecoder();
    private final AudioStreamDecoder audioDecoder = new AudioStreamDecoder();
    private final SpeechRecognizer speechRecognizer;
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
    private boolean asrEnabled = true;
    @NonNull
    private RecordAsrModel asrModel = RecordAsrModel.ZIPFORMER_SMALL;
    @NonNull
    private volatile RecordConnectionState state = RecordConnectionState.IDLE;

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
        this.link = Objects.requireNonNull(link, "link");
        this.speechRecognizer = new SpeechRecognizer(context);
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
                if (asrEnabled) {
                    speechRecognizer.acceptPcm(samples);
                }
            }

            @Override
            public void onAudioError(@NonNull String message) {
                notifySessionError(message);
            }
        });
        speechRecognizer.setListener(new SpeechRecognizer.Listener() {
            @Override
            public void onAsrStatus(@NonNull AsrStatus status) {
                // 加载中可由 UI 通过 session error/status 扩展；暂不额外抛事件。
            }

            @Override
            public void onAsrPartial(@NonNull String text) {
                dispatchSubtitle(text, false);
            }

            @Override
            public void onAsrFinal(@NonNull String text) {
                dispatchSubtitle(text, true);
            }

            @Override
            public void onAsrError(@NonNull String message) {
                notifySessionError(message);
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
                stopPipelines();
                mainHandler.removeCallbacks(statsTicker);
                setState(RecordConnectionState.DISCONNECTED);
            }

            @Override
            public void onFailed(@NonNull String message) {
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
            }

            @Override
            public void onAudioBytes(@NonNull byte[] payload) {
                audioDecoder.enqueue(payload);
            }
        });
    }

    @Override
    public void connect(@NonNull String macAddress) {
        Objects.requireNonNull(macAddress, "macAddress");
        setState(RecordConnectionState.CONNECTING);
        link.connect(macAddress);
    }

    @Override
    public void disconnect() {
        mainHandler.removeCallbacks(statsTicker);
        stopPipelines();
        link.disconnect();
        setState(RecordConnectionState.DISCONNECTED);
    }

    @Override
    public void close() {
        mainHandler.removeCallbacks(statsTicker);
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
        synchronized (pipelineLock) {
            this.videoCodec = Objects.requireNonNull(codec, "codec");
            if (state == RecordConnectionState.READY) {
                restartVideoLocked();
            }
        }
    }

    @Override
    public void setAudioConfig(@NonNull RecordAudioCodec codec, int sampleRateHz, int channelCount) {
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
    public void setAsrEnabled(boolean enabled) {
        synchronized (pipelineLock) {
            this.asrEnabled = enabled;
            if (state == RecordConnectionState.READY) {
                if (enabled) {
                    restartAsrLocked();
                } else {
                    speechRecognizer.stop();
                }
            }
        }
    }

    @Override
    public void setAsrModel(@NonNull RecordAsrModel model) {
        synchronized (pipelineLock) {
            this.asrModel = Objects.requireNonNull(model, "model");
            if (state == RecordConnectionState.READY && asrEnabled) {
                restartAsrLocked();
            }
        }
    }

    @Override
    public void sendText(@NonNull String text) {
        link.writeRxText(Objects.requireNonNull(text, "text"));
    }

    private void startPipelinesLocked() {
        synchronized (pipelineLock) {
            restartAudioLocked();
            restartVideoLocked();
            if (asrEnabled) {
                restartAsrLocked();
            } else {
                speechRecognizer.stop();
            }
        }
    }

    private void stopPipelines() {
        synchronized (pipelineLock) {
            videoDecoder.stop();
            audioDecoder.stop();
            speechRecognizer.stop();
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

    private void restartAsrLocked() {
        speechRecognizer.stop();
        speechRecognizer.start(asrModel);
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

    private void dispatchSubtitle(@NonNull String text, boolean finalResult) {
        RecordSessionObserver session = sessionObserver;
        if (session == null) {
            return;
        }
        RecordSubtitleEvent event = new RecordSubtitleEvent(text, finalResult);
        mainHandler.post(() -> session.onSubtitle(event));
    }
}

package com.yovo.biz_ggec_wy.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.StreamRecordConfig;
import com.yovo.biz_ggec_wy.api.StreamRecordListener;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyRecordApi;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioCodecProviderPort;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioDecoder;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioFileWriter;
import com.yovo.biz_ggec_wy.internal.record.assembler.WyAudioFrameAssembler;
import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WY 随录随传会话管理（Audio SPP 组包 -> Opus 解码 -> PCM 回调 / 落盘）。
 */
public final class WyStreamRecordSession implements WyRecordApi {

    private static final String TAG = "WyStreamRecordSession";

    public interface MainDispatcher {
        void post(@NonNull Runnable r);
    }

    private final WyAudioCodecProviderPort codecProvider;
    private final WyRecordPathResolver pathResolver;
    private final MainDispatcher mainDispatcher;
    private final ExecutorService decodeExecutor;

    private final WyAudioFrameAssembler frameAssembler = new WyAudioFrameAssembler();
    @Nullable
    private WyAudioDecoder decoder;
    @Nullable
    private WyAudioFileWriter fileWriter;
    @Nullable
    private File currentTargetFile;

    private volatile boolean recording;
    @Nullable
    private StreamRecordListener currentListener;
    @Nullable
    private StreamRecordConfig currentConfig;
    private long totalPcmFrames;
    private long startTimeMs;

    public WyStreamRecordSession(@NonNull WyAudioCodecProviderPort codecProvider,
                                 @NonNull WyRecordPathResolver pathResolver) {
        this(codecProvider, pathResolver, createDefaultDispatcher(), Executors.newSingleThreadExecutor());
    }

    public WyStreamRecordSession(@NonNull WyAudioCodecProviderPort codecProvider,
                                 @NonNull WyRecordPathResolver pathResolver,
                                 @NonNull MainDispatcher mainDispatcher,
                                 @NonNull ExecutorService decodeExecutor) {
        this.codecProvider = Objects.requireNonNull(codecProvider, "codecProvider");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.mainDispatcher = Objects.requireNonNull(mainDispatcher, "mainDispatcher");
        this.decodeExecutor = Objects.requireNonNull(decodeExecutor, "decodeExecutor");
    }

    private static MainDispatcher createDefaultDispatcher() {
        if (Looper.getMainLooper() != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            return handler::post;
        }
        return Runnable::run;
    }

    @Override
    public synchronized boolean isStreamRecording() {
        return recording;
    }

    @Override
    public synchronized void startStreamRecord(@NonNull StreamRecordConfig config,
                                              @NonNull StreamRecordListener listener) {
        if (recording) {
            listener.onError("录音已在进行中");
            return;
        }

        this.currentConfig = Objects.requireNonNull(config, "config");
        this.currentListener = Objects.requireNonNull(listener, "listener");
        this.recording = true;
        this.totalPcmFrames = 0;
        this.startTimeMs = System.currentTimeMillis();

        frameAssembler.reset();
        frameAssembler.setAllowIndexResync(false);
        if (decoder != null) {
            decoder.release();
        }
        decoder = codecProvider.createDecoder();

        if (config.outputMode() != StreamRecordConfig.RecordOutputMode.STREAM_ONLY) {
            currentTargetFile = resolveTargetFile(config);
            fileWriter = codecProvider.createPcmFileWriter(currentTargetFile);
        } else {
            currentTargetFile = null;
            fileWriter = null;
        }

        WyLog.i(TAG, "启动随录随传，输出模式=" + config.outputMode());
        mainDispatcher.post(listener::onStarted);
    }

    @Override
    public synchronized void stopStreamRecord(@NonNull WyControl.Callback callback) {
        if (!recording) {
            callback.onResult(new WyControl.Outcome.Ok<>(null));
            return;
        }
        stopInternal(callback, null);
    }

    public void onAudioChunkReceived(@NonNull byte[] chunk) {
        if (!recording || chunk == null || chunk.length == 0) {
            return;
        }
        decodeExecutor.execute(() -> {
            List<byte[]> opusFrames = frameAssembler.assemble(chunk);
            if (opusFrames.isEmpty()) {
                return;
            }
            WyAudioDecoder currentDecoder = decoder;
            if (currentDecoder == null) {
                return;
            }
            for (byte[] opus : opusFrames) {
                byte[] pcm = currentDecoder.decode(opus);
                if (pcm == null || pcm.length == 0) {
                    continue;
                }
                totalPcmFrames++;
                float amplitude = calculateAmplitude(pcm);
                if (totalPcmFrames % 50 == 0) {
                    WyLog.d(TAG, "解码 PCM 帧=" + totalPcmFrames + " 时长=" + (totalPcmFrames * 20) + "ms 振幅=" + amplitude);
                }

                if (currentConfig != null
                        && currentConfig.outputMode() != StreamRecordConfig.RecordOutputMode.FILE_ONLY) {
                    StreamRecordListener listener = currentListener;
                    if (listener != null) {
                        mainDispatcher.post(() -> listener.onPcmFrame(pcm, amplitude));
                    }
                }

                if (fileWriter != null) {
                    fileWriter.writePcm(pcm);
                }
            }
        });
    }

    private synchronized void stopInternal(@Nullable WyControl.Callback callback, @Nullable String error) {
        if (!recording) {
            if (callback != null) {
                callback.onResult(new WyControl.Outcome.Ok<>(null));
            }
            return;
        }

        recording = false;
        StreamRecordListener listener = currentListener;
        File savedFile = currentTargetFile;
        long calcDuration = totalPcmFrames * 20L;
        if (calcDuration <= 0) {
            calcDuration = Math.max(0, System.currentTimeMillis() - startTimeMs);
        }
        final long finalDuration = calcDuration;
        WyLog.i(TAG, "停止随录随传，总 PCM 帧数=" + totalPcmFrames + " 测算时长=" + finalDuration + "ms 文件="
                + (savedFile != null ? savedFile.getAbsolutePath() : "null"));

        decodeExecutor.execute(() -> {
            if (fileWriter != null) {
                fileWriter.close();
                fileWriter = null;
            }
            if (decoder != null) {
                decoder.release();
                decoder = null;
            }
            frameAssembler.reset();

            mainDispatcher.post(() -> {
                if (error != null && listener != null) {
                    listener.onError(error);
                }
                if (savedFile != null && savedFile.exists() && savedFile.length() > 0 && listener != null) {
                    listener.onFileSaved(savedFile, finalDuration, savedFile.length());
                }
                if (listener != null) {
                    listener.onStopped();
                }
                if (callback != null) {
                    callback.onResult(error == null
                            ? new WyControl.Outcome.Ok<>(null)
                            : new WyControl.Outcome.Err<>(WyControl.Failure.TIMEOUT));
                }
            });
        });
    }

    private File resolveTargetFile(@NonNull StreamRecordConfig config) {
        if (config.customFileName() != null && !config.customFileName().isEmpty()) {
            File parent = config.customSaveDir() != null ? config.customSaveDir() : pathResolver.getStreamDir();
            String name = config.customFileName();
            if (!name.contains(".")) {
                name = name + ".pcm";
            }
            return new File(parent, name);
        }
        return pathResolver.generateDefaultStreamFile();
    }

    private float calculateAmplitude(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0f;
        }
        long sum = 0;
        int count = pcmData.length / 2;
        for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sum += Math.abs(sample);
        }
        float avg = (float) sum / count;
        return Math.min(1.0f, avg / 32767f);
    }

    public void release() {
        stopInternal(null, null);
        decodeExecutor.shutdown();
    }
}

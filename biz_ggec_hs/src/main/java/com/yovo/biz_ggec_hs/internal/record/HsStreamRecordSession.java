package com.yovo.biz_ggec_hs.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.StreamRecordConfig;
import com.yovo.biz_ggec_hs.api.StreamRecordListener;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioCodecProviderPort;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDecoder;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioFileWriter;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StartStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StopStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.record.assembler.HsAudioFrameAssembler;
import com.yovo.biz_ggec_hs.internal.record.assembler.HsAudioPacketClassifier;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 随录随传会话管理（Notify 组包 → Opus 解码 → PCM 流回调 / 落盘）。
 */
public final class HsStreamRecordSession {

    private static final String TAG = "HsStreamRecordSession";

    private final HsProtocolEngine engine;
    private final HsAudioDataLink audioLink;
    private final HsRecordPathResolver pathResolver;
    private final HsAudioCodecProviderPort codecProvider;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();

    private final HsAudioFrameAssembler frameAssembler = new HsAudioFrameAssembler();
    private HsAudioDecoder decoder;
    private HsAudioFileWriter fileWriter;
    private File currentTargetFile;

    private volatile boolean recording;
    private StreamRecordListener currentListener;
    private StreamRecordConfig currentConfig;
    private long totalPcmFrames;
    private long startTimeMs;

    public HsStreamRecordSession(@NonNull HsProtocolEngine engine,
                                 @NonNull HsAudioDataLink audioLink,
                                 @NonNull HsRecordPathResolver pathResolver,
                                 @NonNull HsAudioCodecProviderPort codecProvider) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.audioLink = Objects.requireNonNull(audioLink, "audioLink");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.codecProvider = Objects.requireNonNull(codecProvider, "codecProvider");
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    public synchronized void startRecord(@NonNull StreamRecordConfig config,
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

        // 1. 初始化拆包器与解码器
        frameAssembler.reset();
        frameAssembler.setAllowIndexResync(false);
        if (decoder != null) {
            decoder.release();
        }
        decoder = codecProvider.createDecoder();

        // 2. 配置文件落地
        if (config.outputMode() != StreamRecordConfig.RecordOutputMode.STREAM_ONLY) {
            currentTargetFile = resolveTargetFile(config);
            fileWriter = codecProvider.createPcmFileWriter(currentTargetFile);
        } else {
            currentTargetFile = null;
            fileWriter = null;
        }

        // 3. 严格等待 820003 数据通道 Notify 就绪后再发开启命令
        audioLink.startAudioNotify(new HsAudioDataLink.ReadyCallback() {
            @Override
            public void onReady() {
                HsLog.i(TAG, "音频 Notify 通道就绪，发送 StartStreamRecordCommand");
                engine.submit(new StartStreamRecordCommand(), (command, completion) -> {
                    if (completion.getCode() == Command.ResultCode.SUCCESS) {
                        HsLog.i(TAG, "随录随传协议启动成功");
                        mainHandler.post(listener::onStarted);
                    } else {
                        HsLog.e(TAG, "随录随传协议启动失败: " + completion.getMessage());
                        stopInternal(null, completion.getMessage());
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                HsLog.e(TAG, "音频通道订阅失败: " + message);
                stopInternal(null, "音频通道订阅失败: " + message);
            }
        }, this::onAudioChunkReceived);
    }

    public synchronized void stopRecord(@NonNull HsControl.Callback callback) {
        if (!recording) {
            callback.onResult(new HsControl.Outcome.Ok<>(null));
            return;
        }
        engine.submit(new StopStreamRecordCommand(), (cmd, completion) -> {
            boolean success = completion.getCode() == Command.ResultCode.SUCCESS;
            stopInternal(callback, success ? null : completion.getMessage());
        });
    }

    private void onAudioChunkReceived(byte[] chunk) {
        if (!recording || chunk == null || chunk.length == 0) {
            return;
        }

        // 820003 控制上报包轻量分流：控制包不进入音频拆包管线
        if (HsAudioPacketClassifier.isControlPacket(chunk)) {
            handleControlPacket(chunk);
            return;
        }

        decodeExecutor.execute(() -> {
            List<byte[]> opusFrames = frameAssembler.assemble(chunk);
            if (opusFrames.isEmpty() || decoder == null) {
                return;
            }
            for (byte[] opus : opusFrames) {
                byte[] pcm = decoder.decode(opus);
                if (pcm == null || pcm.length == 0) {
                    continue;
                }
                totalPcmFrames++;
                float amplitude = calculateAmplitude(pcm);
                if (totalPcmFrames == 1 || totalPcmFrames % 50 == 0) {
                    HsLog.d(TAG, "解码 PCM 帧=" + totalPcmFrames + " 时长=" + (totalPcmFrames * 20) + "ms 振幅=" + amplitude);
                }

                // 流分发
                if (currentConfig != null
                        && currentConfig.outputMode() != StreamRecordConfig.RecordOutputMode.FILE_ONLY) {
                    StreamRecordListener listener = currentListener;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onPcmFrame(pcm, amplitude));
                    }
                }

                // 文件落盘
                if (fileWriter != null) {
                    fileWriter.writePcm(pcm);
                }
            }
        });
    }

    private void handleControlPacket(byte[] chunk) {
        int cmdType = HsAudioPacketClassifier.resolveControlCmdType(chunk);
        HsLog.i(TAG, "收到音频通道控制上报包: len=" + chunk.length + " cmdType=0x" + Integer.toHexString(cmdType));
        if (cmdType == HsAudioPacketClassifier.CMD_TYPE_STOP) {
            HsLog.i(TAG, "耳机远端主动按键停录，触发远端停止收尾");
            mainHandler.post(() -> stopInternal(null, null));
        }
    }

    private synchronized void stopInternal(@Nullable HsControl.Callback callback, @Nullable String error) {
        if (!recording) {
            if (callback != null) {
                callback.onResult(new HsControl.Outcome.Ok<>(null));
            }
            return;
        }

        recording = false;
        audioLink.stopAudioNotify();

        StreamRecordListener listener = currentListener;
        File savedFile = currentTargetFile;
        long calcDuration = totalPcmFrames * 20L;
        if (calcDuration <= 0) {
            calcDuration = Math.max(0, System.currentTimeMillis() - startTimeMs);
        }
        final long finalDuration = calcDuration;
        HsLog.i(TAG, "停止随录随传，总 PCM 帧数=" + totalPcmFrames + " 测算时长=" + finalDuration + "ms 文件="
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

            mainHandler.post(() -> {
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
                            ? new HsControl.Outcome.Ok<>(null)
                            : new HsControl.Outcome.Err<>(HsControl.Failure.TIMEOUT));
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

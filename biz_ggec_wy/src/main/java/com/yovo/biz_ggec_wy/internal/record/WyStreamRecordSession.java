package com.yovo.biz_ggec_wy.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.StreamRecordConfig;
import com.yovo.biz_ggec_wy.api.StreamRecordListener;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyRecordApi;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.LoggingAudioFrameTrace;
import com.yovo.lib_ggec_audio.record.StreamRecordPipeline;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WY 随录随传会话：复用 HS02 99 EC 协议命令表，负责音频 SPP 通道按需拉起与控制帧收发。
 */
public final class WyStreamRecordSession implements WyRecordApi {

    private static final byte[] CMD_START_STREAM_RECORD = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0xD5, 0x00, 0x01, 0x00, 0x12, 0x34
    };

    private static final byte[] CMD_STOP_STREAM_RECORD = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0xD6, 0x00, 0x01, 0x00, 0x12, 0x34
    };

    public interface MainDispatcher {
        void post(@NonNull Runnable r);

        void postDelayed(@NonNull Runnable r, long delayMillis);

        void removeCallbacks(@NonNull Runnable r);
    }

    private final WyClassicRadioPort radio;
    private final WyRecordPathResolver pathResolver;
    private final MainDispatcher mainDispatcher;
    private final StreamRecordPipeline<WyControl.Failure> pipeline;

    private final Object protocolLock = new Object();
    @Nullable
    private StreamRecordPipeline.Reply<WyControl.Failure> pendingStartReply;
    @Nullable
    private StreamRecordPipeline.Reply<WyControl.Failure> pendingStopReply;
    @Nullable
    private Runnable pendingTimeoutRunnable;

    public WyStreamRecordSession(@NonNull WyClassicRadioPort radio,
                                 @NonNull AudioCodecProviderPort codecProvider,
                                 @NonNull WyRecordPathResolver pathResolver) {
        this(radio, codecProvider, pathResolver, createDefaultDispatcher(),
                Executors.newSingleThreadExecutor());
    }

    public WyStreamRecordSession(@NonNull WyClassicRadioPort radio,
                                 @NonNull AudioCodecProviderPort codecProvider,
                                 @NonNull WyRecordPathResolver pathResolver,
                                 @NonNull MainDispatcher mainDispatcher,
                                 @NonNull ExecutorService decodeExecutor) {
        this.radio = Objects.requireNonNull(radio, "radio");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.mainDispatcher = Objects.requireNonNull(mainDispatcher, "mainDispatcher");
        this.pipeline = new StreamRecordPipeline<>(
                codecProvider,
                pathResolver::getStreamDir,
                pathResolver::generateDefaultStreamFile,
                mainDispatcher::post,
                WyRecordLog.INSTANCE,
                new LoggingAudioFrameTrace("WyAudioFrame", WyRecordLog.INSTANCE),
                decodeExecutor);
    }

    private static MainDispatcher createDefaultDispatcher() {
        if (Looper.getMainLooper() != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            return new MainDispatcher() {
                @Override
                public void post(@NonNull Runnable r) {
                    handler.post(r);
                }

                @Override
                public void postDelayed(@NonNull Runnable r, long delayMillis) {
                    handler.postDelayed(r, delayMillis);
                }

                @Override
                public void removeCallbacks(@NonNull Runnable r) {
                    handler.removeCallbacks(r);
                }
            };
        }
        return new MainDispatcher() {
            @Override
            public void post(@NonNull Runnable r) {
                r.run();
            }

            @Override
            public void postDelayed(@NonNull Runnable r, long delayMillis) {
            }

            @Override
            public void removeCallbacks(@NonNull Runnable r) {
            }
        };
    }

    @Override
    public boolean isStreamRecording() {
        return pipeline.isRecording();
    }

    @Override
    public void startStreamRecord(@NonNull StreamRecordConfig config,
                                  @NonNull StreamRecordListener listener) {
        pipeline.start(config, listener, new WyRecordCommands());
    }

    @Override
    public void stopStreamRecord(@NonNull WyControl.Callback callback) {
        pipeline.stop(failure -> callback.onResult(
                failure == null
                        ? new WyControl.Outcome.Ok<>(null)
                        : new WyControl.Outcome.Err<>(failure)));
    }

    @NonNull
    @Override
    public List<File> listLocalSavedFiles() {
        return pathResolver.listLocalSavedFiles();
    }

    @Override
    public boolean deleteLocalSavedFile(@NonNull String filePath) {
        Objects.requireNonNull(filePath, "filePath");
        return pathResolver.deleteLocalSavedFile(filePath);
    }

    /** 控制数据入口：Control SPP 接收到的协议帧。 */
    public void onControlDataReceived(@NonNull byte[] data) {
        if (data == null || data.length < 8) {
            return;
        }
        for (int i = 0; i <= data.length - 8; i++) {
            if ((data[i] & 0xFF) == 0x99 && (data[i + 1] & 0xFF) == 0xEC) {
                int cmd = data[i + 2] & 0xFF;
                int status = data[i + 5] & 0xFF;
                if (cmd == 0xD5) {
                    handleStartResponse(status);
                } else if (cmd == 0xD6) {
                    handleStopResponse(status);
                }
            }
        }
    }

    /** 音频数据入口：SPP 收到的原始音频字节。 */
    public void onAudioChunkReceived(@NonNull byte[] chunk) {
        if (!pipeline.isRecording()) {
            return;
        }
        // 若音频流通道夹带控制指令帧（< 44 字节），分流并处理远端停止
        if (chunk.length < 44) {
            if (chunk.length >= 8 && (chunk[0] & 0xFF) == 0x99 && (chunk[1] & 0xFF) == 0xEC) {
                int cmd = chunk[2] & 0xFF;
                if (cmd == 0xD6) {
                    WyRecordLog.INSTANCE.info("音频通道收到耳机端主动停止通知 (99 EC D6)");
                    radio.disconnectAudioSpp();
                    pipeline.stopByDevice();
                }
            }
            return;
        }
        pipeline.onAudioChunk(chunk);
    }

    private void handleStartResponse(int status) {
        StreamRecordPipeline.Reply<WyControl.Failure> reply;
        synchronized (protocolLock) {
            reply = pendingStartReply;
            pendingStartReply = null;
            cancelTimeoutLocked();
        }
        if (reply != null) {
            if (status == 0x00) {
                WyRecordLog.INSTANCE.info("随录随传开始成功 (0x00)");
                reply.onReply(null);
            } else {
                WyRecordLog.INSTANCE.warn("随录随传开始失败，status=0x" + Integer.toHexString(status));
                radio.disconnectAudioSpp();
                reply.onReply(WyControl.Failure.REJECTED);
            }
        }
    }

    private void handleStopResponse(int status) {
        StreamRecordPipeline.Reply<WyControl.Failure> reply;
        synchronized (protocolLock) {
            reply = pendingStopReply;
            pendingStopReply = null;
            cancelTimeoutLocked();
        }
        if (reply != null) {
            radio.disconnectAudioSpp();
            if (status == 0x00) {
                WyRecordLog.INSTANCE.info("随录随传停止成功 (0x00)");
                reply.onReply(null);
            } else {
                WyRecordLog.INSTANCE.warn("随录随传停止失败，status=0x" + Integer.toHexString(status));
                reply.onReply(WyControl.Failure.REJECTED);
            }
        } else if (pipeline.isRecording()) {
            WyRecordLog.INSTANCE.info("收到耳机端主动停止随录随传通知 (99 EC D6)");
            radio.disconnectAudioSpp();
            pipeline.stopByDevice();
        }
    }

    private void cancelTimeoutLocked() {
        if (pendingTimeoutRunnable != null) {
            mainDispatcher.removeCallbacks(pendingTimeoutRunnable);
            pendingTimeoutRunnable = null;
        }
    }

    public void release() {
        synchronized (protocolLock) {
            cancelTimeoutLocked();
            pendingStartReply = null;
            pendingStopReply = null;
        }
        pipeline.release();
    }

    private final class WyRecordCommands implements StreamRecordPipeline.Commands<WyControl.Failure> {

        @Override
        public void start(@NonNull StreamRecordPipeline.Reply<WyControl.Failure> reply) {
            WyRecordLog.INSTANCE.info("请求建立音频 SPP 通道");
            radio.connectAudioSpp((success, failure) -> {
                if (!success) {
                    WyRecordLog.INSTANCE.warn("音频 SPP 通道连接失败: " + failure);
                    reply.onReply(failure != null ? failure : WyControl.Failure.DEVICE_ERROR);
                    return;
                }
                WyRecordLog.INSTANCE.info("音频 SPP 通道已建立，发送随录随传开始命令 99ECD5");
                synchronized (protocolLock) {
                    pendingStartReply = reply;
                    cancelTimeoutLocked();
                    pendingTimeoutRunnable = () -> {
                        synchronized (protocolLock) {
                            if (pendingStartReply == reply) {
                                pendingStartReply = null;
                                WyRecordLog.INSTANCE.warn("随录随传开始命令超时");
                                radio.disconnectAudioSpp();
                                reply.onReply(WyControl.Failure.TIMEOUT);
                            }
                        }
                    };
                    mainDispatcher.postDelayed(pendingTimeoutRunnable, 5000L);
                }
                radio.sendControl(CMD_START_STREAM_RECORD, (sendSuccess, sendFailure) -> {
                    if (!sendSuccess) {
                        synchronized (protocolLock) {
                            cancelTimeoutLocked();
                            if (pendingStartReply == reply) {
                                pendingStartReply = null;
                                radio.disconnectAudioSpp();
                                reply.onReply(sendFailure != null ? sendFailure : WyControl.Failure.DEVICE_ERROR);
                            }
                        }
                    }
                });
            });
        }

        @Override
        public void stop(@NonNull StreamRecordPipeline.Reply<WyControl.Failure> reply) {
            WyRecordLog.INSTANCE.info("发送随录随传停止命令 99ECD6");
            synchronized (protocolLock) {
                pendingStopReply = reply;
                cancelTimeoutLocked();
                pendingTimeoutRunnable = () -> {
                    synchronized (protocolLock) {
                        if (pendingStopReply == reply) {
                            pendingStopReply = null;
                            WyRecordLog.INSTANCE.warn("随录随传停止命令超时");
                            radio.disconnectAudioSpp();
                            reply.onReply(WyControl.Failure.TIMEOUT);
                        }
                    }
                };
                mainDispatcher.postDelayed(pendingTimeoutRunnable, 5000L);
            }
            radio.sendControl(CMD_STOP_STREAM_RECORD, (sendSuccess, sendFailure) -> {
                if (!sendSuccess) {
                    synchronized (protocolLock) {
                        cancelTimeoutLocked();
                        if (pendingStopReply == reply) {
                            pendingStopReply = null;
                            radio.disconnectAudioSpp();
                            reply.onReply(sendFailure != null ? sendFailure : WyControl.Failure.DEVICE_ERROR);
                        }
                    }
                }
            });
        }

        @Override
        public void detach() {
            WyRecordLog.INSTANCE.info("随录随传收尾，断开音频 SPP 通道");
            synchronized (protocolLock) {
                cancelTimeoutLocked();
                pendingStartReply = null;
                pendingStopReply = null;
            }
            radio.disconnectAudioSpp();
        }
    }
}

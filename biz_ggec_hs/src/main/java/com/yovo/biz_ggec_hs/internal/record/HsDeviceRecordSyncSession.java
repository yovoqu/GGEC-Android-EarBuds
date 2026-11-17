package com.yovo.biz_ggec_hs.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.SyncProgressListener;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioCodecProviderPort;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDecoder;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioFileWriter;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.CancelTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.record.assembler.HsAudioFrameAssembler;
import com.yovo.biz_ggec_hs.internal.record.assembler.HsAudioPacketClassifier;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备录音文件同步传输会话管理（Notify 组包 → Opus 解码 → PCM 落盘）。
 */
public final class HsDeviceRecordSyncSession {

    private static final String TAG = "HsDeviceRecordSyncSession";

    private final HsProtocolEngine engine;
    private final HsAudioDataLink audioLink;
    private final HsRecordPathResolver pathResolver;
    private final HsAudioCodecProviderPort codecProvider;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();

    private final HsAudioFrameAssembler frameAssembler = new HsAudioFrameAssembler();
    private HsAudioDecoder decoder;
    private HsAudioFileWriter fileWriter;
    private File currentTargetFile;
    private long totalExpectedBytes;
    private long receivedBytes;

    private volatile boolean syncing;
    private SyncProgressListener currentListener;

    public HsDeviceRecordSyncSession(@NonNull HsProtocolEngine engine,
                                     @NonNull HsAudioDataLink audioLink,
                                     @NonNull HsRecordPathResolver pathResolver,
                                     @NonNull HsAudioCodecProviderPort codecProvider) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.audioLink = Objects.requireNonNull(audioLink, "audioLink");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.codecProvider = Objects.requireNonNull(codecProvider, "codecProvider");
    }

    public synchronized boolean isSyncing() {
        return syncing;
    }

    public synchronized void startSync(@NonNull String fileId,
                                       long expectedTotalBytes,
                                       @Nullable File targetFile,
                                       @NonNull SyncProgressListener listener) {
        if (syncing) {
            listener.onError("文件同步已在进行中");
            return;
        }

        this.syncing = true;
        this.currentListener = Objects.requireNonNull(listener, "listener");
        this.totalExpectedBytes = expectedTotalBytes;
        this.receivedBytes = 0;
        this.currentTargetFile = targetFile != null ? targetFile : pathResolver.generateDefaultTransferFile(fileId);

        frameAssembler.reset();
        frameAssembler.setAllowIndexResync(true);
        if (decoder != null) {
            decoder.release();
        }
        decoder = codecProvider.createDecoder();
        fileWriter = codecProvider.createPcmFileWriter(currentTargetFile);

        int index = 0;
        try {
            index = Integer.parseInt(fileId);
        } catch (NumberFormatException ignored) {
        }

        final int targetIndex = index;

        // 严格等待 820003 数据通道 Notify 就绪后再发命令
        audioLink.startAudioNotify(new HsAudioDataLink.ReadyCallback() {
            @Override
            public void onReady() {
                HsLog.i(TAG, "音频传输 Notify 通道就绪，发送 StartTransmitRecordCommand: " + targetIndex);
                engine.submit(new StartTransmitRecordCommand(targetIndex), (cmd, completion) -> {
                    if (completion.getCode() != Command.ResultCode.SUCCESS) {
                        HsLog.e(TAG, "启动文件传输失败: " + completion.getMessage());
                        cancelInternal("设备拒绝传输: " + completion.getMessage());
                    } else {
                        HsLog.i(TAG, "设备接受传输指令，开始接收数据");
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                HsLog.e(TAG, "传输通道订阅失败: " + message);
                cancelInternal("传输通道订阅失败: " + message);
            }
        }, this::onSyncDataReceived);
    }

    public synchronized void cancelSync(@NonNull HsControl.Callback callback) {
        if (!syncing) {
            callback.onResult(new HsControl.Outcome.Ok<>(null));
            return;
        }

        engine.submit(new CancelTransmitRecordCommand(), (cmd, completion) -> {
            boolean success = completion.getCode() == Command.ResultCode.SUCCESS;
            cancelInternal("传输已取消");
            callback.onResult(success
                    ? new HsControl.Outcome.Ok<>(null)
                    : new HsControl.Outcome.Err<>(HsControl.Failure.TIMEOUT));
        });
    }

    private void onSyncDataReceived(byte[] chunk) {
        if (!syncing || chunk == null || chunk.length == 0) {
            return;
        }
        if (HsAudioPacketClassifier.isControlPacket(chunk)) {
            HsLog.i(TAG, "文件同步通道收到控制包: len=" + chunk.length);
            return;
        }
        syncExecutor.execute(() -> {
            receivedBytes += chunk.length;
            int percentage = totalExpectedBytes > 0
                    ? (int) Math.min(100, (receivedBytes * 100) / totalExpectedBytes)
                    : 0;

            SyncProgressListener listener = currentListener;
            if (listener != null) {
                mainHandler.post(() -> listener.onProgress(percentage, receivedBytes, totalExpectedBytes));
            }

            List<byte[]> opusFrames = frameAssembler.assemble(chunk);
            if (!opusFrames.isEmpty() && decoder != null) {
                for (byte[] opus : opusFrames) {
                    byte[] pcm = decoder.decode(opus);
                    if (pcm != null && pcm.length > 0 && fileWriter != null) {
                        fileWriter.writePcm(pcm);
                    }
                }
            }

            if (totalExpectedBytes > 0 && receivedBytes >= totalExpectedBytes) {
                finishSync();
            }
        });
    }

    private synchronized void finishSync() {
        if (!syncing) {
            return;
        }
        syncing = false;
        audioLink.stopAudioNotify();

        SyncProgressListener listener = currentListener;
        File savedFile = currentTargetFile;

        syncExecutor.execute(() -> {
            if (fileWriter != null) {
                fileWriter.close();
                fileWriter = null;
            }
            if (decoder != null) {
                decoder.release();
                decoder = null;
            }

            mainHandler.post(() -> {
                if (savedFile != null && savedFile.exists() && listener != null) {
                    listener.onSuccess(savedFile);
                } else if (listener != null) {
                    listener.onError("文件同步落地失败");
                }
            });
        });
    }

    private synchronized void cancelInternal(@Nullable String errorMsg) {
        if (!syncing) {
            return;
        }
        syncing = false;
        audioLink.stopAudioNotify();

        SyncProgressListener listener = currentListener;
        syncExecutor.execute(() -> {
            if (fileWriter != null) {
                fileWriter.close();
                fileWriter = null;
            }
            if (decoder != null) {
                decoder.release();
                decoder = null;
            }

            mainHandler.post(() -> {
                if (listener != null) {
                    if (errorMsg != null) {
                        listener.onError(errorMsg);
                    } else {
                        listener.onCanceled();
                    }
                }
            });
        });
    }

    public void release() {
        cancelInternal(null);
        syncExecutor.shutdown();
    }
}

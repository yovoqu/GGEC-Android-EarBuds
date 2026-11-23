package com.yovo.biz_ggec_hs.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.SyncProgressListener;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.command.HsCommandFactory;
import com.yovo.biz_ggec_hs.internal.link.HsFailureMapper;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.lib_ggec_audio.AudioFrameAssembler;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.AudioDecoder;
import com.yovo.lib_ggec_audio.record.AudioFileWriter;
import com.yovo.lib_ggec_audio.record.LoggingAudioFrameTrace;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 设备录音文件同步传输会话管理（Notify 组包 → Opus 解码 → PCM 落盘）。
 */
public final class HsDeviceRecordSyncSession {

    private static final String TAG = "HsDeviceRecordSyncSession";

    /** 设备文件 ID 违约：既不上报进度，也不下发任何下标。 */
    private static final String ERROR_INVALID_FILE_ID = "设备录音文件 ID 非法";

    private final HsProtocolEngine engine;
    private final HsAudioDataLink audioLink;
    private final HsRecordPathResolver pathResolver;
    private final AudioCodecProviderPort codecProvider;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();

    private final AudioFrameAssembler frameAssembler =
            new AudioFrameAssembler(new LoggingAudioFrameTrace("HsAudioFrame", HsRecordLog.INSTANCE));
    private AudioDecoder decoder;
    private AudioFileWriter fileWriter;
    private File currentTargetFile;
    private long totalExpectedBytes;
    private long receivedBytes;

    private volatile boolean syncing;
    private SyncProgressListener currentListener;

    /**
     * 本次同步是否已收到过「非控制」音频切片。
     *
     * <p>设备（如 HS02）可能不回 {@code StartTransmitRecordCommand} 的 ack，但音频通道已经在出数据；
     * 只要收到过首段非控制切片，就可判定设备已接受传输，命令失败不应中止接收。</p>
     */
    private final AtomicBoolean transmitDataSeen = new AtomicBoolean(false);

    public HsDeviceRecordSyncSession(@NonNull HsProtocolEngine engine,
                                     @NonNull HsAudioDataLink audioLink,
                                     @NonNull HsRecordPathResolver pathResolver,
                                     @NonNull AudioCodecProviderPort codecProvider) {
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

        // 契约违约：fileId 不是设备侧十进制下标时不下发任何命令，也绝不回落成 0 号文件。
        Integer index = HsRecordFileId.indexOf(fileId);
        if (index == null) {
            HsLog.e(TAG, "拒绝非法设备文件 ID: " + fileId);
            listener.onError(ERROR_INVALID_FILE_ID);
            return;
        }

        this.syncing = true;
        this.currentListener = Objects.requireNonNull(listener, "listener");
        this.totalExpectedBytes = expectedTotalBytes;
        this.receivedBytes = 0;
        this.transmitDataSeen.set(false);
        this.currentTargetFile = targetFile != null
                ? targetFile
                : pathResolver.generateDefaultTransferFile(fileId);

        frameAssembler.reset();
        frameAssembler.setAllowIndexResync(true);
        if (decoder != null) {
            decoder.release();
        }
        decoder = codecProvider.createDecoder();
        fileWriter = codecProvider.createPcmFileWriter(currentTargetFile);

        int targetIndex = index;

        // 严格等待 820003 数据通道 Notify 就绪后再发命令
        audioLink.startAudioNotify(new HsAudioDataLink.ReadyCallback() {
            @Override
            public void onReady() {
                HsLog.i(TAG, "音频传输 Notify 通道就绪，发送 StartTransmitRecordCommand: " + targetIndex);
                engine.submit(HsCommandFactory.startTransmitRecord(targetIndex), (cmd, completion) -> {
                    if (completion.getCode() == Command.ResultCode.SUCCESS) {
                        HsLog.i(TAG, "设备接受传输指令，开始接收数据");
                    } else if (transmitDataSeen.get()) {
                        // 设备未回 ack，但音频通道已在出非控制数据：视为设备已接受，继续接收。
                        HsLog.w(TAG, "启动传输命令未回 ack(code=" + completion.getCode()
                                + ")，但音频通道已在出数据，按设备已接受继续接收");
                    } else {
                        HsLog.e(TAG, "启动文件传输失败: code=" + completion.getCode());
                        cancelInternal("设备拒绝传输");
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                HsLog.e(TAG, "传输通道订阅失败: " + message);
                cancelInternal("传输通道订阅失败");
            }
        }, this::onSyncDataReceived);
    }

    public synchronized void cancelSync(@NonNull HsControl.Callback callback) {
        if (!syncing) {
            callback.onResult(new HsControl.Outcome.Ok<>(null));
            return;
        }

        engine.submit(HsCommandFactory.cancelTransmitRecord(), (cmd, completion) -> {
            cancelInternal("传输已取消");
            callback.onResult(HsFailureMapper.writeResult(completion));
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
        // 收到首段非控制音频数据即视为设备已接受传输（可抵消命令通道 ack 缺失）。
        transmitDataSeen.set(true);
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

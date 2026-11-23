package com.yovo.biz_ggec_hs.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.SyncProgressListener;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.biz_ggec_hs.internal.center.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.command.HsCommandFactory;
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

/**
 * 设备录音文件同步传输会话管理（Notify 组包 → Opus 解码 → PCM 落盘）。
 *
 * <p>与随录随传 {@link HsStreamRecordSession} 一致，本类按<b>事件驱动</b>处理，而不是
 * 「命令请求-回执」事务：</p>
 * <ul>
 *   <li><b>开始/取消命令是单向发送</b>（{@link HsProtocolEngine#sendOneWay}）：不进串行命令队列、
 *       不等 StartTransmitRecord/CancelTransmitRecord 的命令回执，发送即完成（受理与否看链路）；</li>
 *   <li><b>成功/失败以数据进度为准</b>：820003 Notify 出音频数据即视为设备已接受传输，
 *       数据经 assemble→decode→写盘并累计 {@code receivedBytes}；累计达到 {@code expectedTotalBytes}
 *       即判定成功；</li>
 *   <li><b>失败来源只有三类</b>：总超时兜底（{@code SYNC_TOTAL_TIMEOUT_MS}）、Notify 订阅失败
 *       （或单向开始指令下发失败）、解码/写盘异常；均走统一收尾。</li>
 * </ul>
 *
 * <p><b>线程约束</b>：820003 数据回调在 BLE 线程；解码/写盘在 {@code syncExecutor} 单线程；
 * 对外 {@link SyncProgressListener} 回调一律经 {@code mainHandler.post}。收尾/退订（{@code stopAudioNotify}）
 * 统一投递主线程，避免在 BLE 回调栈内重入退订（与 {@link HsStreamRecordSession} 保持一致）。</p>
 */
public final class HsDeviceRecordSyncSession {

    private static final String TAG = "HsDeviceRecordSyncSession";

    /** 设备文件 ID 违约：既不上报进度，也不下发任何下标。 */
    private static final String ERROR_INVALID_FILE_ID = "设备录音文件 ID 非法";
    /** 一次文件同步的总体超时：设备迟迟不同步/不出流时兜底收尾，避免永久「进行中」。 */
    private static final long SYNC_TOTAL_TIMEOUT_MS = 120_000L;
    /**
     * 数据静默判定（设备未提供文件总大小时使用）：收到过数据后连续这么久无新数据，
     * 即认定设备已传完，收尾成功。设备文件大小当前调用方传 0，故这是主判定路径。
     */
    private static final long DATA_IDLE_MS = 1_500L;
    private final Runnable syncTimeout = this::onSyncTimeout;
    private Runnable dataIdleCheck;

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
    private volatile long totalExpectedBytes;
    private long receivedBytes;
    /** 最近一次收到非控制数据的时刻（毫秒），用于“数据静默即传完”判定。 */
    private volatile long lastDataAtMs;

    /** 同步进行中标志：置位后数据回调才开始累计/落盘；收尾时先行清位以挡住后续切片。 */
    private volatile boolean syncing;
    private SyncProgressListener currentListener;

    public HsDeviceRecordSyncSession(@NonNull HsProtocolEngine engine,
                                     @NonNull HsAudioDataLink audioLink,
                                     @NonNull HsRecordPathResolver pathResolver,
                                     @NonNull AudioCodecProviderPort codecProvider) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.audioLink = Objects.requireNonNull(audioLink, "audioLink");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.codecProvider = Objects.requireNonNull(codecProvider, "codecProvider");
        this.dataIdleCheck = () -> {
            boolean done = false;
            synchronized (HsDeviceRecordSyncSession.this) {
                if (syncing && totalExpectedBytes <= 0L && receivedBytes > 0L
                        && System.currentTimeMillis() - lastDataAtMs >= DATA_IDLE_MS) {
                    done = true;
                }
            }
            if (done) {
                HsLog.i(TAG, "文件同步数据静默，判定传输完成");
                finishSync();
            }
        };
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
        this.lastDataAtMs = 0L;
        this.currentTargetFile = targetFile != null
                ? targetFile
                : pathResolver.generateDefaultTransferFile(fileId);

        // 总体超时兜底：从启动同步开始计时，正常完成/取消/失败收尾时撤销。
        mainHandler.removeCallbacks(syncTimeout);
        mainHandler.postDelayed(syncTimeout, SYNC_TOTAL_TIMEOUT_MS);

        frameAssembler.reset();
        frameAssembler.setAllowIndexResync(true);

        // 释放上一轮可能残留的解码器，再按本次目标重建解码器与落盘写入器。
        AudioDecoder previousDecoder = decoder;
        decoder = null;
        if (previousDecoder != null) {
            try {
                previousDecoder.release();
            } catch (RuntimeException e) {
                HsLog.e(TAG, "释放上一轮解码器失败", e);
            }
        }
        try {
            decoder = codecProvider.createDecoder();
            fileWriter = codecProvider.createPcmFileWriter(currentTargetFile);
        } catch (RuntimeException e) {
            HsLog.e(TAG, "创建解码器/落盘写入器失败", e);
            settleAsync(false, "文件同步初始化失败");
            return;
        }

        int targetIndex = index;

        // 先订阅 820003 数据通道，Notify 就绪后再单向发送开始指令；完成不再依赖命令回执。
        audioLink.startAudioNotify(new HsAudioDataLink.ReadyCallback() {
            @Override
            public void onReady() {
                if (!syncing) {
                    return;
                }
                boolean accepted = engine.sendOneWay(
                        HsCommandFactory.startTransmitRecord(targetIndex));
                if (accepted) {
                    HsLog.i(TAG, "Notify 就绪，已单向发送 StartTransmitRecordCommand: " + targetIndex);
                } else {
                    HsLog.e(TAG, "启动传输指令单向下发失败（链路未就绪）: " + targetIndex);
                    settleAsync(false, "启动传输指令下发失败");
                }
            }

            @Override
            public void onError(@NonNull String message) {
                HsLog.e(TAG, "传输通道订阅失败: " + message);
                settleAsync(false, "传输通道订阅失败");
            }
        }, this::onSyncDataReceived);
    }

    public synchronized void cancelSync(@NonNull HsControl.Callback callback) {
        if (!syncing) {
            callback.onResult(new HsControl.Outcome.Ok<>(null));
            return;
        }

        // 单向发送取消指令（不进命令队列、不等回执）；链路未就绪时仍按本地取消收尾。
        boolean sent = engine.sendOneWay(HsCommandFactory.cancelTransmitRecord());
        if (!sent) {
            HsLog.w(TAG, "取消传输指令单向下发失败（链路未就绪），仍按本地取消收尾");
        }
        // errorMsg 传 null：收尾对监听者回调 onCanceled。
        settleAsync(false, null);
        callback.onResult(new HsControl.Outcome.Ok<>(null));
    }

    /**
     * 820003 数据回调（BLE 线程）：控制包在此丢弃，音频切片投递到单线程执行器做解码/落盘。
     */
    private void onSyncDataReceived(byte[] chunk) {
        if (!syncing || chunk == null || chunk.length == 0) {
            return;
        }
        if (HsAudioPacketClassifier.isControlPacket(chunk)) {
            HsLog.i(TAG, "文件同步通道收到控制包: len=" + chunk.length);
            return;
        }
        // 记录最后数据时刻；设备未提供文件总大小时，用“数据静默”作为传完判定（每次数据到来重置计时）。
        lastDataAtMs = System.currentTimeMillis();
        if (totalExpectedBytes <= 0L) {
            mainHandler.removeCallbacks(dataIdleCheck);
            mainHandler.postDelayed(dataIdleCheck, DATA_IDLE_MS);
        }
        syncExecutor.execute(() -> handleDataChunk(chunk));
    }

    /**
     * 解码/落盘执行体（syncExecutor 单线程）：累计字节、上报进度、组包解码写盘，达到预期字节即成功。
     */
    private void handleDataChunk(byte[] chunk) {
        if (!syncing) {
            return;
        }
        try {
            long received;
            long total;
            SyncProgressListener listener;
            synchronized (this) {
                if (!syncing) {
                    return;
                }
                receivedBytes += chunk.length;
                received = receivedBytes;
                total = totalExpectedBytes;
                listener = currentListener;
            }

            int percentage = total > 0
                    ? (int) Math.min(100, (received * 100) / total)
                    : 0;
            if (listener != null) {
                mainHandler.post(() -> listener.onProgress(percentage, received, total));
            }

            List<byte[]> opusFrames = frameAssembler.assemble(chunk);
            if (!opusFrames.isEmpty()) {
                AudioDecoder activeDecoder;
                AudioFileWriter activeWriter;
                synchronized (this) {
                    activeDecoder = decoder;
                    activeWriter = fileWriter;
                }
                if (activeDecoder != null && activeWriter != null) {
                    for (byte[] opus : opusFrames) {
                        byte[] pcm = activeDecoder.decode(opus);
                        if (pcm != null && pcm.length > 0) {
                            activeWriter.writePcm(pcm);
                        }
                    }
                }
            }

            boolean reached;
            synchronized (this) {
                reached = syncing && totalExpectedBytes > 0 && receivedBytes >= totalExpectedBytes;
            }
            if (reached) {
                finishSync();
            }
        } catch (RuntimeException e) {
            // 解码或写盘异常：不静默吞掉，记录堆栈后按失败收尾。
            HsLog.e(TAG, "文件同步解码/写盘异常", e);
            settleAsync(false, "文件同步解码/写盘失败");
        }
    }

    /** 达到预期字节：判定同步成功。 */
    private void finishSync() {
        settleAsync(true, null);
    }

    /**
     * 统一收尾：清进行中标志、撤销超时、投递主线程退订与资源关闭，最后回调监听者。
     *
     * <p>退订/关闭一律不在 BLE 回调栈内同步执行：先 {@code mainHandler.post} 到主线程做
     * {@code stopAudioNotify}，再由 {@code syncExecutor} 关闭解码器/写入器，最终回到主线程回调，
     * 与 {@link HsStreamRecordSession} 的收尾线程模型一致。</p>
     *
     * @param success  true=成功；false 时若 {@code errorMsg} 为 null 表示「取消」，否则为「失败」
     * @param errorMsg 失败原因；成功或取消时为 null
     */
    private synchronized void settleAsync(boolean success, @Nullable String errorMsg) {
        if (!syncing) {
            return;
        }
        syncing = false;
        mainHandler.removeCallbacks(syncTimeout);
        mainHandler.removeCallbacks(dataIdleCheck);
        final SyncProgressListener listener = currentListener;
        currentListener = null;
        final File savedFile = currentTargetFile;
        // 捕获本轮的具体资源实例并即时置空字段：即使随后又开启新一轮同步，
        // 收尾任务关闭的也只是本轮实例，绝不会误关新一轮刚创建的解码器/写入器。
        final AudioFileWriter writerToClose = fileWriter;
        fileWriter = null;
        final AudioDecoder decoderToRelease = decoder;
        decoder = null;

        mainHandler.post(() -> {
            audioLink.stopAudioNotify();
            syncExecutor.execute(() -> {
                closeResources(writerToClose, decoderToRelease);
                mainHandler.post(() -> notifyListener(listener, success, savedFile, errorMsg));
            });
        });
    }

    /** 关闭指定的解码器与落盘写入器（任一环节异常记录后继续，不影响另一环节）。 */
    private void closeResources(@Nullable AudioFileWriter writer,
                                @Nullable AudioDecoder activeDecoder) {
        if (writer != null) {
            try {
                writer.close();
            } catch (RuntimeException e) {
                HsLog.e(TAG, "关闭落盘写入器失败", e);
            }
        }
        if (activeDecoder != null) {
            try {
                activeDecoder.release();
            } catch (RuntimeException e) {
                HsLog.e(TAG, "释放解码器失败", e);
            }
        }
    }

    /** 收尾结果对监听者回调（主线程）：成功校验落地文件，失败/取消按语义回调。 */
    private void notifyListener(@Nullable SyncProgressListener listener,
                                boolean success,
                                @Nullable File savedFile,
                                @Nullable String errorMsg) {
        if (listener == null) {
            return;
        }
        if (success) {
            if (savedFile != null && savedFile.exists()) {
                listener.onSuccess(savedFile);
            } else {
                listener.onError("文件同步落地失败");
            }
        } else if (errorMsg != null) {
            listener.onError(errorMsg);
        } else {
            listener.onCanceled();
        }
    }

    private void onSyncTimeout() {
        if (!syncing) {
            return;
        }
        HsLog.w(TAG, "录音文件同步总体超时（" + SYNC_TOTAL_TIMEOUT_MS + "ms），中止本次同步");
        settleAsync(false, "录音文件同步超时");
    }

    public void release() {
        boolean active;
        SyncProgressListener listener;
        AudioFileWriter writer;
        AudioDecoder activeDecoder;
        synchronized (this) {
            active = syncing;
            if (active) {
                syncing = false;
                mainHandler.removeCallbacks(syncTimeout);
                mainHandler.removeCallbacks(dataIdleCheck);
            }
            listener = currentListener;
            currentListener = null;
            writer = fileWriter;
            fileWriter = null;
            activeDecoder = decoder;
            decoder = null;
        }
        if (active) {
            // 释放路径同步退订（release 由上层主动调用，不在 BLE 回调栈内），
            // 资源关闭投递到单线程执行器，与在途数据处理串行，再 shutdown（已提交任务仍会执行）。
            audioLink.stopAudioNotify();
            syncExecutor.execute(() -> closeResources(writer, activeDecoder));
            if (listener != null) {
                mainHandler.post(listener::onCanceled);
            }
        }
        syncExecutor.shutdown();
    }
}

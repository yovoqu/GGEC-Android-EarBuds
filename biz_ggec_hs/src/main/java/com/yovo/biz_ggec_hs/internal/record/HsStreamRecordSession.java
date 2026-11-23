package com.yovo.biz_ggec_hs.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.StreamRecordConfig;
import com.yovo.biz_ggec_hs.api.StreamRecordListener;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.biz_ggec_hs.internal.center.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.command.HsCommandFactory;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsHex;
import com.yovo.biz_ggec_hs.internal.link.HsFailureMapper;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.LoggingAudioFrameTrace;
import com.yovo.lib_ggec_audio.record.StreamRecordPipeline;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HS 随录随传：**事件驱动的流状态机**（IDLE→STARTING→RECORDING→STOPPING→IDLE）。
 *
 * <p>与旧实现（把开始/停止塞进请求-回复事务 + 多源闸门 + 命令 replay + 停止兜底计时）不同，
 * 这里把随录随传当“流”处理：</p>
 * <ul>
 *   <li><b>开始/停止命令是单向发送</b>（{@link HsProtocolEngine#sendOneWay}）：不进串行命令队列、不等命令回执，
 *       因此不占用队列、也不需要把在途命令 replay 掉；</li>
 *   <li><b>完成由设备侧信号驱动</b>：STARTING 由“首段音频出流”或控制上报 {@code 0x8004} 进入 RECORDING；
 *       STOPPING 由控制上报 {@code 0x8005} 或“音频静默”进入 IDLE；均有单一有界超时兜底；</li>
 *   <li>设备停止回执 {@code 99ECD6} 实测不稳定（偶发 >10s 不回），因此停止**不以命令回执为准**，以音频停流为准。</li>
 * </ul>
 *
 * <p>组包/解码/振幅/落盘仍由 {@link StreamRecordPipeline} 唯一实现；本类只做流状态与设备信号判定。</p>
 */
public final class HsStreamRecordSession {

    private static final String TAG = "HsStreamRecordSession";

    /** 启动兜底：Notify 就绪并发送开始后，这么久内无音频/无 0x8004 则视为启动失败。 */
    private static final long START_TIMEOUT_MS = 2_000L;
    /** 停止兜底：发送停止后这么久仍未判定停流则按 best-effort 收尾。 */
    private static final long STOP_TIMEOUT_MS = 3_000L;
    /** 音频静默判定：停止后连续这么久没有非控制音频切片，即认定设备已停止出流。 */
    private static final long AUDIO_IDLE_MS = 800L;

    /** 音频流原始字节日志限流：约每 20ms 一片（≈50/s），逐片 hex 会刷屏；前 3 片 + 每约 1s 采样。 */
    private long audioChunksSeen;
    private long lastAudioLogMs;

    private final HsProtocolEngine engine;
    private final HsAudioDataLink audioLink;
    private final StreamRecordPipeline<HsControl.Failure> pipeline;
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    /** 主线程 Handler：启动/停止兜底计时与收尾派发（无主线程时为空，退化为直接执行）。 */
    private final Handler mainHandler;
    /** 当前流会话的命令信号接收者；820003 音频/控制回调经它推进状态机。 */
    private volatile HsRecordCommands active;

    public HsStreamRecordSession(@NonNull HsProtocolEngine engine,
                                 @NonNull HsAudioDataLink audioLink,
                                 @NonNull HsRecordPathResolver pathResolver,
                                 @NonNull AudioCodecProviderPort codecProvider) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.audioLink = Objects.requireNonNull(audioLink, "audioLink");
        Objects.requireNonNull(pathResolver, "pathResolver");
        this.mainHandler = Looper.getMainLooper() == null ? null : new Handler(Looper.getMainLooper());
        this.pipeline = new StreamRecordPipeline<>(
                codecProvider,
                pathResolver::getStreamDir,
                pathResolver::generateDefaultStreamFile,
                mainDispatcher(),
                HsRecordLog.INSTANCE,
                new LoggingAudioFrameTrace("HsAudioFrame", HsRecordLog.INSTANCE),
                decodeExecutor);
    }

    private static StreamRecordPipeline.Dispatcher mainDispatcher() {
        Looper main = Looper.getMainLooper();
        if (main == null) {
            return Runnable::run;
        }
        Handler handler = new Handler(main);
        return handler::post;
    }

    public boolean isRecording() {
        return pipeline.isRecording();
    }

    public void startRecord(@NonNull StreamRecordConfig config,
                            @NonNull StreamRecordListener listener) {
        pipeline.start(config, listener, new HsRecordCommands());
    }

    public void stopRecord(@NonNull HsControl.Callback callback) {
        pipeline.stop(failure -> callback.onResult(HsFailureMapper.writeOutcome(failure)));
    }

    public void release() {
        // 解码执行器由管线持有并统一关闭，这里不再重复 shutdown。
        pipeline.release();
    }

    /** 随录随传流状态机的命令侧：单向发送开始/停止，完成由设备信号与有界超时推进。 */
    private final class HsRecordCommands
            implements StreamRecordPipeline.Commands<HsControl.Failure> {

        private enum Phase { STARTING, RECORDING, STOPPING, IDLE }

        private volatile Phase phase = Phase.IDLE;
        private volatile StreamRecordPipeline.Reply<HsControl.Failure> startReply;
        private volatile StreamRecordPipeline.Reply<HsControl.Failure> stopReply;
        private volatile long lastAudioAtMs;
        private boolean startSettled;
        private boolean stopSettled;

        private final Runnable startTimeout = () -> {
            boolean timeout = false;
            synchronized (HsRecordCommands.this) {
                if (phase == Phase.STARTING && !startSettled) {
                    startSettled = true;
                    phase = Phase.IDLE;
                    timeout = true;
                }
            }
            if (timeout) {
                HsLog.w(TAG, "随录随传启动超时：无音频出流/无 0x8004");
                postReplyStart(HsControl.Failure.TIMEOUT);
            }
        };

        private Runnable stopIdleCheck;
        private final Runnable stopTimeout = () -> settleStop("停止兜底超时");

        HsRecordCommands() {
            this.stopIdleCheck = () -> {
                boolean reschedule = false;
                boolean idle = false;
                synchronized (HsRecordCommands.this) {
                    if (phase == Phase.STOPPING && !stopSettled) {
                        if (System.currentTimeMillis() - lastAudioAtMs >= AUDIO_IDLE_MS) {
                            idle = true;
                        } else {
                            reschedule = true;
                        }
                    }
                }
                if (idle) {
                    settleStop("音频停流");
                } else if (reschedule && mainHandler != null) {
                    mainHandler.postDelayed(stopIdleCheck, AUDIO_IDLE_MS);
                }
            };
        }

        @Override
        public void start(@NonNull StreamRecordPipeline.Reply<HsControl.Failure> reply) {
            synchronized (this) {
                startReply = reply;
                phase = Phase.STARTING;
                startSettled = false;
                stopSettled = false;
            }
            active = this;
            audioLink.startAudioNotify(new HsAudioDataLink.ReadyCallback() {
                @Override
                public void onReady() {
                    HsLog.i(TAG, "音频 Notify 通道就绪，单向发送 StartStreamRecordCommand");
                    engine.sendOneWay(HsCommandFactory.startStreamRecord());
                    if (mainHandler != null) {
                        mainHandler.postDelayed(startTimeout, START_TIMEOUT_MS);
                    }
                }

                @Override
                public void onError(@NonNull String message) {
                    HsLog.e(TAG, "音频通道订阅失败: " + message);
                    boolean fail = false;
                    synchronized (HsRecordCommands.this) {
                        if (phase == Phase.STARTING && !startSettled) {
                            startSettled = true;
                            phase = Phase.IDLE;
                            fail = true;
                        }
                    }
                    if (fail) {
                        postReplyStart(HsControl.Failure.DEVICE_ERROR);
                    }
                }
            }, HsStreamRecordSession.this::onAudioChunk);
        }

        @Override
        public void stop(@NonNull StreamRecordPipeline.Reply<HsControl.Failure> reply) {
            synchronized (this) {
                stopReply = reply;
                phase = Phase.STOPPING;
                lastAudioAtMs = System.currentTimeMillis();
            }
            HsLog.i(TAG, "单向发送 StopStreamRecordCommand");
            engine.sendOneWay(HsCommandFactory.stopStreamRecord());
            if (mainHandler != null) {
                mainHandler.postDelayed(stopTimeout, STOP_TIMEOUT_MS);
                mainHandler.postDelayed(stopIdleCheck, AUDIO_IDLE_MS);
            }
        }

        @Override
        public void detach() {
            if (mainHandler != null) {
                mainHandler.removeCallbacks(startTimeout);
                mainHandler.removeCallbacks(stopTimeout);
                mainHandler.removeCallbacks(stopIdleCheck);
            }
            audioLink.stopAudioNotify();
            synchronized (this) {
                phase = Phase.IDLE;
            }
            if (active == this) {
                active = null;
            }
        }

        /** 收到一段非控制音频切片（设备在出流）：推进 STARTING→RECORDING；任何时候刷新“最后音频时间”。 */
        void onAudioSignal() {
            lastAudioAtMs = System.currentTimeMillis();
            boolean started = false;
            synchronized (this) {
                if (phase == Phase.STARTING && !startSettled) {
                    startSettled = true;
                    phase = Phase.RECORDING;
                    started = true;
                }
            }
            if (started) {
                if (mainHandler != null) {
                    mainHandler.removeCallbacks(startTimeout);
                }
                HsLog.i(TAG, "随录随传启动成功（首段音频出流）");
                postReplyStart(null);
            }
        }

        /** 控制上报包（0x8004/0x8005/0x8012）。 */
        void onControlSignal(int cmdType) {
            if (HsAudioPacketClassifier.isStartSignal(cmdType)) {
                boolean started = false;
                synchronized (this) {
                    if (phase == Phase.STARTING && !startSettled) {
                        startSettled = true;
                        phase = Phase.RECORDING;
                        started = true;
                    }
                }
                if (started) {
                    if (mainHandler != null) {
                        mainHandler.removeCallbacks(startTimeout);
                    }
                    HsLog.i(TAG, "随录随传启动成功（控制上报 0x8004）");
                    postReplyStart(null);
                }
            } else if (HsAudioPacketClassifier.isStopSignal(cmdType)) {
                boolean remoteStop = false;
                synchronized (this) {
                    remoteStop = phase == Phase.RECORDING && !stopSettled;
                }
                if (remoteStop) {
                    HsLog.i(TAG, "耳机远端上报停止出流(0x8005)，触发远端停止收尾");
                    Runnable task = pipeline::stopByDevice;
                    if (mainHandler != null) {
                        mainHandler.post(task);
                    } else {
                        task.run();
                    }
                } else {
                    settleStop("控制上报 0x8005");
                }
            }
            // 0x8012（延时）/其它：外层已记录日志，这里无需处理。
        }

        private void postReplyStart(HsControl.Failure failure) {
            StreamRecordPipeline.Reply<HsControl.Failure> r = startReply;
            startReply = null;
            if (r != null) {
                r.onReply(failure);
            }
        }

        /** 单次结算停止：以 null 上报（best-effort，不把停止失败抛给 UI）；收尾派发到主线程。 */
        private void settleStop(String source) {
            StreamRecordPipeline.Reply<HsControl.Failure> r;
            synchronized (this) {
                if (stopSettled) {
                    return;
                }
                stopSettled = true;
                phase = Phase.IDLE;
                if (mainHandler != null) {
                    mainHandler.removeCallbacks(stopTimeout);
                    mainHandler.removeCallbacks(stopIdleCheck);
                }
                HsLog.i(TAG, "随录随传停止完成（" + source + "）");
                r = stopReply;
                stopReply = null;
            }
            if (r == null) {
                return;
            }
            if (mainHandler != null) {
                // 收尾（finish→detach→stopAudioNotify）放到主线程，避免在 BLE 回调栈内重入退订。
                mainHandler.post(() -> r.onReply(null));
            } else {
                r.onReply(null);
            }
        }
    }

    /**
     * 820003 通道入口：控制上报包在此分流（不进音频管线），音频切片进管线并推进流状态机。
     */
    private void onAudioChunk(@NonNull byte[] chunk) {
        boolean control = HsAudioPacketClassifier.isControlPacket(chunk);
        int cmdType = control ? HsAudioPacketClassifier.resolveControlCmdType(chunk) : 0;
        if (control) {
            HsLog.i(TAG, "BLE接收音频通道控制包: len=" + chunk.length
                    + " cmdType=0x" + Integer.toHexString(cmdType)
                    + " 语义=" + HsAudioPacketClassifier.describeControlCmdType(cmdType)
                    + " data=" + HsHex.preview(chunk, HsHex.PREVIEW_BYTES));
        } else {
            long now = System.currentTimeMillis();
            if (audioChunksSeen < 3L || now - lastAudioLogMs >= 1000L) {
                lastAudioLogMs = now;
                HsLog.d(TAG, "BLE接收音频数据: " + HsHex.preview(chunk, HsHex.PREVIEW_BYTES));
            }
            audioChunksSeen++;
        }

        if (!pipeline.isRecording()) {
            return;
        }
        HsRecordCommands commands = active;
        if (!control) {
            if (commands != null) {
                commands.onAudioSignal();
            }
            pipeline.onAudioChunk(chunk);
            return;
        }
        if (commands != null) {
            commands.onControlSignal(cmdType);
        }
    }
}

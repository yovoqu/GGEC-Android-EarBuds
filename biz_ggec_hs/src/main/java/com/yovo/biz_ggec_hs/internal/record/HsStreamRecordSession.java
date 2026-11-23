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
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsHex;
import com.yovo.biz_ggec_hs.internal.link.HsFailureMapper;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.LoggingAudioFrameTrace;
import com.yovo.lib_ggec_audio.record.StreamRecordPipeline;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HS 随录随传会话：产品命令表 + 820003 控制包分流 + 多源启动/停止结算。
 *
 * <p>组包、解码、振幅、分流、落盘与收尾序列的唯一实现在
 * {@link StreamRecordPipeline}；本类只负责「HS 用什么命令启动/停止」、「控制上报包怎么处理」
 * 以及「HS02 无命令回执时以设备侧动作为权威成功信号」的判定。</p>
 */
public final class HsStreamRecordSession {

    private static final String TAG = "HsStreamRecordSession";

    private final HsProtocolEngine engine;
    private final HsAudioDataLink audioLink;
    private final StreamRecordPipeline<HsControl.Failure> pipeline;
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();

    /**
     * 当前随录随传会话的命令对象（每会话一个 {@link HsRecordCommands}）。
     * 820003 的音频/控制回调通过它把「设备侧信号」喂给启动/停止闸门。
     */
    private volatile HsRecordCommands activeCommands;

    public HsStreamRecordSession(@NonNull HsProtocolEngine engine,
                                 @NonNull HsAudioDataLink audioLink,
                                 @NonNull HsRecordPathResolver pathResolver,
                                 @NonNull AudioCodecProviderPort codecProvider) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.audioLink = Objects.requireNonNull(audioLink, "audioLink");
        Objects.requireNonNull(pathResolver, "pathResolver");
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

    /**
     * HS 产品命令表。
     *
     * <p><b>开始</b>：先等 820003 Notify 就绪再下发 {@link Command}，成功判定改为「多源首个设备侧信号即成功」——
     * 命令回执 SUCCESS、控制上报 0x8004、或启动待确认窗口内的首段非控制音频切片，谁先到算谁，且只结算一次。
     * 成功后迟到的命令 FAILED/timeout 一律忽略；若命令仍在途则主动 replay 成功，避免串行队列被占满 ~10s。</p>
     *
     * <p><b>停止</b>：best-effort——命令终态（无论成功失败）、控制上报 0x8005 都算「停止已完成」，
     * 不再把「录音停止失败」上抛给 UI，失败原因只落日志。</p>
     */
    private final class HsRecordCommands
            implements StreamRecordPipeline.Commands<HsControl.Failure> {

        /** 开始成功多源结算闸门（只成功一次，成功后忽略迟到失败）。 */
        private final HsStreamStartGate startGate = new HsStreamStartGate();
        /** 停止只结算一次（命令终态或 0x8005 先到者生效）。 */
        private final AtomicBoolean stopSettled = new AtomicBoolean(false);

        private volatile StreamRecordPipeline.Reply<HsControl.Failure> startReply;
        private volatile StreamRecordPipeline.Reply<HsControl.Failure> stopReply;
        /** 在途的开始/停止命令对象；用于设备侧先行成功时主动结算。 */
        private volatile Command startCommand;
        private volatile Command stopCommand;

        @Override
        public void start(@NonNull StreamRecordPipeline.Reply<HsControl.Failure> reply) {
            startReply = reply;
            activeCommands = this;
            audioLink.startAudioNotify(new HsAudioDataLink.ReadyCallback() {
                @Override
                public void onReady() {
                    HsLog.i(TAG, "音频 Notify 通道就绪，发送 StartStreamRecordCommand");
                    Command command = HsCommandFactory.startStreamRecord();
                    startCommand = command;
                    engine.submit(command, (cmd, completion) -> onStartCommandCompletion(completion));
                    // 设备侧信号（音频/0x8004）可能早于命令下发就已判定成功：立即结算命令对象，
                    // 免得这条无回执的命令在串行队列里挂到超时，阻塞后续命令。
                    if (startGate.hasSucceeded()) {
                        replayStartCommandIfInFlight("设备侧已开始出流（命令下发时已判定成功）");
                    }
                }

                @Override
                public void onError(@NonNull String message) {
                    HsLog.e(TAG, "音频通道订阅失败: " + message);
                    // 仅在尚未有设备侧成功信号时才把订阅失败当作启动失败。
                    settleStartFailure(HsControl.Failure.DEVICE_ERROR);
                }
            }, HsStreamRecordSession.this::onAudioChunk);
        }

        @Override
        public void stop(@NonNull StreamRecordPipeline.Reply<HsControl.Failure> reply) {
            stopReply = reply;
            Command command = HsCommandFactory.stopStreamRecord();
            stopCommand = command;
            engine.submit(command, (cmd, completion) -> onStopCommandCompletion(completion));
        }

        @Override
        public void detach() {
            audioLink.stopAudioNotify();
            if (HsStreamRecordSession.this.activeCommands == this) {
                HsStreamRecordSession.this.activeCommands = null;
            }
        }

        /**
         * 820003 回调进来的「设备侧信号」统一入口：控制包按 cmdType 分派，非控制切片视为设备已在出流。
         */
        void onDeviceSignal(boolean control, int cmdType) {
            if (control) {
                if (HsAudioPacketClassifier.isStartSignal(cmdType)) {
                    onStartDeviceSuccess(HsStreamStartGate.Event.CTRL_START, "控制上报 0x8004");
                } else if (HsAudioPacketClassifier.isStopSignal(cmdType)) {
                    onStopDeviceSignal();
                } else if (HsAudioPacketClassifier.isDelayNotice(cmdType)) {
                    // 0x8012 只作信息记录：既不算成功也不算失败，继续等音频/0x8004/命令回执。
                    HsLog.i(TAG, "设备延时中（0x8012），继续等待音频数据或开始回执");
                }
            } else {
                // 启动待确认窗口内的首段非控制音频切片 = 设备已开始出流。
                onStartDeviceSuccess(HsStreamStartGate.Event.AUDIO_CHUNK, "首段非控制音频切片");
            }
        }

        /** 命令对象完成：SUCCESS 走成功闸门；失败/超时仅在尚无设备侧成功时收尾。 */
        private void onStartCommandCompletion(@NonNull CommandCompletion completion) {
            int code = completion.getCode();
            if (code == Command.ResultCode.SUCCESS) {
                if (startGate.onEvent(HsStreamStartGate.Event.CMD_SUCCESS) == HsStreamStartGate.Outcome.SUCCESS) {
                    HsLog.i(TAG, "随录随传启动成功（命令回执 99ECD5 SUCCESS）");
                    replyStart(null);
                }
            } else {
                if (startGate.isSettled()) {
                    // 已由设备侧先行成功：迟到的命令失败一律忽略，绝不把已 started 的会话以失败收尾。
                    HsLog.i(TAG, "设备侧已先行成功，忽略迟到的命令失败: code=" + code);
                    return;
                }
                settleStartFailure(HsFailureMapper.ofCommandCode(code));
            }
        }

        /** 设备侧成功信号（0x8004 / 首段音频）：闸门首次成功时结算 reply，并 replay 在途命令。 */
        private void onStartDeviceSuccess(HsStreamStartGate.Event event, String source) {
            if (startGate.onEvent(event) != HsStreamStartGate.Outcome.SUCCESS) {
                return;
            }
            HsLog.i(TAG, "随录随传启动成功（设备侧信号：" + source + "），结算启动");
            replyStart(null);
            replayStartCommandIfInFlight("设备侧已开始出流");
        }

        /** 停止：命令终态（无论成功失败）都算停止完成，失败原因只落日志。 */
        private void onStopCommandCompletion(@NonNull CommandCompletion completion) {
            if (completion.getCode() != Command.ResultCode.SUCCESS) {
                HsLog.w(TAG, "停止命令终态为失败（code=" + completion.getCode()
                        + "，msg=" + completion.getMessage() + "），按 best-effort 视为停止完成");
            }
            settleStop("命令终态");
        }

        /** 设备侧停止上报 0x8005：我方停止在途时结算停止，否则按设备主动按键停录处理。 */
        private void onStopDeviceSignal() {
            if (stopReply == null) {
                HsLog.i(TAG, "耳机远端主动按键停录，触发远端停止收尾");
                pipeline.stopByDevice();
                return;
            }
            if (stopSettled.compareAndSet(false, true)) {
                HsLog.i(TAG, "随录随传停止成功（控制上报 0x8005）");
                replayStopCommandIfInFlight("设备侧已停止出流");
                replyStop(null);
            }
        }

        /** 结算启动成功（只调用一次）。 */
        private void replyStart(HsControl.Failure failure) {
            StreamRecordPipeline.Reply<HsControl.Failure> reply = startReply;
            if (reply != null) {
                reply.onReply(failure);
                startReply = null;
            }
        }

        /** 在无成功信号的前提下按失败结算启动（成功后调用会被闸门拒绝）。 */
        private void settleStartFailure(HsControl.Failure failure) {
            if (startGate.trySettleFailure()) {
                HsLog.w(TAG, "随录随传启动失败: " + failure);
                replyStart(failure);
            }
        }

        /** 结算停止完成（只调用一次，且永远以 null 上报，避免把停止失败抛给 UI）。 */
        private void settleStop(String source) {
            if (stopSettled.compareAndSet(false, true)) {
                HsLog.i(TAG, "随录随传停止完成（" + source + "）");
                replyStop(null);
            }
        }

        private void replyStop(HsControl.Failure failure) {
            StreamRecordPipeline.Reply<HsControl.Failure> reply = stopReply;
            if (reply != null) {
                reply.onReply(failure);
                stopReply = null;
            }
        }

        /** 开始命令仍在途时主动结算为 SUCCESS，释放调度器串行队列。 */
        private void replayStartCommandIfInFlight(String reason) {
            Command command = startCommand;
            if (command != null && command.getCompletion() == null) {
                HsLog.i(TAG, "命令通道无 99ECD5 回执，设备侧已开始出流，主动结算 StartStreamRecordCommand");
                command.replayCompletion(CommandCompletion.stringResult(
                        Command.ResultCode.SUCCESS, reason));
            }
        }

        /** 停止命令仍在途时主动结算为 SUCCESS，释放调度器串行队列。 */
        private void replayStopCommandIfInFlight(String reason) {
            Command command = stopCommand;
            if (command != null && command.getCompletion() == null) {
                HsLog.i(TAG, "命令通道无 99ECD6 回执，设备侧已停止出流，主动结算 StopStreamRecordCommand");
                command.replayCompletion(CommandCompletion.stringResult(
                        Command.ResultCode.SUCCESS, reason));
            }
        }
    }

    /**
     * 820003 通道入口：控制上报包在此分流，只有音频切片进管线。
     *
     * <p>本方法同时是 HS「BLE 接收数据」的可观测点：命令通道（{@code HsProtocolEngine}）
     * 与音频通道分开打日志，音频通道内部再区分「控制上报包」与「音频流字节」，对齐 WY01
     * 的 {@code SPP接收控制命令数据 / SPP接收音频数据} 两条日志。</p>
     *
     * <p>在判定「是否录音中」之前先把设备侧信号喂给当前会话的启动/停止闸门，
     * 这样启动待确认窗口内的首段音频也能结算启动成功。</p>
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
            HsLog.d(TAG, "BLE接收音频数据: " + HsHex.preview(chunk, HsHex.PREVIEW_BYTES));
        }

        if (!pipeline.isRecording()) {
            return;
        }

        HsRecordCommands commands = activeCommands;
        if (commands != null) {
            commands.onDeviceSignal(control, cmdType);
        }

        if (!control) {
            pipeline.onAudioChunk(chunk);
            return;
        }
        // 控制包不进音频管线；0x8005 的停止语义已在 onDeviceSignal 内处理。
        // 极端情况下（命令对象缺失）仍保底触发一次远端停止收尾。
        if (HsAudioPacketClassifier.isStopSignal(cmdType) && commands == null) {
            HsLog.i(TAG, "耳机远端主动按键停录，触发远端停止收尾");
            pipeline.stopByDevice();
        }
    }
}

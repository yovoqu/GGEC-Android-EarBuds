package com.yovo.biz_ggec_hs.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.StreamRecordConfig;
import com.yovo.biz_ggec_hs.api.StreamRecordListener;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StartStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StopStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.link.HsFailureMapper;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.LoggingAudioFrameTrace;
import com.yovo.lib_ggec_audio.record.StreamRecordPipeline;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HS 随录随传会话：只剩产品命令表 + 820003 控制包分流。
 *
 * <p>组包、解码、振幅、分流、落盘与收尾序列的唯一实现在
 * {@link StreamRecordPipeline}；本类只负责「HS 用什么命令启动/停止」与
 * 「控制上报包怎么处理」。</p>
 */
public final class HsStreamRecordSession {

    private static final String TAG = "HsStreamRecordSession";

    private final HsProtocolEngine engine;
    private final HsAudioDataLink audioLink;
    private final StreamRecordPipeline<HsControl.Failure> pipeline;
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();

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
     * HS 产品命令表：启动要等 820003 Notify 就绪，停止直接下发协议命令，收尾解绑 Notify。
     *
     * <p>失败分类只在 {@link HsFailureMapper} 一处完成，本类不写第二套映射。</p>
     */
    private final class HsRecordCommands
            implements StreamRecordPipeline.Commands<HsControl.Failure> {

        @Override
        public void start(@NonNull StreamRecordPipeline.Reply<HsControl.Failure> reply) {
            audioLink.startAudioNotify(new HsAudioDataLink.ReadyCallback() {
                @Override
                public void onReady() {
                    HsLog.i(TAG, "音频 Notify 通道就绪，发送 StartStreamRecordCommand");
                    engine.submit(new StartStreamRecordCommand(), (command, completion) ->
                            reply.onReply(HsFailureMapper.ofCommandCode(completion.getCode())));
                }

                @Override
                public void onError(@NonNull String message) {
                    HsLog.e(TAG, "音频通道订阅失败: " + message);
                    reply.onReply(HsControl.Failure.DEVICE_ERROR);
                }
            }, HsStreamRecordSession.this::onAudioChunk);
        }

        @Override
        public void stop(@NonNull StreamRecordPipeline.Reply<HsControl.Failure> reply) {
            engine.submit(new StopStreamRecordCommand(), (command, completion) ->
                    reply.onReply(HsFailureMapper.ofCommandCode(completion.getCode())));
        }

        @Override
        public void detach() {
            audioLink.stopAudioNotify();
        }
    }

    /**
     * 820003 通道入口：控制上报包在此分流，只有音频切片进管线。
     */
    private void onAudioChunk(@NonNull byte[] chunk) {
        if (!pipeline.isRecording()) {
            return;
        }
        if (!HsAudioPacketClassifier.isControlPacket(chunk)) {
            pipeline.onAudioChunk(chunk);
            return;
        }
        int cmdType = HsAudioPacketClassifier.resolveControlCmdType(chunk);
        HsLog.i(TAG, "收到音频通道控制上报包: len=" + chunk.length
                + " cmdType=0x" + Integer.toHexString(cmdType));
        if (cmdType == HsAudioPacketClassifier.CMD_TYPE_STOP) {
            HsLog.i(TAG, "耳机远端主动按键停录，触发远端停止收尾");
            pipeline.stopByDevice();
        }
    }
}

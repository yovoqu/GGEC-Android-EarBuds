package com.yovo.biz_ggec_wy.internal.record;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.StreamRecordConfig;
import com.yovo.biz_ggec_wy.api.StreamRecordListener;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyRecordApi;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.LoggingAudioFrameTrace;
import com.yovo.lib_ggec_audio.record.StreamRecordPipeline;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WY 随录随传会话：只剩产品命令表。
 *
 * <p>WY 侧没有随录随传的开关命令（音频通道由 {@code WySession} 自己拉起），因此这里的
 * 产品命令表是「空表」；组包、解码、振幅、分流、落盘与收尾序列的唯一实现在
 * {@link StreamRecordPipeline}。</p>
 */
public final class WyStreamRecordSession implements WyRecordApi {

    /** 主线程调度口子；测试可换成同步执行。 */
    public interface MainDispatcher {
        void post(@NonNull Runnable r);
    }

    /** WY 产品命令表：本端没有开关命令，回执恒为成功，通道不需要单独解绑。 */
    private static final StreamRecordPipeline.Commands<WyControl.Failure> NO_PRODUCT_COMMANDS =
            new StreamRecordPipeline.Commands<>() {
                @Override
                public void start(@NonNull StreamRecordPipeline.Reply<WyControl.Failure> reply) {
                    reply.onReply(null);
                }

                @Override
                public void stop(@NonNull StreamRecordPipeline.Reply<WyControl.Failure> reply) {
                    reply.onReply(null);
                }

                @Override
                public void detach() {
                }
            };

    private final StreamRecordPipeline<WyControl.Failure> pipeline;

    public WyStreamRecordSession(@NonNull AudioCodecProviderPort codecProvider,
                                 @NonNull WyRecordPathResolver pathResolver) {
        this(codecProvider, pathResolver, createDefaultDispatcher(),
                Executors.newSingleThreadExecutor());
    }

    public WyStreamRecordSession(@NonNull AudioCodecProviderPort codecProvider,
                                 @NonNull WyRecordPathResolver pathResolver,
                                 @NonNull MainDispatcher mainDispatcher,
                                 @NonNull ExecutorService decodeExecutor) {
        Objects.requireNonNull(pathResolver, "pathResolver");
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
            return handler::post;
        }
        return Runnable::run;
    }

    @Override
    public boolean isStreamRecording() {
        return pipeline.isRecording();
    }

    @Override
    public void startStreamRecord(@NonNull StreamRecordConfig config,
                                  @NonNull StreamRecordListener listener) {
        pipeline.start(config, listener, NO_PRODUCT_COMMANDS);
    }

    @Override
    public void stopStreamRecord(@NonNull WyControl.Callback callback) {
        pipeline.stop(failure -> callback.onResult(
                failure == null
                        ? new WyControl.Outcome.Ok<>(null)
                        : new WyControl.Outcome.Err<>(failure)));
    }

    /** 音频数据入口：SPP 收到的原始音频字节。 */
    public void onAudioChunkReceived(@NonNull byte[] chunk) {
        pipeline.onAudioChunk(chunk);
    }

    public void release() {
        pipeline.release();
    }
}

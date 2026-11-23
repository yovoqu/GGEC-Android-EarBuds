package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ggec_audio.AudioFrameAssembler;
import com.yovo.lib_ggec_audio.AudioFrameContract;
import com.yovo.lib_ggec_audio.AudioFrameTrace;
import com.yovo.lib_ggec_audio.AudioWireByteOrder;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 随录随传 PCM 管线的唯一实现：组包 → 解码 → 振幅 → 分流 / 落盘 → 收尾。
 *
 * <p>产品之间的差异只有三件事，全部收在 {@link Commands} 里，本类不复制任何产品分支：</p>
 * <ol>
 *   <li>启动时下发的产品命令（有的产品要先等数据通道就绪，有的没有命令）；</li>
 *   <li>停止时下发的产品命令；</li>
 *   <li>停止后解绑音频通道。</li>
 * </ol>
 *
 * <p>失败分类由产品的 {@link Commands} 实现给出（泛型 {@code F}）：产品在自己的映射单点上把
 * 命令完成码翻成对外失败枚举，本类只透传，绝不维护第二套映射表。</p>
 *
 * <p><b>每会话一个 {@link Session}</b>：组包器、解码器、写入器、监听器、配置、命令都挂在会话上，
 * 而不是本类共享字段。{@code start}/{@code stop} 只操作「当前会话」，{@code finish} 只清理
 * <b>传进来的旧会话</b>的局部对象，因此「停→立刻开」时旧会话的延迟清理不会误伤新会话。</p>
 *
 * @param <F> 产品侧的失败分类类型
 */
public final class StreamRecordPipeline<F> {

    /** 主线程调度口子；测试可换成同步执行。 */
    public interface Dispatcher {
        void post(@NonNull Runnable action);
    }

    /** 产品命令表：三条产品动作，全部由产品实现。 */
    public interface Commands<F> {

        /** 下发「开始」产品命令。 */
        void start(@NonNull Reply<F> reply);

        /** 下发「停止」产品命令。 */
        void stop(@NonNull Reply<F> reply);

        /** 解绑音频数据通道（停止/失败收尾时调用）。 */
        void detach();
    }

    /** 产品命令回执：{@code failure == null} 表示成功。 */
    public interface Reply<F> {
        void onReply(@Nullable F failure);
    }

    /** 停止回执：{@code failure == null} 表示正常停止。 */
    public interface StopCallback<F> {
        void onStop(@Nullable F failure);
    }

    private static final String ERROR_ALREADY_RECORDING = "录音已在进行中";
    private static final String ERROR_START_FAILED = "录音启动失败";
    private static final String ERROR_STOP_FAILED = "录音停止失败";

    private final AudioCodecProviderPort codecProvider;
    private final Supplier<File> streamDir;
    private final Supplier<File> defaultStreamFile;
    private final Dispatcher dispatcher;
    private final AudioLog log;
    private final AudioFrameTrace trace;
    private final AudioWireByteOrder byteOrder;
    private final ExecutorService decodeExecutor;

    /** 当前会话；{@code null} 表示未在录音。 */
    private volatile Session current;
    private int generationCounter;

    public StreamRecordPipeline(@NonNull AudioCodecProviderPort codecProvider,
                               @NonNull Supplier<File> streamDir,
                               @NonNull Supplier<File> defaultStreamFile,
                               @NonNull Dispatcher dispatcher,
                               @NonNull AudioLog log,
                               @NonNull AudioFrameTrace trace,
                               @NonNull ExecutorService decodeExecutor) {
        this(codecProvider, streamDir, defaultStreamFile, dispatcher, log, trace,
                decodeExecutor, AudioWireByteOrder.LITTLE_ENDIAN);
    }

    /**
     * 允许产品声明帧头字节序（HS 小端 / WY 大端）。
     *
     * <p>字节序是产品适配参数：共享装配层不自作主张，避免两个产品互相牵制。</p>
     *
     * <p><b>执行器约定</b>：{@code decodeExecutor} 必须是<b>单线程</b>且 FIFO 的执行器——
     * 解码与 {@code finish} 的清理任务都投递到它，只有单线程才能保证「writer/decoder 关闭
     * 一定落在在途解码之后」。WY/HS 均传 {@code Executors.newSingleThreadExecutor()}。</p>
     */
    public StreamRecordPipeline(@NonNull AudioCodecProviderPort codecProvider,
                               @NonNull Supplier<File> streamDir,
                               @NonNull Supplier<File> defaultStreamFile,
                               @NonNull Dispatcher dispatcher,
                               @NonNull AudioLog log,
                               @NonNull AudioFrameTrace trace,
                               @NonNull ExecutorService decodeExecutor,
                               @NonNull AudioWireByteOrder byteOrder) {
        this.codecProvider = Objects.requireNonNull(codecProvider, "codecProvider");
        this.streamDir = Objects.requireNonNull(streamDir, "streamDir");
        this.defaultStreamFile = Objects.requireNonNull(defaultStreamFile, "defaultStreamFile");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.log = Objects.requireNonNull(log, "log");
        this.trace = Objects.requireNonNull(trace, "trace");
        this.byteOrder = Objects.requireNonNull(byteOrder, "byteOrder");
        this.decodeExecutor = Objects.requireNonNull(decodeExecutor, "decodeExecutor");
    }

    public synchronized boolean isRecording() {
        return current != null;
    }

    /**
     * 开始随录随传：先备好本会话的组包器/解码器/写入器，再下发产品「开始」命令。
     */
    public synchronized void start(@NonNull StreamRecordConfig config,
                                   @NonNull StreamRecordListener listener,
                                   @NonNull Commands<F> commands) {
        if (current != null) {
            dispatcher.post(() -> listener.onError(ERROR_ALREADY_RECORDING));
            return;
        }

        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(commands, "commands");

        File targetFile;
        AudioFileWriter writer;
        if (config.outputMode() == StreamRecordConfig.RecordOutputMode.STREAM_ONLY) {
            targetFile = null;
            writer = null;
        } else {
            targetFile = StreamRecordTargets.resolve(config, streamDir.get(), defaultStreamFile.get());
            writer = codecProvider.createPcmFileWriter(targetFile);
        }

        AudioFrameAssembler assembler = new AudioFrameAssembler(trace, byteOrder);
        assembler.reset();
        assembler.setAllowIndexResync(false);

        Session session = new Session(
                ++generationCounter,
                assembler,
                codecProvider.createDecoder(),
                writer,
                targetFile,
                listener,
                config,
                commands);
        current = session;

        commands.start(failure -> {
            if (failure == null) {
                synchronized (StreamRecordPipeline.this) {
                    if (current == session) {
                        dispatcher.post(listener::onStarted);
                    }
                }
            } else {
                log.warn(ERROR_START_FAILED);
                finish(session, null, failure, ERROR_START_FAILED);
            }
        });
    }

    /** 停止随录随传：先下发产品「停止」命令，再收尾。 */
    public synchronized void stop(@Nullable StopCallback<F> callback) {
        Session session = current;
        if (session == null) {
            if (callback != null) {
                callback.onStop(null);
            }
            return;
        }
        session.commands.stop(failure -> {
            if (failure != null) {
                log.warn(ERROR_STOP_FAILED);
            }
            finish(session, callback, failure, failure == null ? null : ERROR_STOP_FAILED);
        });
    }

    /**
     * 设备侧主动停录（按键/远端上报）：没有产品命令要下发，直接收尾。
     */
    public synchronized void stopByDevice() {
        finish(current, null, null, null);
    }

    /**
     * 投喂一个音频 Notify 切片；控制上报包由产品侧先行分流，不进本方法。
     */
    public void onAudioChunk(@NonNull byte[] chunk) {
        Session session = current;
        if (session == null || chunk.length == 0) {
            return;
        }
        decodeExecutor.execute(() -> decodeChunk(session, chunk));
    }

    private void decodeChunk(@NonNull Session session, @NonNull byte[] chunk) {
        if (session.closed) {
            return;
        }
        List<byte[]> opusFrames = session.assembler.assemble(chunk);
        if (opusFrames.isEmpty()) {
            return;
        }
        AudioDecoder decoder = session.decoder;
        if (decoder == null) {
            return;
        }
        for (byte[] opus : opusFrames) {
            byte[] pcm = decoder.decode(opus);
            if (pcm == null || pcm.length == 0) {
                continue;
            }
            session.totalPcmFrames++;
            float amplitude = PcmAmplitude.normalized(pcm);

            if (session.config.outputMode() != StreamRecordConfig.RecordOutputMode.FILE_ONLY
                    && session.listener != null) {
                dispatcher.post(() -> session.listener.onPcmFrame(pcm, amplitude));
            }

            AudioFileWriter writer = session.writer;
            if (writer != null) {
                writer.writePcm(pcm);
            }
        }
    }

    /**
     * 统一收尾：解绑通道 → 关写入器 → 放解码器 → 按「错误/落盘/停止」顺序回调。
     *
     * <p>只清理<b>传进来的这个会话</b>：先把 {@code current} 置空并标记 {@code closed}，再把该会话的
     * 写入器/解码器抓进局部变量交给解码线程延迟关闭。这样即便立刻 {@code start} 了新会话，
     * 排队中的清理任务也只触碰旧会话的局部引用，不会关掉新会话的解码器/写入器。</p>
     *
     * @param session      要收尾的会话；不是当前会话或已收尾时直接回调 null
     * @param failure      产品判定的失败分类；{@code null} 表示正常停止
     * @param errorMessage 非 {@code null} 时先给监听器一次错误回调
     */
    private synchronized void finish(@Nullable Session session,
                                     @Nullable StopCallback<F> callback,
                                     @Nullable F failure,
                                     @Nullable String errorMessage) {
        if (session == null || session != current || session.closed) {
            if (callback != null) {
                callback.onStop(null);
            }
            return;
        }

        session.closed = true;
        current = null;

        session.commands.detach();

        long calcDuration = session.totalPcmFrames * AudioFrameContract.FRAME_PERIOD_MS;
        if (calcDuration <= 0L) {
            calcDuration = Math.max(0L, System.currentTimeMillis() - session.startTimeMs);
        }
        final long finalDuration = calcDuration;
        final File savedFile = session.targetFile;
        log.info("停止随录随传，总 PCM 帧数=" + session.totalPcmFrames + " 测算时长=" + finalDuration
                + "ms 文件=" + (savedFile != null ? savedFile.getAbsolutePath() : "null"));

        final StreamRecordListener listener = session.listener;
        final AudioFileWriter writer = session.writer;
        final AudioDecoder decoder = session.decoder;

        decodeExecutor.execute(() -> {
            if (writer != null) {
                writer.close();
            }
            if (decoder != null) {
                decoder.release();
            }
            dispatcher.post(() -> {
                if (errorMessage != null && listener != null) {
                    listener.onError(errorMessage);
                }
                if (listener != null && StreamRecordTargets.hasContent(savedFile)) {
                    listener.onFileSaved(savedFile, finalDuration, savedFile.length());
                }
                if (listener != null) {
                    listener.onStopped();
                }
                if (callback != null) {
                    callback.onStop(failure);
                }
            });
        });
    }

    public void release() {
        finish(current, null, null, null);
        decodeExecutor.shutdown();
    }

    /** 一次录音会话的全部可变状态；与代次绑定，收尾只触及本对象。 */
    private final class Session {

        final int generation;
        final AudioFrameAssembler assembler;
        final AudioDecoder decoder;
        final AudioFileWriter writer;
        @Nullable
        final File targetFile;
        final StreamRecordListener listener;
        final StreamRecordConfig config;
        final Commands<F> commands;
        final long startTimeMs;

        volatile long totalPcmFrames;
        volatile boolean closed;

        Session(int generation,
                @NonNull AudioFrameAssembler assembler,
                @NonNull AudioDecoder decoder,
                @Nullable AudioFileWriter writer,
                @Nullable File targetFile,
                @NonNull StreamRecordListener listener,
                @NonNull StreamRecordConfig config,
                @NonNull Commands<F> commands) {
            this.generation = generation;
            this.assembler = assembler;
            this.decoder = decoder;
            this.writer = writer;
            this.targetFile = targetFile;
            this.listener = listener;
            this.config = config;
            this.commands = commands;
            this.startTimeMs = System.currentTimeMillis();
        }
    }
}

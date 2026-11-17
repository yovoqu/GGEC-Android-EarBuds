package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ggec_audio.AudioFrameAssembler;
import com.yovo.lib_ggec_audio.AudioFrameTrace;

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
    private final AudioFrameAssembler frameAssembler;
    private final ExecutorService decodeExecutor;

    private AudioDecoder decoder;
    private AudioFileWriter fileWriter;
    private File currentTargetFile;
    private StreamRecordListener currentListener;
    private StreamRecordConfig currentConfig;
    private Commands<F> currentCommands;

    private volatile boolean recording;
    private long totalPcmFrames;
    private long startTimeMs;

    public StreamRecordPipeline(@NonNull AudioCodecProviderPort codecProvider,
                               @NonNull Supplier<File> streamDir,
                               @NonNull Supplier<File> defaultStreamFile,
                               @NonNull Dispatcher dispatcher,
                               @NonNull AudioLog log,
                               @NonNull AudioFrameTrace trace,
                               @NonNull ExecutorService decodeExecutor) {
        this.codecProvider = Objects.requireNonNull(codecProvider, "codecProvider");
        this.streamDir = Objects.requireNonNull(streamDir, "streamDir");
        this.defaultStreamFile = Objects.requireNonNull(defaultStreamFile, "defaultStreamFile");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.log = Objects.requireNonNull(log, "log");
        this.frameAssembler = new AudioFrameAssembler(Objects.requireNonNull(trace, "trace"));
        this.decodeExecutor = Objects.requireNonNull(decodeExecutor, "decodeExecutor");
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    /**
     * 开始随录随传：先备好组包器/解码器/写入器，再下发产品「开始」命令。
     */
    public synchronized void start(@NonNull StreamRecordConfig config,
                                   @NonNull StreamRecordListener listener,
                                   @NonNull Commands<F> commands) {
        if (recording) {
            dispatcher.post(() -> listener.onError(ERROR_ALREADY_RECORDING));
            return;
        }

        this.currentConfig = Objects.requireNonNull(config, "config");
        this.currentListener = Objects.requireNonNull(listener, "listener");
        this.currentCommands = Objects.requireNonNull(commands, "commands");
        this.recording = true;
        this.totalPcmFrames = 0;
        this.startTimeMs = System.currentTimeMillis();

        frameAssembler.reset();
        frameAssembler.setAllowIndexResync(false);
        if (decoder != null) {
            decoder.release();
        }
        decoder = codecProvider.createDecoder();

        if (config.outputMode() == StreamRecordConfig.RecordOutputMode.STREAM_ONLY) {
            currentTargetFile = null;
            fileWriter = null;
        } else {
            currentTargetFile = StreamRecordTargets.resolve(
                    config, streamDir.get(), defaultStreamFile.get());
            fileWriter = codecProvider.createPcmFileWriter(currentTargetFile);
        }

        commands.start(failure -> {
            if (failure == null) {
                dispatcher.post(listener::onStarted);
            } else {
                log.warn(ERROR_START_FAILED);
                finish(null, failure, ERROR_START_FAILED);
            }
        });
    }

    /** 停止随录随传：先下发产品「停止」命令，再收尾。 */
    public synchronized void stop(@Nullable StopCallback<F> callback) {
        if (!recording) {
            if (callback != null) {
                callback.onStop(null);
            }
            return;
        }
        Commands<F> commands = currentCommands;
        commands.stop(failure -> {
            if (failure != null) {
                log.warn(ERROR_STOP_FAILED);
            }
            finish(callback, failure, failure == null ? null : ERROR_STOP_FAILED);
        });
    }

    /**
     * 设备侧主动停录（按键/远端上报）：没有产品命令要下发，直接收尾。
     */
    public synchronized void stopByDevice() {
        finish(null, null, null);
    }

    /**
     * 投喂一个音频 Notify 切片；控制上报包由产品侧先行分流，不进本方法。
     */
    public void onAudioChunk(@NonNull byte[] chunk) {
        if (!recording || chunk.length == 0) {
            return;
        }
        decodeExecutor.execute(() -> decodeChunk(chunk));
    }

    private void decodeChunk(byte[] chunk) {
        List<byte[]> opusFrames = frameAssembler.assemble(chunk);
        if (opusFrames.isEmpty()) {
            return;
        }
        AudioDecoder currentDecoder = decoder;
        if (currentDecoder == null) {
            return;
        }
        for (byte[] opus : opusFrames) {
            byte[] pcm = currentDecoder.decode(opus);
            if (pcm == null || pcm.length == 0) {
                continue;
            }
            totalPcmFrames++;
            float amplitude = PcmAmplitude.normalized(pcm);
            if (totalPcmFrames == 1 || totalPcmFrames % 50 == 0) {
                log.debug("解码 PCM 帧=" + totalPcmFrames + " 时长=" + (totalPcmFrames * 20)
                        + "ms 振幅=" + amplitude);
            }

            StreamRecordConfig config = currentConfig;
            StreamRecordListener listener = currentListener;
            if (config != null
                    && config.outputMode() != StreamRecordConfig.RecordOutputMode.FILE_ONLY
                    && listener != null) {
                dispatcher.post(() -> listener.onPcmFrame(pcm, amplitude));
            }

            AudioFileWriter writer = fileWriter;
            if (writer != null) {
                writer.writePcm(pcm);
            }
        }
    }

    /**
     * 统一收尾：解绑通道 → 关写入器 → 放解码器 → 复位组包器 → 按「错误/落盘/停止」顺序回调。
     *
     * @param failure 产品判定的失败分类；{@code null} 表示正常停止
     * @param errorMessage 非 {@code null} 时先给监听器一次错误回调
     */
    private synchronized void finish(@Nullable StopCallback<F> callback,
                                     @Nullable F failure,
                                     @Nullable String errorMessage) {
        if (!recording) {
            if (callback != null) {
                callback.onStop(null);
            }
            return;
        }

        recording = false;
        Commands<F> commands = currentCommands;
        if (commands != null) {
            commands.detach();
        }

        StreamRecordListener listener = currentListener;
        File savedFile = currentTargetFile;
        long calcDuration = totalPcmFrames * 20L;
        if (calcDuration <= 0L) {
            calcDuration = Math.max(0L, System.currentTimeMillis() - startTimeMs);
        }
        final long finalDuration = calcDuration;
        log.info("停止随录随传，总 PCM 帧数=" + totalPcmFrames + " 测算时长=" + finalDuration
                + "ms 文件=" + (savedFile != null ? savedFile.getAbsolutePath() : "null"));

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
        finish(null, null, null);
        decodeExecutor.shutdown();
    }
}

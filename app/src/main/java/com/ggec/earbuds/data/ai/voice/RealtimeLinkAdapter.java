package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiRealtimeInput;
import com.ggec.earbuds.domain.ai.port.AiRealtimeLink;
import com.ggec.earbuds.domain.ai.port.AiRealtimeOutput;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.realtime.YoRealtime;
import com.yovo.lib_ai.realtime.YoRealtimeConfig;
import com.yovo.lib_ai.realtime.YoRealtimeDialect;
import com.yovo.lib_ai.realtime.YoRealtimeAudioFormat;
import com.yovo.lib_ai.realtime.YoRealtimeTurnDetection;
import com.yovo.lib_ai.realtime.YoRealtimeInput;
import com.yovo.lib_ai.realtime.YoRealtimeOutput;

import java.util.Objects;

/**
 * 把 {@link YoRealtime} 的输入输出译成领域端口。
 *
 * <p>这一层只做改名：地址、密钥、模型和方言进 {@link YoRealtimeConfig}，
 * {@link AiRealtimeInput} 进 {@link YoRealtimeInput}，{@link YoRealtimeOutput} 出
 * {@link AiRealtimeOutput}。传输、方言 JSON 和打断截断不在这里组装。</p>
 *
 * <p>本地 {@link #close()} 之后不再把 {@link YoRealtimeOutput.Closed} 报成失败。
 * 没经过这次 close 的 Closed 是对端断开，报成 {@code NETWORK}。</p>
 */
public final class RealtimeLinkAdapter implements AiRealtimeLink {

    private static final int MIN_REDACTED_KEY = 8;
    private static final int MAX_MESSAGE = 300;

    @Nullable
    private volatile YoRealtime.Connection current;
    private volatile int outputSampleRateHz = YoRealtimeConfig.DEFAULT_OUTPUT_SAMPLE_RATE_HZ;
    private volatile int inputWireSampleRateHz = YoRealtimeConfig.DEFAULT_OUTPUT_SAMPLE_RATE_HZ;
    private volatile boolean closed;
    private volatile boolean failureReported;

    public RealtimeLinkAdapter() {
    }

    @Override
    public void open(@NonNull Config config, @NonNull Listener listener) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");

        YoRealtime.Connection stale = current;
        if (stale != null) {
            bestEffort(stale::close);
            current = null;
        }
        closed = false;
        failureReported = false;
        outputSampleRateHz = YoRealtimeConfig.DEFAULT_OUTPUT_SAMPLE_RATE_HZ;
        YoRealtimeConfig realtime;
        try {
            YoRealtimeDialect dialect = YoRealtimeDialect.infer(config.baseUrl(), config.model());
            inputWireSampleRateHz = dialect == YoRealtimeDialect.QWEN ? 16_000 : 24_000;
            realtime = YoRealtimeConfig.builder()
                    .baseUrl(config.baseUrl())
                    .apiKey(config.apiKey())
                    .model(config.model())
                    .dialect(dialect)
                    .inputSampleRateHz(inputWireSampleRateHz)
                    .inputWireFormat(YoRealtimeAudioFormat.pcm16(inputWireSampleRateHz))
                    .outputSampleRateHz(outputSampleRateHz)
                    .countPlaybackOnArrival(false)
                    .interruptOnSpeech(config.bargeIn())
                    .turnDetection(YoRealtimeTurnDetection.serverVadFor(dialect, config.bargeIn()))
                    .build();
        } catch (IllegalArgumentException unusable) {
            failureReported = true;
            emit(listener, new AiRealtimeOutput.Failed(new AiFailure(AiFailureKind.BAD_REQUEST,
                    redact(unusable.getMessage(), config.apiKey()), unusable)));
            return;
        }

        try {
            current = YoRealtime.open(realtime, output -> deliver(output, listener, config.apiKey()));
        } catch (RuntimeException broken) {
            failOnce(listener, new AiFailure(AiFailureKind.UNKNOWN,
                    redact(broken.getMessage(), config.apiKey()), broken));
        }
    }

    @Override
    public void send(@NonNull AiRealtimeInput input) {
        YoRealtime.Connection link = current;
        if (closed || link == null) {
            return;
        }
        try {
            link.send(toSdk(input));
        } catch (RuntimeException lost) {
            // 上行失败由 SDK 的 Failed 输出上报。
        }
    }

    @Override
    public int inputWireSampleRateHz() {
        return inputWireSampleRateHz;
    }

    @Override
    public void truncate(@NonNull String itemId, int contentIndex, int audioEndMs) {
        YoRealtime.Connection link = current;
        if (closed || link == null || itemId.isBlank()) {
            return;
        }
        try {
            link.truncate(itemId, contentIndex, audioEndMs);
        } catch (RuntimeException lost) {
            // 截断失败不影响本地已经回到聆听；对话史略长一点由下一轮的 instructions 兜住。
        }
    }

    @Override
    public void close() {
        closed = true;
        YoRealtime.Connection link = current;
        current = null;
        if (link != null) {
            bestEffort(link::close);
        }
    }


    private static YoRealtimeInput toSdk(@NonNull AiRealtimeInput input) {
        return switch (input) {
            case AiRealtimeInput.Audio audio -> new YoRealtimeInput.Audio(audio.pcm16());
            case AiRealtimeInput.Cancel ignored -> new YoRealtimeInput.Cancel();
            case AiRealtimeInput.Clear ignored -> new YoRealtimeInput.Clear();
        };
    }

    private void deliver(@NonNull YoRealtimeOutput output, Listener listener, String apiKey) {
        if (closed) {
            return;
        }
        switch (output) {
            case YoRealtimeOutput.Opened ignored -> emit(listener, new AiRealtimeOutput.Opened());
            case YoRealtimeOutput.SpeechStarted ignored -> emit(listener, new AiRealtimeOutput.UserSpeechStarted());
            case YoRealtimeOutput.SpeechStopped ignored -> emit(listener, new AiRealtimeOutput.UserSpeechStopped());
            case YoRealtimeOutput.AssistantText text -> {
                if (!text.delta().isEmpty()) {
                    emit(listener, new AiRealtimeOutput.AssistantText(text.delta()));
                }
            }
            case YoRealtimeOutput.AssistantAudio audio -> {
                if (audio.pcm16().length > 0) {
                    emit(listener, new AiRealtimeOutput.AssistantAudio(
                            audio.pcm16(), outputSampleRateHz,
                            audio.itemId(), audio.contentIndex()));
                }
            }
            case YoRealtimeOutput.ResponseDone ignored -> emit(listener, new AiRealtimeOutput.ResponseDone());
            case YoRealtimeOutput.UserTranscript user -> {
                if (!user.transcript().isBlank()) {
                    emit(listener, new AiRealtimeOutput.UserTranscript(
                            user.transcript(), user.itemId()));
                }
            }
            case YoRealtimeOutput.ToolCall ignored -> {
                // 通话页还没有工具调用。
            }
            case YoRealtimeOutput.TranslationDelta ignored -> {
                // 通话页还没有同传字幕。
            }
            case YoRealtimeOutput.Failed failed -> failOnce(listener, new AiFailure(
                    kindOf(failed.kind()), redact(failed.message(), apiKey)));
            case YoRealtimeOutput.Closed ignored -> failOnce(listener,
                    new AiFailure(AiFailureKind.NETWORK, null));
        }
    }

    private void failOnce(Listener listener, AiFailure failure) {
        if (closed || failureReported) {
            return;
        }
        failureReported = true;
        emit(listener, new AiRealtimeOutput.Failed(failure));
    }

    private void emit(Listener listener, AiRealtimeOutput output) {
        if (closed) {
            return;
        }
        listener.onOutput(output);
    }

    /**
     * lib_ai 的错误分类只有一份（{@code YoAiErrors}/{@code YoAiErrorKind}），这里只做枚举改名：
     * 领域层不该认识 SDK 的枚举，界面也不该为同一种失败给出两套原因名。
     */
    static AiFailureKind kindOf(@Nullable YoAiErrorKind kind) {
        if (kind == null) {
            return AiFailureKind.UNKNOWN;
        }
        return switch (kind) {
            case AUTHENTICATION -> AiFailureKind.AUTHENTICATION;
            case PERMISSION -> AiFailureKind.PERMISSION;
            case QUOTA -> AiFailureKind.QUOTA;
            case RATE_LIMITED -> AiFailureKind.RATE_LIMITED;
            case NOT_FOUND -> AiFailureKind.NOT_FOUND;
            case BAD_REQUEST -> AiFailureKind.BAD_REQUEST;
            case CONTENT_FILTERED -> AiFailureKind.CONTENT_FILTERED;
            case CONTEXT_LENGTH -> AiFailureKind.CONTEXT_LENGTH;
            case UNSUPPORTED_PARAM -> AiFailureKind.UNSUPPORTED_PARAM;
            case OVERLOADED -> AiFailureKind.OVERLOADED;
            case SERVER -> AiFailureKind.SERVER;
            case NETWORK -> AiFailureKind.NETWORK;
            case TIMEOUT -> AiFailureKind.TIMEOUT;
            case CANCELLED -> AiFailureKind.CANCELLED;
            case PROTOCOL -> AiFailureKind.PROTOCOL;
            case UNSUPPORTED_CAPABILITY -> AiFailureKind.UNSUPPORTED_CAPABILITY;
            case UNKNOWN -> AiFailureKind.UNKNOWN;
        };
    }

    /** 平台回显里可能带密钥片段；进领域之前抹掉，并且截断，避免把整段报文塞进页面。 */
    @NonNull
    private static String redact(@Nullable String message, @Nullable String apiKey) {
        String clean = message == null ? "" : message;
        if (apiKey != null && apiKey.length() >= MIN_REDACTED_KEY) {
            clean = clean.replace(apiKey, "***");
        }
        return clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
    }

    private static void bestEffort(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException alreadyClosing) {
            // 关闭路径上某个环节失败不该挡住其他环节；通话已经结束，没有第二条命令要发。
        }
    }
}

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
import com.yovo.lib_ai.realtime.YoRealtimeInput;
import com.yovo.lib_ai.realtime.YoRealtimeOutput;
import com.yovo.lib_ai.realtime.YoRealtimeTurnDetection;

import java.util.Objects;

/**
 * 把 {@link YoRealtime} 的输入输出适配成领域端口 {@link AiRealtimeLink}。
 *
 * <p>所有配置与方言默认线速率均由 {@link YoRealtimeConfig} 统一解析并在握手完成时通过
 * {@link AiRealtimeOutput.Opened} 向上游公布；音频、取消、截断与清空统一走 {@link #send} 单入口。</p>
 */
public final class RealtimeLinkAdapter implements AiRealtimeLink {

    private static final int MIN_REDACTED_KEY = 8;
    private static final int MAX_MESSAGE = 300;

    @Nullable
    private volatile YoRealtime.Connection current;
    private volatile int outputSampleRateHz = YoRealtimeConfig.DEFAULT_WIRE_SAMPLE_RATE_HZ;
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
        outputSampleRateHz = YoRealtimeConfig.DEFAULT_WIRE_SAMPLE_RATE_HZ;

        YoRealtimeConfig realtime;
        try {
            YoRealtimeDialect dialect = YoRealtimeDialect.infer(config.baseUrl(), config.model());
            realtime = YoRealtimeConfig.builder()
                    .baseUrl(config.baseUrl())
                    .apiKey(config.apiKey())
                    .model(config.model())
                    .dialect(dialect)
                    .turnDetection(YoRealtimeTurnDetection.serverVadFor(dialect, false))
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
        Objects.requireNonNull(input, "input");
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
    public void close() {
        closed = true;
        YoRealtime.Connection link = current;
        current = null;
        if (link != null) {
            bestEffort(link::close);
        }
    }

    @NonNull
    private static YoRealtimeInput toSdk(@NonNull AiRealtimeInput input) {
        return switch (input) {
            case AiRealtimeInput.Audio audio -> new YoRealtimeInput.Audio(audio.pcm16());
            case AiRealtimeInput.Cancel ignored -> new YoRealtimeInput.Cancel();
            case AiRealtimeInput.Truncate truncate -> new YoRealtimeInput.Truncate(
                    truncate.itemId(), truncate.contentIndex(), truncate.audioEndMs());
            case AiRealtimeInput.Clear ignored -> new YoRealtimeInput.Clear();
        };
    }

    private void deliver(@NonNull YoRealtimeOutput output, Listener listener, String apiKey) {
        if (closed) {
            return;
        }
        switch (output) {
            case YoRealtimeOutput.Opened opened -> {
                outputSampleRateHz = opened.outputSampleRateHz();
                emit(listener, new AiRealtimeOutput.Opened(
                        opened.inputSampleRateHz(), opened.outputSampleRateHz()));
            }
            case YoRealtimeOutput.SpeechStarted ignored ->
                    emit(listener, new AiRealtimeOutput.UserSpeechStarted());
            case YoRealtimeOutput.SpeechStopped ignored ->
                    emit(listener, new AiRealtimeOutput.UserSpeechStopped());
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
            }
            case YoRealtimeOutput.TranslationDelta ignored -> {
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
        }
    }
}

package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiRealtimeLink;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.realtime.RealtimeWebSocketLink;
import com.yovo.lib_ai.realtime.YoRealtimeConfig;
import com.yovo.lib_ai.realtime.YoRealtimeEvent;
import com.yovo.lib_ai.realtime.YoRealtimeLink;
import com.yovo.lib_ai.realtime.YoRealtimeListener;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 把 lib_ai 的 {@link YoRealtimeLink} 包成领域端口 {@link AiRealtimeLink}。
 *
 * <p><b>为什么需要这一层：</b>领域只知道"打开一条双向语音链路、送音频、取消回答"，
 * 不该知道 Realtime 的帧名、也不该依赖 OkHttp。lib_ai 那边的事件是有字段的事件对象，
 * 领域这边是七个语义化回调；两边的形状不一样，中间必须有一次翻译，而翻译是要被测试和替换的
 * （单测里换掉的是端口那一侧，不是 lib_ai 那一侧）。</p>
 *
 * <p><b>为什么每次 open 都新建一条 lib_ai 链路：</b>那条链路是一次性的（open 一次、
 * close 之后不能重开），而一次通话正好对应一条。复用实例会在第二通电话里直接抛
 * {@code IllegalStateException}，而这种 bug 只有用户打第二通电话才会出现。</p>
 *
 * <p><b>为什么 close 之后不再回调：</b>端口没有"已关闭"这个回调。用户挂断时我们主动 close，
 * 如果这时还把 lib_ai 的 {@code CLOSED} 折成 {@code onFailure}，状态机就会在 ENDED 之后再报一次
 * 失败——页面会从"已结束"跳成"出错了"。所以本地 close 之后一律静默；反过来，
 * 没经过我们 close 的 CLOSED 是真断线，必须报成失败（{@code NETWORK}），不能静默。</p>
 */
public final class RealtimeLinkAdapter implements AiRealtimeLink {

    private static final int MIN_REDACTED_KEY = 8;
    private static final int MAX_MESSAGE = 300;

    private final Supplier<YoRealtimeLink> links;

    @Nullable
    private volatile YoRealtimeLink current;
    private volatile boolean closed;
    private volatile boolean failureReported;

    /**
     * 生产用构造：每次 open 新建一条 {@code RealtimeWebSocketLink}。
     */
    public RealtimeLinkAdapter() {
        this(RealtimeWebSocketLink::new);
    }

    /**
     * @param links 链路工厂。真实通话用 {@link RealtimeWebSocketLink::new}；
     *              留这个构造是为了在没有网络的环境里演练本类自己的翻译逻辑。
     */
    RealtimeLinkAdapter(@NonNull Supplier<YoRealtimeLink> links) {
        this.links = Objects.requireNonNull(links, "links");
    }

    @Override
    public void open(@NonNull Config config, @NonNull Listener listener) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");

        // 同一个适配器被复用时（理论上每通电话一个，这里只是防御）：先收掉上一条。
        YoRealtimeLink stale = current;
        if (stale != null) {
            bestEffort(stale::close);
            current = null;
        }
        closed = false;
        failureReported = false;

        YoRealtimeConfig realtime;
        try {
            realtime = YoRealtimeConfig.builder()
                    .baseUrl(config.baseUrl())
                    .apiKey(config.apiKey())
                    .model(config.model())
                    .inputSampleRateHz(config.inputSampleRateHz())
                    .outputSampleRateHz(config.outputSampleRateHz())
                    .build();
        } catch (IllegalArgumentException unusable) {
            // 地址或密钥不合法：端口没有返回值，只能折成一次失败回调。
            failureReported = true;
            listener.onFailure(new AiFailure(AiFailureKind.BAD_REQUEST,
                    redact(unusable.getMessage(), config.apiKey()), unusable));
            return;
        }

        YoRealtimeLink link = links.get();
        current = link;
        try {
            link.open(realtime, new YoRealtimeListener() {
                @Override
                public void onEvent(@NonNull YoRealtimeEvent event) {
                    deliver(event, listener, config.apiKey());
                }
            });
        } catch (RuntimeException broken) {
            // lib_ai 的 open 在参数非法、已关闭时会抛：折成失败，交给状态机统一收尾。
            failOnce(listener, new AiFailure(AiFailureKind.UNKNOWN,
                    redact(broken.getMessage(), config.apiKey()), broken));
        }
    }

    @Override
    public void appendAudio(@NonNull byte[] pcm16) {
        YoRealtimeLink link = current;
        if (closed || link == null) {
            return;
        }
        try {
            link.appendAudio(pcm16);
        } catch (RuntimeException lost) {
            // 上行失败在 lib_ai 内部已经会折成 ERROR 事件；这里再抛只会打断采集线程。
        }
    }

    @Override
    public void cancelResponse() {
        YoRealtimeLink link = current;
        if (closed || link == null) {
            return;
        }
        try {
            link.cancelResponse();
        } catch (RuntimeException lost) {
            // 同上：取消失败由链路的 ERROR 事件负责上报。
        }
    }

    @Override
    public void close() {
        // 幂等：状态机可能从挂断、失败、页面销毁三条路径同时走到这里。
        closed = true;
        YoRealtimeLink link = current;
        current = null;
        if (link != null) {
            bestEffort(link::close);
        }
    }

    // ---------------------------------------------------------------- 事件翻译

    private void deliver(@NonNull YoRealtimeEvent event, Listener listener, String apiKey) {
        if (closed) {
            return;
        }
        switch (event.type()) {
            case OPENED -> listener.onOpened();
            case INPUT_SPEECH_STARTED -> listener.onUserSpeechStarted();
            case INPUT_SPEECH_STOPPED -> listener.onUserSpeechStopped();
            case ASSISTANT_TEXT_DELTA -> {
                String delta = event.text();
                if (delta != null && !delta.isEmpty()) {
                    listener.onAssistantText(delta);
                }
            }
            case ASSISTANT_AUDIO_CHUNK -> {
                byte[] pcm = event.audioPcm16();
                if (pcm != null && pcm.length > 0) {
                    listener.onAssistantAudio(pcm);
                }
            }
            case RESPONSE_DONE -> listener.onResponseDone();
            case ERROR -> failOnce(listener, new AiFailure(
                    kindOf(event.errorKind()),
                    redact(event.message(), apiKey)));
            case CLOSED -> {
                // 我们自己 close 的情况在上面已经被 closed 挡掉了；走到这里说明是被动断开，
                // 而且 lib_ai 没给过 ERROR（例如对端直接关掉，连 close 原因都没有）。
                failOnce(listener, new AiFailure(AiFailureKind.NETWORK, "实时链路已经断开。"));
            }
        }
    }

    private void failOnce(Listener listener, AiFailure failure) {
        if (closed || failureReported) {
            return;
        }
        failureReported = true;
        listener.onFailure(failure);
    }

    /**
     * lib_ai 的错误分类只有一份（{@code YoAiErrors}/{@code YoAiErrorKind}），这里只做枚举改名，
     * 与 {@code WhisperSpeechRecognizer} 保持同一套映射：同一条链路上的两种协议，
     * 不该在界面上给出两套不同的原因名。
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

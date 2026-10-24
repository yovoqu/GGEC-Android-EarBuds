package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.AiSpeechRecognizer;
import com.ggec.earbuds.domain.ai.voice.AiVoiceException;
import com.yovo.lib_ai.catalog.YoPlatformCatalog;
import com.yovo.lib_ai.catalog.YoPlatformDescriptor;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.speech.WavEncoder;
import com.yovo.lib_ai.speech.YoSpeech;
import com.yovo.lib_ai.speech.YoTranscription;
import com.yovo.lib_ai.speech.YoTranscriptionRequest;
import com.yovo.lib_ai.speech.YoWhisperSpeech;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link AiSpeechRecognizer} 的实现：把一段 PCM 包成 wav，走 lib_ai 的 Whisper 兼容转写
 * （{@code POST /audio/transcriptions}，见 {@link YoWhisperSpeech}）。
 *
 * <p>为什么平台与密钥是"每次调用现取"而不是构造时绑定：端口的方法签名里没有平台参数
 * （{@code transcribe(pcm, sampleRateHz, model)}），而用户会在通话前、甚至通话中改平台和密钥。
 * 把 {@link AiSettingsRepository} 注入进来、每次转写时读一次，既守住了端口签名，
 * 又不会出现"改了地址还要重启应用"。</p>
 *
 * <p>为什么不传 {@code language}：这条链路是给耳机用的，用户可能说中文也可能说英文，
 * 硬写 {@code zh} 会让英文被强行译成中文。缺少语种提示时 Whisper 自己会判定。</p>
 *
 * <p>为什么空音频直接返回空串而不发请求：空请求在服务端只会得到 400，
 * 那会把"用户其实没说话"变成一次失败。静音返回空串是协议里合法的结果，
 * 调用方据此跳过这一轮（见 {@code CascadedVoiceStrategy}）。</p>
 */
public final class WhisperSpeechRecognizer implements AiSpeechRecognizer, AutoCloseable {

    /** 平台没配转写模型时用它：OpenAI 与绝大多数兼容网关都认这个名字。 */
    public static final String DEFAULT_TRANSCRIPTION_MODEL = "whisper-1";

    private static final int MAX_MESSAGE = 300;
    private static final int MIN_REDACTED_KEY = 8;

    private final AiSettingsRepository settings;
    @Nullable
    private final String platformId;
    private final Supplier<YoSpeech> speech;

    private YoSpeech created;
    private boolean closed;

    /** 跟随对话页当前选中的平台：绝大多数用户只有一个平台，不该为语音再配一遍。 */
    public WhisperSpeechRecognizer(@NonNull AiSettingsRepository settings) {
        this(settings, null);
    }

    /**
     * @param platformId 语音单独指定用哪个平台；为空表示跟当前选中的对话平台走
     */
    public WhisperSpeechRecognizer(@NonNull AiSettingsRepository settings, @Nullable String platformId) {
        this(settings, platformId, () -> YoWhisperSpeech.builder().build());
    }

    /** 供单测替换传输层：真实实现要发一次 HTTP，断言的却是"包成 wav 了吗""错误怎么折"。 */
    WhisperSpeechRecognizer(
            @NonNull AiSettingsRepository settings,
            @Nullable String platformId,
            @NonNull Supplier<YoSpeech> speech) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.platformId = platformId == null || platformId.isBlank() ? null : platformId.trim();
        this.speech = Objects.requireNonNull(speech, "speech");
    }

    @NonNull
    @Override
    public String transcribe(@NonNull byte[] pcm16, int sampleRateHz, @Nullable String model)
            throws AiVoiceException {
        Objects.requireNonNull(pcm16, "pcm16");
        if (pcm16.length == 0) {
            return "";
        }
        if (sampleRateHz <= 0) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.BAD_REQUEST,
                    "转写的采样率不合法：" + sampleRateHz));
        }
        if (pcm16.length % (WavEncoder.BITS_PER_SAMPLE / 8) != 0) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.BAD_REQUEST,
                    "这段音频不是 16bit 采样，字节数对不上。"));
        }
        Target target = resolveTarget();
        byte[] wav;
        try {
            wav = WavEncoder.encode(pcm16, sampleRateHz, WavEncoder.CHANNELS);
        } catch (IllegalArgumentException unusable) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.BAD_REQUEST,
                    redact(unusable.getMessage(), target.apiKey), unusable));
        }
        YoTranscriptionRequest request = YoTranscriptionRequest.wav(wav, modelOf(model));
        try {
            YoTranscription result = speech().transcribe(target.baseUrl, target.apiKey, request);
            return result.text();
        } catch (YoAiException failure) {
            throw new AiVoiceException(fold(failure, target.apiKey));
        } catch (IllegalArgumentException unusable) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.BAD_REQUEST,
                    redact(unusable.getMessage(), target.apiKey), unusable));
        } catch (RuntimeException unexpected) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.UNKNOWN,
                    redact(unexpected.getMessage(), target.apiKey), unexpected));
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            if (created instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // 关闭连接池失败不影响已经结束的通话；下一次通话会新建一个实例。
                }
            }
            created = null;
        }
    }

    private synchronized YoSpeech speech() {
        if (closed) {
            throw new IllegalStateException("WhisperSpeechRecognizer is closed");
        }
        if (created == null) {
            created = speech.get();
        }
        return created;
    }

    private static String modelOf(@Nullable String model) {
        return model == null || model.isBlank() ? DEFAULT_TRANSCRIPTION_MODEL : model.trim();
    }

    /**
     * 找到这条转写该发往哪台主机：显式平台 → 当前选中的平台 → 第一个配好密钥的平台。
     */
    private Target resolveTarget() throws AiVoiceException {
        AiSettings current;
        try {
            current = settings.load();
        } catch (RuntimeException unreadable) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.STORAGE,
                    "读取 AI 设置失败，语音识别无法开始。", unreadable));
        }
        AiPlatformConfig platform = pick(current);
        String raw = platform.baseUrl();
        if (raw == null || raw.isBlank()) {
            raw = defaultBaseUrl(platform.templateId());
        }
        if (raw == null || raw.isBlank()) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.BAD_REQUEST,
                    "平台「" + platform.name() + "」还没有填写主机地址。"));
        }
        String baseUrl;
        try {
            // 先按平台规则收成主机基址：用户可能把 /v1 甚至整个 /chat/completions 粘进来。
            baseUrl = YoPlatformCatalog.normalizeBaseUrl(raw);
        } catch (IllegalArgumentException malformed) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.BAD_REQUEST,
                    redact(malformed.getMessage(), platform.apiKey()), malformed));
        }
        return new Target(baseUrl, platform.apiKey());
    }

    private AiPlatformConfig pick(AiSettings current) throws AiVoiceException {
        if (platformId != null) {
            Optional<AiPlatformConfig> chosen = current.platform(platformId);
            if (chosen.isEmpty()) {
                throw new AiVoiceException(new AiFailure(AiFailureKind.NO_PLATFORM,
                        "语音设置里指定的平台已经不在配置中（可能被删除了）。"));
            }
            if (!chosen.get().configured()) {
                throw new AiVoiceException(new AiFailure(AiFailureKind.NO_PLATFORM,
                        "平台「" + chosen.get().name() + "」还没有填 API Key。"));
            }
            return chosen.get();
        }
        Optional<AiActiveModel> active = current.activeModel();
        if (active.isPresent()) {
            return active.get().platform();
        }
        for (AiPlatformConfig platform : current.platforms()) {
            if (platform.configured()) {
                return platform;
            }
        }
        throw new AiVoiceException(new AiFailure(AiFailureKind.NO_PLATFORM,
                "还没有配置带 API Key 的 AI 平台，语音识别无法开始。"));
    }

    private static String defaultBaseUrl(String templateId) {
        YoPlatformDescriptor descriptor = YoPlatformCatalog.standard().find(templateId);
        return descriptor == null ? "" : descriptor.defaultBaseUrl();
    }

    /**
     * lib_ai 的错误分类只有一份（{@link YoAiErrors}），这里只做枚举改名；
     * 平台返回的说明可能回显密钥片段，进领域之前先抹掉。
     */
    private static AiFailure fold(Throwable error, @Nullable String apiKey) {
        return new AiFailure(kindOf(YoAiErrors.kindOf(error)), redact(error.getMessage(), apiKey), error);
    }

    private static AiFailureKind kindOf(YoAiErrorKind kind) {
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
            case UNSUPPORTED_CAPABILITY -> AiFailureKind.UNSUPPORTED_CAPABILITY;
            case PROTOCOL -> AiFailureKind.PROTOCOL;
            case UNKNOWN -> AiFailureKind.UNKNOWN;
        };
    }

    private static String redact(@Nullable String message, @Nullable String apiKey) {
        String clean = message == null ? "" : message;
        if (apiKey != null && apiKey.length() >= MIN_REDACTED_KEY) {
            clean = clean.replace(apiKey, "***");
        }
        return clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
    }

    /** 一次转写要用的主机与密钥，只在一次调用里活着。 */
    private record Target(String baseUrl, String apiKey) {
    }
}

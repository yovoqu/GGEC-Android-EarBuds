package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.policy.VoiceModelPolicy;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.AiVoiceModelGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用当前语音服务商的地址和 Key 拉取<b>能接实时会话</b>的模型，并写回设置。
 *
 * <p>网关只负责原样拉取；"哪些能用"由领域策略 {@link VoiceModelPolicy} 决定。
 * 类别随 SDK 规格原样记下。阻塞调用，不要在主线程执行。</p>
 */
public final class FetchVoiceModels {

    private final AiSettingsRepository repository;
    private final AiVoiceModelGateway gateway;
    private final VoiceModelPolicy policy;
    private final AiCredentialStore credentials;

    public FetchVoiceModels(AiSettingsRepository repository, AiVoiceModelGateway gateway,
                            AiCredentialStore credentials) {
        this(repository, gateway, new VoiceModelPolicy(), credentials);
    }

    public FetchVoiceModels(AiSettingsRepository repository, AiVoiceModelGateway gateway,
                            VoiceModelPolicy policy, AiCredentialStore credentials) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    public void execute() {
        AiVoiceConfig voice = repository.load().voice();
        AiVoiceModel target = voice.model();
        String apiKey = credentials.voiceKey(target);
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiFailure(AiFailureKind.VOICE_NOT_CONFIGURED, null);
        }
        List<AiModel> fetched = gateway.fetch(voice.effectiveBaseUrl(), apiKey);
        List<AiModel> usable = policy.realtimeOnly(fetched);
        // 写回发起请求的那一家：返回时用户可能已经切到别家。
        repository.update(settings -> settings.withVoice(
                settings.voice().withRealtimeModelsFor(target, usable)));
    }
}

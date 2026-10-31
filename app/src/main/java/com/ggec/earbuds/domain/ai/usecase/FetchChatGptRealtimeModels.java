package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiChatGptRealtimeGateway;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用当前 ChatGPT 的地址和 Key 拉取实时模型，并写回设置。
 * 阻塞调用，不要在主线程执行。
 */
public final class FetchChatGptRealtimeModels {

    private final AiSettingsRepository repository;
    private final AiChatGptRealtimeGateway gateway;

    public FetchChatGptRealtimeModels(AiSettingsRepository repository, AiChatGptRealtimeGateway gateway) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public void execute() {
        AiVoiceConfig voice = repository.load().voice();
        if (voice.model() != AiVoiceModel.CHATGPT) {
            throw new AiFailure(AiFailureKind.UNSUPPORTED_CAPABILITY, "当前只拉取 ChatGPT 的实时模型。");
        }
        if (!voice.configured()) {
            throw new AiFailure(AiFailureKind.VOICE_NOT_CONFIGURED, "先填写 ChatGPT 的 API Key，再拉取模型。");
        }
        List<AiModel> fetched = gateway.fetch(voice.effectiveBaseUrl(), voice.apiKey());
        List<String> ids = new ArrayList<>(fetched.size());
        for (AiModel model : fetched) {
            ids.add(model.id());
        }
        repository.update(settings -> settings.withVoice(settings.voice().withRealtimeModels(ids)));
    }
}

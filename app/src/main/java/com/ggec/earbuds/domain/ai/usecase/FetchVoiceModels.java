package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.AiVoiceModelGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用当前语音服务商的地址和 Key 拉取<b>全部</b>模型，并写回设置。
 *
 * <p>不再只留实时模型：平台没给能力位时，实时模型也会从"只看实时"的筛选里消失。
 * 全量拉回后由 {@code AiVoiceModelOption.realtime} 在界面标注，用户自己决定选哪一个。
 * 阻塞调用，不要在主线程执行。</p>
 */
public final class FetchVoiceModels {

    private final AiSettingsRepository repository;
    private final AiVoiceModelGateway gateway;

    public FetchVoiceModels(AiSettingsRepository repository, AiVoiceModelGateway gateway) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public void execute() {
        AiVoiceConfig voice = repository.load().voice();
        if (!voice.configured()) {
            throw new AiFailure(AiFailureKind.VOICE_NOT_CONFIGURED,
                    "先填写「" + voice.model().displayName() + "」的 API Key，再拉取模型。");
        }
        List<AiModel> fetched = gateway.fetch(voice.effectiveBaseUrl(), voice.apiKey());
        List<String> ids = new ArrayList<>(fetched.size());
        for (AiModel model : fetched) {
            ids.add(model.id());
        }
        repository.update(settings -> settings.withVoice(settings.voice().withRealtimeModels(ids)));
    }
}

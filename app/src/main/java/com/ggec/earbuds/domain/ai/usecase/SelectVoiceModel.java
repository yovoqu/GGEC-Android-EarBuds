package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 切换语音交流模式用的预设模型。
 *
 * <p>只改"选哪一家"，不动任何一家的 Key：用户从 ChatGPT 切到 Qwen 再切回来，
 * 原来填过的密钥仍然在。语音模型不经过平台，所以这里没有任何平台校验。</p>
 */
public final class SelectVoiceModel {

    private final AiSettingsRepository repository;

    public SelectVoiceModel(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(@NonNull AiVoiceModel model) {
        Objects.requireNonNull(model, "model");
        repository.update(settings -> settings.withVoice(settings.voice().withModel(model)));
    }
}

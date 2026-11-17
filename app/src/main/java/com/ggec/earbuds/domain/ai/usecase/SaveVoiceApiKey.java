package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 保存语音密钥：写密文与写"已配置"标记是同一件事。
 *
 * <p>两者都在 {@link AiSettingsRepository#saveVoiceKey} 的同一个提交点完成：密钥先落密文，
 * 设置文档随后落盘，任一步失败都回滚到写前的值。调用方不再自己调两次协作者。</p>
 */
public final class SaveVoiceApiKey {

    private final AiSettingsRepository settings;

    public SaveVoiceApiKey(AiSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public AiSettings execute(AiVoiceModel model, String apiKey) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
        return settings.saveVoiceKey(model, apiKey);
    }
}

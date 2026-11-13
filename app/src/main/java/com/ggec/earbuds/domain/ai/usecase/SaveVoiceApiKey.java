package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsChanges;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 保存语音密钥：写密文与写「已配置」标记是同一件事。
 *
 * <p>顺序是先落密文、再提交设置。设置仓库在提交时会按密钥库的真实存在情况重算
 * {@code configured}，所以两者不会长期分叉；界面也不再各自调用两个协作者。</p>
 */
public final class SaveVoiceApiKey {

    private final AiSettingsRepository settings;
    private final AiCredentialStore credentials;

    public SaveVoiceApiKey(AiSettingsRepository settings, AiCredentialStore credentials) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    public AiSettings execute(AiVoiceModel model, String apiKey) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
        credentials.putVoiceKey(model, apiKey);
        return settings.update(AiSettingsChanges.voiceApiKey(apiKey));
    }
}

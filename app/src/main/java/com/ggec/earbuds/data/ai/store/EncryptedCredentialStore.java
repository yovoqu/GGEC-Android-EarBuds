package com.ggec.earbuds.data.ai.store;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;

import java.util.Objects;

/**
 * {@link AiCredentialStore} 的加密实现：读走 {@link SecretStore} 的两套别名，
 * 文本平台用 {@code apikey.<platformId>}，语音用 {@code voicekey.<modelId>}。
 *
 * <p>写入不在这里：密钥必须与设置文档成对提交，那一对由 {@code AiSettingsRepositoryImpl}
 * 用同样的别名常量完成。别名是单点定义，读写共用。</p>
 */
public final class EncryptedCredentialStore implements AiCredentialStore {

    private static final String PLATFORM_PREFIX = "apikey.";
    private static final String VOICE_PREFIX = "voicekey.";

    private final SecretStore secrets;

    public EncryptedCredentialStore(@NonNull SecretStore secrets) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
    }

    /** 平台密钥在密文文档里的别名。单源：仓库写、网关读都从这里取。 */
    @NonNull
    public static String platformAlias(@NonNull String platformId) {
        return PLATFORM_PREFIX + platformId;
    }

    /** 语音模型密钥的别名。 */
    @NonNull
    public static String voiceAlias(@NonNull AiVoiceModel model) {
        return VOICE_PREFIX + model.id();
    }

    @Nullable
    @Override
    public String platformKey(@NonNull String platformId) {
        return blankToNull(secrets.get(platformAlias(platformId)));
    }

    @Nullable
    @Override
    public String voiceKey(@NonNull AiVoiceModel model) {
        return blankToNull(secrets.get(voiceAlias(model)));
    }

    @Override
    public boolean hasPlatformKey(@NonNull String platformId) {
        return platformKey(platformId) != null;
    }

    @Override
    public boolean hasVoiceKey(@NonNull AiVoiceModel model) {
        return voiceKey(model) != null;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

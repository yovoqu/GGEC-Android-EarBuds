package com.ggec.earbuds.data.ai.store;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;

import java.util.Objects;

/**
 * {@link AiCredentialStore} 的加密实现：复用 {@link SecretStore} 的两套别名，
 * 文本平台用 {@code apikey.<platformId>}，语音用 {@code voicekey.<modelId>}。
 */
public final class EncryptedCredentialStore implements AiCredentialStore {

    private static final String PLATFORM_PREFIX = "apikey.";
    private static final String VOICE_PREFIX = "voicekey.";

    private final SecretStore secrets;

    public EncryptedCredentialStore(@NonNull SecretStore secrets) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
    }

    /** 平台密钥在密文文档里的别名。单源：仓库与 UI 都从这里取，不再各写一份前缀。 */
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

    @Override
    public void putPlatformKey(@NonNull String platformId, @NonNull String key) {
        put(platformAlias(platformId), key);
    }

    @Override
    public void putVoiceKey(@NonNull AiVoiceModel model, @NonNull String key) {
        put(voiceAlias(model), key);
    }

    private void put(@NonNull String alias, @NonNull String key) {
        String trimmed = key == null ? "" : key.strip();
        if (trimmed.isEmpty()) {
            secrets.remove(alias);
        } else {
            secrets.put(alias, trimmed);
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;

import java.util.HashMap;
import java.util.Map;

/** 内存凭证仓库：测试里按需放各家的 Key。 */
public final class FakeCredentialStore implements AiCredentialStore {

    private final Map<String, String> platformKeys = new HashMap<>();
    private final Map<AiVoiceModel, String> voiceKeys = new HashMap<>();

    @Nullable
    @Override
    public String platformKey(@NonNull String platformId) {
        return platformKeys.get(platformId);
    }

    @Nullable
    @Override
    public String voiceKey(@NonNull AiVoiceModel model) {
        return voiceKeys.get(model);
    }

    @Override
    public boolean hasPlatformKey(@NonNull String platformId) {
        String key = platformKeys.get(platformId);
        return key != null && !key.isBlank();
    }

    @Override
    public boolean hasVoiceKey(@NonNull AiVoiceModel model) {
        String key = voiceKeys.get(model);
        return key != null && !key.isBlank();
    }

    @Override
    public void putPlatformKey(@NonNull String platformId, @NonNull String key) {
        put(platformKeys, platformId, key);
    }

    @Override
    public void putVoiceKey(@NonNull AiVoiceModel model, @NonNull String key) {
        put(voiceKeys, model, key);
    }

    @NonNull
    public FakeCredentialStore withVoiceKey(@NonNull AiVoiceModel model, @NonNull String key) {
        putVoiceKey(model, key);
        return this;
    }

    @NonNull
    public FakeCredentialStore withPlatformKey(@NonNull String platformId, @NonNull String key) {
        putPlatformKey(platformId, key);
        return this;
    }

    private static <K> void put(@NonNull Map<K, String> map, @NonNull K key, @Nullable String value) {
        if (value == null || value.isBlank()) {
            map.remove(key);
        } else {
            map.put(key, value.strip());
        }
    }
}

package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiVoiceModel;

/**
 * 密钥的唯一出入口。领域模型只保存"是否已配置"的事实，明文密钥通过这个端口按需取用：
 * 拉模型、建实时连接、发对话时临时取一次，用完不留在设置快照里。
 */
public interface AiCredentialStore {

    /** 文本平台的密钥；没有返回 {@code null}。 */
    @Nullable
    String platformKey(@NonNull String platformId);

    /** 语音服务商的密钥；没有返回 {@code null}。 */
    @Nullable
    String voiceKey(@NonNull AiVoiceModel model);

    /** 文本平台是否已配置密钥。 */
    boolean hasPlatformKey(@NonNull String platformId);

    /** 语音服务商是否已配置密钥。 */
    boolean hasVoiceKey(@NonNull AiVoiceModel model);

    /** 写入/清除文本平台密钥；空串表示删除。 */
    void putPlatformKey(@NonNull String platformId, @NonNull String key);

    /** 写入/清除语音服务商密钥；空串表示删除。 */
    void putVoiceKey(@NonNull AiVoiceModel model, @NonNull String key);
}

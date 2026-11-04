package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.port.AiVoiceModelGateway;
import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.catalog.YoPlatformCatalog;
import com.yovo.lib_ai.catalog.YoPlatformSettings;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;

import java.util.List;

/**
 * 按 APP 给出的地址和密钥向服务商拉 {@code /v1/models}，原样交回全部模型和 SDK 类别。
 * 这里不做筛选：实时语音要不要哪些模型，由领域策略决定。
 */
public final class LibAiVoiceModelGateway implements AiVoiceModelGateway {

    private static final String INSTANCE_ID = "voice-models";
    private static final long CONNECT_TIMEOUT_MILLIS = 15_000L;
    private static final long READ_TIMEOUT_MILLIS = 30_000L;
    private static final long WRITE_TIMEOUT_MILLIS = 30_000L;

    @Override
    public List<AiModel> fetch(String baseUrl, String apiKey) {
        YoPlatform platform = YoPlatformCatalog.standard().create(
                YoPlatformSettings.builder(OpenAiPlatform.ID)
                        .instanceId(INSTANCE_ID)
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .build());
        try (YoAiClient client = YoAiClient.builder()
                .platform(platform)
                .maxRetries(2)
                .connectTimeoutMillis(CONNECT_TIMEOUT_MILLIS)
                .readTimeoutMillis(READ_TIMEOUT_MILLIS)
                .writeTimeoutMillis(WRITE_TIMEOUT_MILLIS)
                .callbackExecutor(Runnable::run)
                .build()) {
            return LibAiModels.all(client.models().list());
        } catch (RuntimeException error) {
            throw LibAiFailures.map(error, apiKey);
        }
    }
}

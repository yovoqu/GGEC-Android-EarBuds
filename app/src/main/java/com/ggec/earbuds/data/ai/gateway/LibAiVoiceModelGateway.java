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
 * 按 APP 给出的地址和密钥向服务商拉 {@code /v1/models}，把<b>全部</b>模型交回。
 *
 * <p>旧实现用 {@code YoChatGptRealtime.offered(...)} 只留实时模型，结果官方 {@code /v1/models}
 * 里没有能力位、名字又不带 {@code realtime} 的实时模型会被静默丢掉，用户配好的模型在列表里
 * 找不到。现在不筛：文本、向量、实时各回各家，由界面按能力位标注，让用户自己挑。</p>
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

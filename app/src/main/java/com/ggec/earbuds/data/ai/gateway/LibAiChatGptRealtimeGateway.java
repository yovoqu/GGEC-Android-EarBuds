package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.port.AiChatGptRealtimeGateway;
import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.catalog.YoPlatformCatalog;
import com.yovo.lib_ai.catalog.YoPlatformSettings;
import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.realtime.YoChatGptRealtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 按 APP 给出的地址和密钥向 OpenAI 拉 {@code /v1/models}，再用 SDK 的
 * {@link YoChatGptRealtime} 留下接得上实时会话的模型。文本列表的过滤不走这里。
 */
public final class LibAiChatGptRealtimeGateway implements AiChatGptRealtimeGateway {

    private static final String INSTANCE_ID = "chatgpt-realtime";
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
            List<AiModel> models = new ArrayList<>();
            for (YoModelInfo info : YoChatGptRealtime.offered(client.models().list())) {
                models.add(new AiModel(info.id(), info.displayName(), null, null,
                        Set.of(AiModelFeature.REALTIME_AUDIO), false));
            }
            return List.copyOf(models);
        } catch (RuntimeException error) {
            throw LibAiFailures.map(error, apiKey);
        }
    }
}

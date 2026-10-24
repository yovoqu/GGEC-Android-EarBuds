package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiRole;
import com.ggec.earbuds.domain.ai.model.AiContentPart;
import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiChatGateway;
import com.ggec.earbuds.domain.ai.port.AiChatListener;
import com.ggec.earbuds.domain.ai.port.AiChatRequest;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.domain.ai.port.AiChatTurn;

import java.util.List;
import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.completion.YoCompletionEvent;
import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;
import com.yovo.lib_ai.completion.YoCompletionTurn;
import com.yovo.lib_ai.completion.YoCompletionUsage;
import com.yovo.lib_ai.error.YoAiException;

import java.util.Objects;

/**
 * 对话走 lib_ai 的统一补全：三条协议的差异、思考内容的拆分都已经在库里处理完，
 * 这里只翻译类型。租约在流结束、失败或被取消时归还，且只归还一次。
 */
public final class LibAiChatGateway implements AiChatGateway {

    private final LibAiClientPool pool;

    public LibAiChatGateway(LibAiClientPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public AiCancellable stream(AiChatRequest request, AiChatListener listener) {
        AiPlatformConfig platform = request.platform();
        LibAiClientPool.Lease lease = null;
        try {
            lease = pool.acquire(platform);
            LibAiClientPool.Lease held = lease;
            YoAiCall call = lease.client().completions(platform.id()).stream(toRequest(request),
                    new YoCompletionListener() {
                        @Override
                        public void onEvent(YoCompletionEvent event) {
                            if (event.type() == YoCompletionEvent.Type.TEXT) {
                                listener.onText(event.delta());
                            } else {
                                listener.onReasoning(event.delta());
                            }
                        }

                        @Override
                        public void onComplete(YoCompletionResult result) {
                            held.close();
                            listener.onComplete(toResult(result));
                        }

                        @Override
                        public void onFailure(YoAiException error) {
                            held.close();
                            listener.onFailure(LibAiFailures.map(error, platform.apiKey()));
                        }
                    });
            return () -> {
                call.cancel();
                held.close();
            };
        } catch (RuntimeException error) {
            if (lease != null) {
                lease.close();
            }
            throw LibAiFailures.map(error, platform.apiKey());
        }
    }

    static YoCompletionRequest toRequest(AiChatRequest request) {
        YoCompletionRequest.Builder builder = YoCompletionRequest.builder()
                .model(request.modelId())
                .protocol(LibAiPlatformCatalog.protocol(request.platform().protocol()))
                .system(request.systemPrompt())
                .temperature(request.temperature())
                .topP(request.topP())
                .maxOutputTokens(request.maxOutputTokens());
        for (AiChatTurn turn : request.turns()) {
            // 出站内容一律先表达成"部件列表"，再压平成平台要的纯文本。
            // 现在只会有一个 Text 部件（相册还没开放），将来的图片/音频只需要在这里多塞一个部件，
            // 上层与端口都不用动——这就是留这个抽象的全部意义。
            List<AiContentPart> parts = List.of(new AiContentPart.Text(turn.text()));
            builder.addTurn(YoCompletionTurn.of(
                    turn.role() == AiRole.USER ? YoCompletionTurn.Role.USER : YoCompletionTurn.Role.ASSISTANT,
                    AiContentPart.flatten(parts)));
        }
        return builder.build();
    }

    static AiChatResult toResult(YoCompletionResult result) {
        YoCompletionUsage usage = result.usage();
        AiFinishReason finish = switch (result.finishReason()) {
            case STOP -> AiFinishReason.STOP;
            case LENGTH -> AiFinishReason.LENGTH;
            case FILTERED -> AiFinishReason.FILTERED;
            case OTHER -> AiFinishReason.OTHER;
        };
        return new AiChatResult(result.text(), result.reasoning(), finish,
                usage == null ? null : new AiUsage(
                        usage.inputTokens(),
                        usage.outputTokens(),
                        usage.totalTokens(),
                        usage.cost(),
                        usage.cachedInputTokens(),
                        usage.cacheCreationInputTokens()));
    }
}

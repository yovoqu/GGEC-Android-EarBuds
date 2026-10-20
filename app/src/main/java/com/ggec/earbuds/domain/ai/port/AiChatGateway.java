package com.ggec.earbuds.domain.ai.port;

public interface AiChatGateway {

    /**
     * 立即返回。请求不合法时同步抛 {@link com.ggec.earbuds.domain.ai.model.AiFailure}，
     * 其余结果经监听器异步送达。
     */
    AiCancellable stream(AiChatRequest request, AiChatListener listener);
}

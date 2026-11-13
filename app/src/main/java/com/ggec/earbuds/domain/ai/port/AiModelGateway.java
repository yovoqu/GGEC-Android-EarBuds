package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;

import java.util.List;

public interface AiModelGateway {

    /**
     * 向平台拉取当前账号的模型列表，阻塞调用，不要在主线程执行。
     *
     * <p><b>不筛选</b>：平台返回什么就交回什么；哪些模型能用于文本对话由
     * {@link AiPlatformConfig#chatModels()} 决定。失败抛
     * {@link com.ggec.earbuds.domain.ai.model.AiFailure}。</p>
     */
    List<AiModel> fetch(AiPlatformConfig platform);
}

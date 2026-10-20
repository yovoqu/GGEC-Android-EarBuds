package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;

import java.util.List;

public interface AiModelGateway {

    /**
     * 向平台拉取当前账号可用的对话模型，阻塞调用，不要在主线程执行。
     * 非对话模型（向量、图片、语音等）已被过滤。失败抛 {@link com.ggec.earbuds.domain.ai.model.AiFailure}。
     */
    List<AiModel> fetch(AiPlatformConfig platform);
}

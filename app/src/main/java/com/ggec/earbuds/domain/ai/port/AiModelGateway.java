package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;

import java.util.List;

public interface AiModelGateway {

    /**
     * 向平台拉取当前账号可用的全部模型（含向量、图像、语音等，带 {@code kind}），保持接口返回顺序。
     * 阻塞调用，不要在主线程执行。失败抛 {@link com.ggec.earbuds.domain.ai.model.AiFailure}。
     */
    List<AiModel> fetch(AiPlatformConfig platform);
}

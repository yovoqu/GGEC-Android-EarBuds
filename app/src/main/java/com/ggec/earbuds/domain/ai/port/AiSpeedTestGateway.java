package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;

import java.util.List;

public interface AiSpeedTestGateway {

    /**
     * 立即返回。参数不合法时同步抛 {@link com.ggec.earbuds.domain.ai.model.AiFailure}，
     * 其余结果经监听器异步送达。
     */
    AiCancellable run(AiPlatformConfig platform, List<String> modelIds, AiSpeedTestListener listener);
}

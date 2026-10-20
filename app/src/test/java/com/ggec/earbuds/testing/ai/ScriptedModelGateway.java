package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.port.AiModelGateway;

import java.util.List;

/** 拉取模型的假网关：返回测试设定的列表，或者抛出设定的失败，并数着被调用了几次。 */
public final class ScriptedModelGateway implements AiModelGateway {

    public List<AiModel> models = List.of();
    public AiFailure failure;
    public int fetches;

    @Override
    public List<AiModel> fetch(AiPlatformConfig platform) {
        fetches++;
        if (failure != null) {
            throw failure;
        }
        return models;
    }
}

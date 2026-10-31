package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 为文本模式选定模型。平台必须已配置密钥，模型必须在该平台的列表里。
 *
 * <p>这里只处理文本：语音模型是 APP 预设的一项能力，由 {@link SelectVoiceModel} 负责，
 * 不经过平台，也不写进同一个选择字段。</p>
 */
public final class SelectModel {

    private final AiSettingsRepository repository;

    public SelectModel(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(String platformId, String modelId) {
        repository.update(settings -> {
            AiPlatformConfig platform = PlatformLookup.configured(settings, platformId);
            AiModel model = platform.model(modelId)
                    .orElseThrow(() -> new AiValidationException(AiValidationError.MODEL_UNKNOWN));
            if (model.realtime()) {
                // 实时模型接不上 Chat Completions；在这里拦住，错误落在用户刚做的动作上。
                throw new AiValidationException(AiValidationError.REALTIME_MODEL_NOT_CHAT);
            }
            return settings.withTextSelection(new AiSelection(platformId, modelId));
        });
    }
}

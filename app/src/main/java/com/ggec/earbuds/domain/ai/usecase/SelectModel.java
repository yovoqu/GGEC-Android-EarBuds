package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 为某一种接入形态选定模型。平台必须已配置密钥，模型必须在该平台的列表里。
 *
 * <p>语音交流模式只接受实时模型：选中一个普通对话模型，通话页会连不上，
 * 而且失败要等到拨号之后才暴露。在这里拦住，错误就落在用户刚做的动作上。</p>
 */
public final class SelectModel {

    private final AiSettingsRepository repository;

    public SelectModel(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(@NonNull AiInteractionMode mode, String platformId, String modelId) {
        repository.update(settings -> {
            AiPlatformConfig platform = PlatformLookup.configured(settings, platformId);
            AiModel model = platform.model(modelId)
                    .orElseThrow(() -> new AiValidationException(AiValidationError.MODEL_UNKNOWN));
            if (mode == AiInteractionMode.VOICE && !model.realtime()) {
                throw new AiValidationException(AiValidationError.VOICE_NEEDS_REALTIME_MODEL);
            }
            return settings.withSelection(mode, new AiSelection(platformId, modelId));
        });
    }
}

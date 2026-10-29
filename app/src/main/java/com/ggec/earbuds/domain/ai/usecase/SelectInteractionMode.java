package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 切换设置页正在配置的接入形态。
 *
 * <p>只是记住"上次停在哪个分段"，不校验另一模式有没有选模型：用户可以在语音模式里
 * 先切过去再慢慢配。真正能不能对话由对话页与通话页各自判定。</p>
 */
public final class SelectInteractionMode {

    private final AiSettingsRepository repository;

    public SelectInteractionMode(AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(@NonNull AiInteractionMode mode) {
        Objects.requireNonNull(mode, "mode");
        repository.update(settings -> settings.withMode(mode));
    }
}

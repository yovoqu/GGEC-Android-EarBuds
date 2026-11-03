package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 把一组已经由界面确认过的纯变更写进设置仓库。
 *
 * <p>设置页先在内存里落地并重绘，再在一个节流窗口后把合并后的变更交给这里一次性写盘；
 * 用户连点多个开关不会产生多次"整份文档写入"。</p>
 */
public final class SaveAiSettings {

    private final AiSettingsRepository repository;

    public SaveAiSettings(@NonNull AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void execute(@NonNull UnaryOperator<AiSettings> change) {
        repository.update(Objects.requireNonNull(change, "change"));
    }
}

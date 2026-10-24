package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把一套生成参数批量写进模型的参数覆盖表。
 *
 * <p>为什么需要这个用例：R-11 的三级取值里，只有"模型参数"是落盘的，
 * "这套值对谁生效"在数据上就等于"哪些模型有覆盖"。所以界面上的"批量应用到全部模型"
 * 就是给目标模型逐个写 {@link AiModelParams}——写完它们各自固定、不再跟随全局，
 * 这正是 {@link com.ggec.earbuds.domain.ai.policy.AiParamsPolicy} 那条
 * "模型被单独设过就不再被全局覆盖"的规则。</p>
 *
 * <p>参数为空（{@link AiModelParams#EMPTY}）时什么都不写，返回 0；
 * 界面据此提示"没有可应用的值"，而不是往磁盘上写一片空覆盖。</p>
 */
public final class ApplyModelParams {

    /** 没有写入任何模型：参数为空，或目标平台一个模型都没有。 */
    public static final int NOTHING_APPLIED = 0;

    private final AiSettingsRepository repository;

    public ApplyModelParams(@NonNull AiSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * @param params     要写入的那套值；空值直接忽略
     * @param platformId 目标平台；{@code null} 表示本机所有平台
     * @return 实际写入的模型数；0 表示什么都没发生
     */
    public int execute(@NonNull AiModelParams params, @Nullable String platformId) {
        Objects.requireNonNull(params, "params");
        if (params.isEmpty()) {
            return NOTHING_APPLIED;
        }
        AtomicInteger written = new AtomicInteger();
        repository.update(settings -> {
            written.set(0);
            List<AiPlatformConfig> next = new ArrayList<>(settings.platforms().size());
            int count = 0;
            for (AiPlatformConfig platform : settings.platforms()) {
                if (platformId != null && !platform.id().equals(platformId)) {
                    next.add(platform);
                    continue;
                }
                AiPlatformConfig updated = platform;
                for (AiModel model : platform.models()) {
                    updated = updated.withModelParams(model.id(), params);
                    count++;
                }
                next.add(updated);
            }
            if (count == 0) {
                // 没有可写的模型：返回原设置，仓库据此不通知观察者。
                return settings;
            }
            AiSettings result = settings;
            for (AiPlatformConfig platform : next) {
                result = result.withPlatform(platform);
            }
            written.set(count);
            return result;
        });
        return written.get();
    }
}

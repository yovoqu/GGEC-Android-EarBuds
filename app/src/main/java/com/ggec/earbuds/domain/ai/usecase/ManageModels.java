package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFetchedModels;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiModelDraftPolicy;
import com.ggec.earbuds.domain.ai.policy.AiParamsPolicy;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiModelGateway;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 平台页模型区的全部写操作（MOD-06…12、PLT-06、GEN-03）。每个方法一次原子读-改-写。
 */
public final class ManageModels {

    private final AiSettingsRepository repository;
    private final AiModelGateway gateway;
    private final AiClock clock;

    public ManageModels(AiSettingsRepository repository, AiModelGateway gateway, AiClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 拉取模型（MOD-06/07）。阻塞调用。成功时：已添加但远端没有的模型（手动添加的除外）标"已下线"，
     * 再次出现的清除标记，并按远端信息补全显示名/上下文/类型；不自动添加任何模型。失败抛 AiFailure。
     */
    public AiFetchedModels fetch(String platformId) {
        AiPlatformConfig platform = existing(repository.load(), platformId);
        List<AiModel> remote = gateway.fetch(platform);
        Set<String> remoteIds = new HashSet<>();
        for (AiModel model : remote) {
            remoteIds.add(model.id());
        }
        long fetchedAt = clock.now();
        AtomicReference<Set<String>> added = new AtomicReference<>(Set.of());
        repository.update(settings -> settings.platform(platformId).map(current -> {
            List<AiModel> next = new ArrayList<>();
            Set<String> ids = new LinkedHashSet<>();
            for (AiModel model : current.models()) {
                ids.add(model.id());
                if (model.manual()) {
                    next.add(model);
                    continue;
                }
                AiModel fresh = find(remote, model.id());
                next.add(fresh == null ? model.withRemoteMissing(true)
                        : model.withRemoteMissing(false).withDetails(
                                fresh.hasDisplayName() ? fresh.name() : model.name(),
                                fresh.contextWindow() != null ? fresh.contextWindow() : model.contextWindow(),
                                fresh.kind()));
            }
            added.set(ids);
            return settings.withPlatform(current.withModels(next, fetchedAt));
        }).orElse(settings));
        return new AiFetchedModels(platformId, remote, added.get());
    }

    /** 拉取面板里勾选的模型按接口顺序追加（已存在的跳过）。 */
    public void addFetched(String platformId, Collection<AiModel> picked) {
        if (picked.isEmpty()) {
            return;
        }
        update(platformId, (settings, platform) -> {
            List<AiModel> fresh = new ArrayList<>();
            for (AiModel model : picked) {
                fresh.add(model.withRemoteMissing(false));
            }
            return settings.withPlatform(platform.plusModels(fresh));
        });
    }

    /** 手动添加（MOD-08）。不自动测速。 */
    public AiModel addManual(String platformId, AiModelDraftPolicy.Draft draft) {
        AtomicReference<AiModel> created = new AtomicReference<>();
        update(platformId, (settings, platform) -> {
            requireValid(draft, platform, null);
            AiModel model = AiModelDraftPolicy.toModel(draft, null);
            created.set(model);
            return settings.withPlatform(platform.plusModels(List.of(model)));
        });
        return created.get();
    }

    /** 编辑（MOD-09）：ID 只读。改成非对话类型时，若它是当前模型则当前模型置空。 */
    public void edit(String platformId, String modelId, AiModelDraftPolicy.Draft draft) {
        update(platformId, (settings, platform) -> {
            AiModel original = platform.model(modelId)
                    .orElseThrow(() -> new AiValidationException(AiValidationError.MODEL_UNKNOWN));
            requireValid(draft, platform, modelId);
            AiModel edited = AiModelDraftPolicy.toModel(draft, original);
            AiSettings next = settings.withPlatform(platform.replaceModel(edited));
            if (!edited.chat() && settings.isCurrent(platformId, modelId)) {
                next = next.withSelection(null);
            }
            return next;
        });
    }

    /**
     * 删除（MOD-11）：模型、测速结果、单模型参数一起删；含当前模型时当前模型置空。
     *
     * @return 删除的是否包含当前模型
     */
    public boolean remove(String platformId, Set<String> modelIds) {
        if (modelIds.isEmpty()) {
            return false;
        }
        AtomicReference<Boolean> includedCurrent = new AtomicReference<>(false);
        update(platformId, (settings, platform) -> {
            AiSelection selection = settings.selection();
            boolean current = selection != null && selection.platformId().equals(platformId)
                    && modelIds.contains(selection.modelId());
            includedCurrent.set(current);
            AiSettings next = settings.withPlatform(platform.withoutModels(modelIds));
            return current ? next.withSelection(null) : next;
        });
        return includedCurrent.get();
    }

    /** 测速失败的模型（MOD-12）。只看对话模型的现有结果。 */
    public static Set<String> failedSpeed(AiPlatformConfig platform) {
        Set<String> failed = new LinkedHashSet<>();
        for (AiModel model : platform.chatModels()) {
            AiSpeedResult result = platform.speedResults().get(model.id());
            if (result != null && !result.ok()) {
                failed.add(model.id());
            }
        }
        return failed;
    }

    /** 设为当前（PLT-06）：只有对话模型可以。 */
    public void select(String platformId, String modelId) {
        update(platformId, (settings, platform) -> {
            AiModel model = platform.model(modelId)
                    .orElseThrow(() -> new AiValidationException(AiValidationError.MODEL_UNKNOWN));
            if (!model.chat()) {
                throw new AiValidationException(AiValidationError.MODEL_NOT_CHAT);
            }
            return settings.withSelection(new AiSelection(platformId, modelId));
        });
    }

    /**
     * 单模型生成参数（GEN-03）。每项为空＝清除此模型设置（跟随全局/平台默认）。
     * 越界抛 {@link AiValidationError#PARAM_OUT_OF_RANGE}。最大输出的范围按模型；温度按协议取 0–2，
     * 超出当前协议的部分发送时裁剪（GEN-05），不拒绝。
     */
    public void setParams(String platformId, String modelId, AiModelParams params) {
        update(platformId, (settings, platform) -> {
            AiModel model = platform.model(modelId)
                    .orElseThrow(() -> new AiValidationException(AiValidationError.MODEL_UNKNOWN));
            if (!model.chat()) {
                throw new AiValidationException(AiValidationError.MODEL_NOT_CHAT);
            }
            requireRange(params.temperature(), new AiParamsPolicy.Range(0, 2));
            requireRange(params.topP(), AiParamsPolicy.range(AiParamsPolicy.Param.TOP_P, platform.protocol(), model));
            requireRange(params.maxOutputTokens() == null ? null : params.maxOutputTokens().doubleValue(),
                    AiParamsPolicy.range(AiParamsPolicy.Param.MAX_OUTPUT, platform.protocol(), model));
            return settings.withPlatform(platform.withModelParams(modelId, params));
        });
    }

    private static void requireRange(@Nullable Double value, AiParamsPolicy.Range range) {
        if (value != null && !range.contains(value)) {
            throw new AiValidationException(AiValidationError.PARAM_OUT_OF_RANGE);
        }
    }

    private static void requireValid(AiModelDraftPolicy.Draft draft, AiPlatformConfig platform,
                                     @Nullable String editingId) {
        AiModelDraftPolicy.Error error = AiModelDraftPolicy.validate(draft, platform, editingId);
        if (error != null) {
            throw new AiValidationException(switch (error) {
                case ID_EMPTY -> AiValidationError.MODEL_ID_EMPTY;
                case ID_INVALID -> AiValidationError.MODEL_ID_INVALID;
                case ID_DUPLICATE -> AiValidationError.MODEL_ID_DUPLICATE;
                case CONTEXT_INVALID -> AiValidationError.CONTEXT_INVALID;
            });
        }
    }

    @Nullable
    private static AiModel find(List<AiModel> models, String id) {
        for (AiModel model : models) {
            if (model.id().equals(id)) {
                return model;
            }
        }
        return null;
    }

    private static AiPlatformConfig existing(AiSettings settings, String platformId) {
        return settings.platform(platformId)
                .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
    }

    private interface Change {
        AiSettings apply(AiSettings settings, AiPlatformConfig platform);
    }

    private void update(String platformId, Change change) {
        repository.update(settings -> change.apply(settings, existing(settings, platformId)));
    }
}

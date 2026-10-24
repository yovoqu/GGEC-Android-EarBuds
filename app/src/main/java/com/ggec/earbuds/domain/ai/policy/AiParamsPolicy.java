package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
/**
 * 生成参数的三级解析：模型 → 全局 → 默认（不发送）。
 *
 * <p>规则只有一条，但必须能被验证：<b>模型被单独设过就不再被全局覆盖</b>。
 * 否则用户改了全局，之前精调过的模型会跟着变，而他不知道为什么。</p>
 */
public final class AiParamsPolicy {

    /** 一个参数最终从哪来。界面要把它写出来，否则用户改了全局没生效会当成 bug。 */
    public enum Source {
        MODEL,
        GLOBAL,
        DEFAULT
    }

    public record Resolved(@Nullable Double temperature, @Nullable Double topP,
                           @Nullable Integer maxOutputTokens, @NonNull Source source) {
    }

    private AiParamsPolicy() {
    }

    /**
     * 把"这个模型现在真正生效的那套值"取出来，用于批量应用（写进别的模型）。
     *
     * <p>为什么要 snapshot 而不是直接拿全局三值：生效值可能来自模型自己的覆盖。
     * 界面上那句"把当前生效的值写进全部模型"，说的就是这个快照。</p>
     *
     * <p>模型或平台为空、或者生效来源就是"默认"（什么都没设）时，返回 {@link AiModelParams#EMPTY}：
     * 批量应用一个空参数等于什么都没给，调用方应当据此提示"没有可应用的值"，而不是写一堆空覆盖。</p>
     */
    @NonNull
    public static AiModelParams snapshot(@Nullable AiModel model, @Nullable AiPlatformConfig platform,
                                         @NonNull AiChatPreferences preferences) {
        if (model != null && platform != null) {
            Resolved resolved = resolve(model, platform, preferences);
            if (resolved.source() != Source.DEFAULT) {
                return new AiModelParams(resolved.temperature(), resolved.topP(), resolved.maxOutputTokens());
            }
            return AiModelParams.EMPTY;
        }
        // 没有选中模型时退回全局三值：设置页在没有选中模型的平台上也应当能"把全局值推下去"。
        if (!preferences.globalParamsEnabled()) {
            return AiModelParams.EMPTY;
        }
        AiModelParams global = new AiModelParams(preferences.temperature(), preferences.topP(),
                preferences.maxOutputTokens());
        return global.isEmpty() ? AiModelParams.EMPTY : global;
    }

    @NonNull
    public static Resolved resolve(@NonNull AiModel model, @NonNull AiPlatformConfig platform,
                                   @NonNull AiChatPreferences preferences) {
        AiModelParams own = platform.modelParams().get(model.id());
        if (own != null && !own.isEmpty()) {
            return new Resolved(own.temperature(), own.topP(), own.maxOutputTokens(), Source.MODEL);
        }
        // 开关关着时，全局滑杆里的值一律不参与——这正是"全局默认关闭"的含义。
        boolean globalUsable = preferences.globalParamsEnabled();
        if (globalUsable && (preferences.temperature() != null || preferences.topP() != null
                || preferences.maxOutputTokens() != null)) {
            return new Resolved(preferences.temperature(), preferences.topP(),
                    preferences.maxOutputTokens(), Source.GLOBAL);
        }
        return new Resolved(null, null, null, Source.DEFAULT);
    }
}
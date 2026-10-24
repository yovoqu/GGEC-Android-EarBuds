package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiConnectionResult;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;

import java.util.List;

/**
 * 连接状态的规则（CON-01…06）：前置条件、测试模型、状态判定、失败指向。
 */
public final class AiConnectionPolicy {

    /** 不能测试的原因，按优先级从高到低。 */
    public enum Blocker {
        NO_API_KEY,
        NO_BASE_URL,
        BAD_BASE_URL,
        NO_MODEL,
        OFFLINE
    }

    public enum Status {
        UNTESTED,
        OK,
        FAILED,
        STALE
    }

    /** 失败时要用户去改的那一行。 */
    public enum Target {
        API_KEY,
        TEST_MODEL,
        BASE_URL,
        PROTOCOL,
        NONE
    }

    private AiConnectionPolicy() {
    }

    /**
     * @param baseUrlRequired 自定义模板必须填接口地址
     * @param baseUrlValid    当前接口地址能否被规范化（为空时传 true）
     */
    @Nullable
    public static Blocker blocker(AiPlatformConfig platform, boolean baseUrlRequired, boolean baseUrlValid,
                                  boolean online) {
        if (!platform.configured()) {
            return Blocker.NO_API_KEY;
        }
        boolean emptyUrl = platform.baseUrl() == null || platform.baseUrl().isBlank();
        if (baseUrlRequired && emptyUrl) {
            return Blocker.NO_BASE_URL;
        }
        if (!emptyUrl && !baseUrlValid) {
            return Blocker.BAD_BASE_URL;
        }
        if (platform.chatModels().isEmpty()) {
            return Blocker.NO_MODEL;
        }
        if (!online) {
            return Blocker.OFFLINE;
        }
        return null;
    }

    /** CON-03：用户选的 → 当前模型（属于本平台）→ 第一个对话模型。都没有返回空。 */
    @Nullable
    public static String testModel(AiPlatformConfig platform, @Nullable AiSelection selection) {
        String chosen = platform.testModelId();
        if (chosen != null && isChat(platform, chosen)) {
            return chosen;
        }
        if (selection != null && selection.platformId().equals(platform.id())
                && isChat(platform, selection.modelId())) {
            return selection.modelId();
        }
        List<AiModel> chat = platform.chatModels();
        return chat.isEmpty() ? null : chat.get(0).id();
    }

    public static Status status(AiPlatformConfig platform, @Nullable AiSelection selection) {
        AiConnectionResult result = platform.connection();
        if (result == null) {
            return Status.UNTESTED;
        }
        String current = AiFingerprint.connection(platform, testModel(platform, selection));
        if (!current.equals(result.fingerprint())) {
            return Status.STALE;
        }
        return result.ok() ? Status.OK : Status.FAILED;
    }

    public static Target target(@Nullable AiFailureKind kind) {
        if (kind == null) {
            return Target.NONE;
        }
        return switch (kind) {
            case AUTHENTICATION -> Target.API_KEY;
            case PERMISSION -> Target.TEST_MODEL;
            case NOT_FOUND -> Target.BASE_URL;
            case BAD_REQUEST, PROTOCOL -> Target.PROTOCOL;
            default -> Target.NONE;
        };
    }

    private static boolean isChat(AiPlatformConfig platform, String modelId) {
        return platform.model(modelId).map(AiModel::chat).orElse(false);
    }
}

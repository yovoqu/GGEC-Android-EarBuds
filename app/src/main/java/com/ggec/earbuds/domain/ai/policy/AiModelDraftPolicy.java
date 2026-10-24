package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;

import java.util.Set;

/**
 * 手动添加/编辑模型面板的校验（MOD-08/09）：ID 必填、去空白、本平台内不重复；显示名可选；
 * 上下文可选、正整数、单位 K（1K＝1,000，与显示规则一致）；类型默认对话。
 */
public final class AiModelDraftPolicy {

    public enum Error {
        ID_EMPTY,
        ID_INVALID,
        ID_DUPLICATE,
        CONTEXT_INVALID
    }

    /** 面板里的原始输入。{@code contextK} 是用户填的 K 数（文本）。 */
    public record Draft(String id, String name, String contextK, AiModelKind kind) {
        public Draft {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            contextK = contextK == null ? "" : contextK;
            kind = kind == null ? AiModelKind.CHAT : kind;
        }

        public static Draft of(AiModel model) {
            String context = model.contextWindow() == null ? ""
                    : trimZeros(model.contextWindow() / 1000d);
            return new Draft(model.id(), model.hasDisplayName() ? model.name() : "", context, model.kind());
        }
    }

    private static final int MAX_CONTEXT_K = 100_000;

    private AiModelDraftPolicy() {
    }

    /**
     * @param editingId 编辑时为原 ID（ID 只读，不查重）；添加时为空
     * @return 第一个错误；合法返回空
     */
    @Nullable
    public static Error validate(Draft draft, AiPlatformConfig platform, @Nullable String editingId) {
        String id = draft.id().strip();
        if (editingId == null) {
            if (id.isEmpty()) {
                return Error.ID_EMPTY;
            }
            if (!validId(id)) {
                return Error.ID_INVALID;
            }
            if (platform.model(id).isPresent()) {
                return Error.ID_DUPLICATE;
            }
        }
        if (parseContext(draft.contextK()) == INVALID) {
            return Error.CONTEXT_INVALID;
        }
        return null;
    }

    /** 已校验通过的草稿转成模型。编辑时保留原模型的能力位、最大输出与下线标记。 */
    public static AiModel toModel(Draft draft, @Nullable AiModel original) {
        String id = original != null ? original.id() : draft.id().strip();
        String name = draft.name().strip();
        long context = parseContext(draft.contextK());
        Integer window = context <= 0 ? null : (int) context;
        if (original == null) {
            return new AiModel(id, name.isEmpty() ? id : name, window, null, Set.of(), true, draft.kind(), false);
        }
        return new AiModel(id, name.isEmpty() ? id : name, window, original.maxOutputTokens(),
                original.features(), original.manual(), draft.kind(), original.remoteMissing());
    }

    private static final long INVALID = -2L;

    /** 空 → 0（未填）；合法 → token 数；不合法 → {@link #INVALID}。允许一位小数，如 1.5 → 1,500。 */
    static long parseContext(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return 0L;
        }
        if (value.endsWith("K") || value.endsWith("k")) {
            value = value.substring(0, value.length() - 1).strip();
        }
        try {
            double k = Double.parseDouble(value);
            if (!(k > 0) || k > MAX_CONTEXT_K || Double.isNaN(k)) {
                return INVALID;
            }
            long tokens = Math.round(k * 1000d);
            return tokens <= 0 ? INVALID : tokens;
        } catch (NumberFormatException notNumber) {
            return INVALID;
        }
    }

    private static boolean validId(String id) {
        if (id.length() > 200) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    private static String trimZeros(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        String text = String.format(java.util.Locale.ROOT, "%.3f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}

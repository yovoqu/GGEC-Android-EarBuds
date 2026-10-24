package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiMessage;

/**
 * 对话页速度（SPD-A）：首字之后按输出 token 计算。生成中只能估算，结束后有平台回报就用准确值。
 */
public final class AiSpeedPolicy {

    /** 生成中至少过了这么久才显示实时速度，避免开头几个字算出离谱的数。 */
    public static final long LIVE_MIN_MILLIS = 500L;
    static final int MIN_TOKENS = 2;
    static final long MIN_GENERATION_MILLIS = 100L;

    /** 速度值；{@code estimated} 为真时界面加"≈"。 */
    public record Speed(int tokensPerSecond, boolean estimated) {
    }

    private AiSpeedPolicy() {
    }

    /** 粗估 token：中日韩字符每字 1 个，其余字符每 4 个 1 个。 */
    public static int estimateTokens(@Nullable CharSequence text) {
        if (text == null) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + (other + 3) / 4;
    }

    /**
     * 生成中的实时速度。
     *
     * @param sinceFirstTokenMillis 现在距首字的毫秒数
     */
    @Nullable
    public static Speed live(int estimatedTokens, long sinceFirstTokenMillis) {
        if (sinceFirstTokenMillis < LIVE_MIN_MILLIS || estimatedTokens <= 0) {
            return null;
        }
        return new Speed((int) Math.round(estimatedTokens * 1000d / sinceFirstTokenMillis), true);
    }

    /** 一条已结束回答的平均速度；数据不够时返回空。 */
    @Nullable
    public static Speed average(AiMessage message) {
        if (!message.hasFirstToken() || message.streaming()) {
            return null;
        }
        long generation = message.elapsedMillis() - message.firstTokenMillis();
        if (generation < MIN_GENERATION_MILLIS) {
            return null;
        }
        int reported = message.usage() == null ? 0 : message.usage().outputTokens();
        boolean estimated = reported <= 0;
        int tokens = estimated
                ? estimateTokens(message.text()) + estimateTokens(message.reasoning())
                : reported;
        if (tokens < MIN_TOKENS) {
            return null;
        }
        return new Speed((int) Math.round(tokens * 1000d / generation), estimated);
    }

    static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}

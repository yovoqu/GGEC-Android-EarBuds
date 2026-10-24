package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.List;

/**
 * 一行里"还没解析成字符串"的文案。
 *
 * <p>Mapper 是纯函数、拿不到 {@link Context}，所以它只决定<b>用哪条资源、带什么参数</b>，
 * 由页面在最后一刻 {@link #resolve(Context)} 成字符串。{@link #raw(String)} 用于用户自己输入的文本
 * （例如系统提示词的预览），那部分不需要也不应该走资源。</p>
 *
 * <p>为什么不用"直接返回 {@code @StringRes int} + 可选参数"：尾部值里既有需要占位符的
 * （"2 个模型"、"费用（8 次上报）"），也有纯用户文本；两种混在一个 ArrayList 里会让页面到处判空。</p>
 */
public record AiText(@StringRes int res, List<String> arguments, @Nullable String raw) {

    public AiText {
        arguments = List.copyOf(arguments);
    }

    @NonNull
    public static AiText of(@StringRes int res) {
        return new AiText(res, List.of(), null);
    }

    @NonNull
    public static AiText of(@StringRes int res, @NonNull String... arguments) {
        return new AiText(res, List.of(arguments), null);
    }

    @NonNull
    public static AiText raw(@NonNull String text) {
        return new AiText(0, List.of(), text);
    }

    public boolean isRaw() {
        return raw != null;
    }

    /** 解析成可以直接放进原生 TextView 的字符串。 */
    @NonNull
    public String resolve(@NonNull Context context) {
        if (raw != null) {
            return raw;
        }
        if (arguments.isEmpty()) {
            return context.getString(res);
        }
        return context.getString(res, arguments.toArray());
    }
}

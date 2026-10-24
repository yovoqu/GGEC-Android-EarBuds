package com.ggec.earbuds.ui.ai.history;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.List;

/**
 * 历史面板里"还没解析成字符串"的一行文案。
 *
 * <p>{@link AiHistoryUiMapper} 是纯函数、拿不到 {@link Context}，所以它只决定<b>用哪条资源、带什么参数</b>，
 * 由面板在最后一刻 {@link #resolve(Context)} 成字符串。{@link #raw(String)} 留给用户自己写的内容
 * （对话标题、摘要），那部分不需要也不该走资源。</p>
 *
 * <p>为什么不让 Mapper 直接返回 {@code String}：兜底文案（「未命名对话」「还没有内容」）必须能被翻译，
 * 而相对时间的参数（几分钟前、几时几分）也只有交给资源才能本地化。把"选哪条 + 什么参数"与
 * "拼成什么文字"分开，Mapper 才能在纯 JVM 单测里断言，面板也不必到处判空。</p>
 */
public record AiHistoryText(@StringRes int res, List<Object> arguments, @Nullable String raw) {

    public AiHistoryText {
        arguments = List.copyOf(arguments);
    }

    @NonNull
    public static AiHistoryText of(@StringRes int res) {
        return new AiHistoryText(res, List.of(), null);
    }

    /**
     * 参数保持原类型，不要统一转成字符串：{@code %1$d} 这种占位符只接受整型，
     * 传 {@code String} 会在 {@code getString} 里抛 {@code IllegalFormatConversionException}。
     */
    @NonNull
    public static AiHistoryText of(@StringRes int res, @NonNull Object... arguments) {
        return new AiHistoryText(res, List.of(arguments), null);
    }

    /** 用户自己写的内容：标题、摘要，原样显示，不查资源。 */
    @NonNull
    public static AiHistoryText raw(@NonNull String text) {
        return new AiHistoryText(0, List.of(), text);
    }

    public boolean isRaw() {
        return raw != null;
    }

    /** 解析成可以直接放进原生 {@code TextView} 的字符串。 */
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

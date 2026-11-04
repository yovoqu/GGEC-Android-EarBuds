package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 把一条 Chat 内容块写成报文。标准形状和主机方言各有一个实现，资源只持有其中一个。
 */
public interface YoChatPartEncoder {

    @NonNull
    JSONObject encode(@NonNull YoChatContentPart part) throws JSONException;

    /**
     * 单次输出上限的字段名。官方 Chat Completions 用 {@code max_completion_tokens}；
     * 仍只认 {@code max_tokens} 的主机在自己的编码器里改掉，避免上限被静默忽略。
     */
    @NonNull
    default String maxOutputTokensField() {
        return "max_completion_tokens";
    }
}

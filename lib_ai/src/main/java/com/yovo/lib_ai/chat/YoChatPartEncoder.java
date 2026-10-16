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
}

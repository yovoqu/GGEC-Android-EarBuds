package com.yovo.lib_ai.platform.deepseek;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.chat.OpenAiChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatContentPart;
import com.yovo.lib_ai.chat.YoChatPartEncoder;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * DeepSeek Chat 的文件块是扁平字段。文本、图片和音频仍用官方形状。
 */
public final class DeepSeekChatPartEncoder implements YoChatPartEncoder {

    public static final DeepSeekChatPartEncoder INSTANCE = new DeepSeekChatPartEncoder();

    @NonNull
    @Override
    public JSONObject encode(@NonNull YoChatContentPart part) throws JSONException {
        if (part.kind() != YoChatContentPart.Kind.FILE) {
            return OpenAiChatPartEncoder.INSTANCE.encode(part);
        }
        JSONObject json = new JSONObject();
        json.put("type", "file");
        if (part.fileId() != null) {
            json.put("file_id", part.fileId());
        } else {
            json.put("file_data", part.fileData());
            json.put("filename", part.filename());
        }
        return json;
    }
}

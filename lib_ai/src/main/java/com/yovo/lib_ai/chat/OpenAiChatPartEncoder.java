package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Chat Completions 官方内容块。文件块嵌在 {@code file} 对象里。
 */
public final class OpenAiChatPartEncoder implements YoChatPartEncoder {

    public static final OpenAiChatPartEncoder INSTANCE = new OpenAiChatPartEncoder();

    @NonNull
    @Override
    public JSONObject encode(@NonNull YoChatContentPart part) throws JSONException {
        JSONObject json = new JSONObject();
        switch (part.kind()) {
            case TEXT -> {
                json.put("type", "text");
                json.put("text", part.text());
            }
            case IMAGE_URL -> {
                JSONObject image = new JSONObject();
                image.put("url", part.text());
                if (part.detail() != null) {
                    image.put("detail", part.detail());
                }
                json.put("type", "image_url");
                json.put("image_url", image);
            }
            case INPUT_AUDIO -> {
                JSONObject audio = new JSONObject();
                audio.put("data", part.text());
                audio.put("format", part.audioFormat());
                json.put("type", "input_audio");
                json.put("input_audio", audio);
            }
            case FILE -> {
                JSONObject file = new JSONObject();
                if (part.fileId() != null) {
                    file.put("file_id", part.fileId());
                } else {
                    file.put("file_data", part.fileData());
                    file.put("filename", part.filename());
                }
                json.put("type", "file");
                json.put("file", file);
            }
            default -> throw new JSONException("Unsupported chat content part");
        }
        return json;
    }
}

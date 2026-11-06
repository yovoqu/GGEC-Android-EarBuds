package com.yovo.lib_ai.chat;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Chat Completions 官方内容块。文件块嵌在 {@code file} 对象里。
 *
 * <p>缓存断点写在内容块自身的 {@code prompt_cache_breakpoint} 上，而不是独立成块：
 * 官方协议要求断点附着在某个可缓存的内容块上。</p>
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
            case VIDEO_URL -> {
                JSONObject video = new JSONObject();
                video.put("url", part.text());
                json.put("type", "video_url");
                json.put("video_url", video);
                if (part.fps() != null) {
                    json.put("fps", part.fps());
                }
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
                } else if (part.fileUrl() != null) {
                    file.put("file_url", part.fileUrl());
                } else {
                    file.put("file_data", part.fileData());
                    file.put("filename", part.filename());
                }
                json.put("type", "file");
                json.put("file", file);
            }
            default -> throw new JSONException("Unsupported chat content part");
        }
        if (part.cacheMode() != null) {
            JSONObject cache = new JSONObject();
            cache.put("mode", part.cacheMode());
            if (part.cacheTtl() != null) {
                cache.put("ttl", part.cacheTtl());
            }
            json.put("prompt_cache_breakpoint", cache);
        }
        return json;
    }
}

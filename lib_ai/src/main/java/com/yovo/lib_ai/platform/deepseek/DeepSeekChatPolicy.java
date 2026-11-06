package com.yovo.lib_ai.platform.deepseek;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatContentPart;
import com.yovo.lib_ai.chat.YoChatMessage;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;
import com.yovo.lib_ai.chat.YoChatResponseFormat;
import com.yovo.lib_ai.models.YoCapabilityException;
import com.yovo.lib_ai.platform.DialectRejections;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * DeepSeek Chat 的方言约束。
 *
 * <p>除了图片数量/HTTP 图片地址长度，DeepSeek 还有几条与 OpenAI 不同的硬规则，全部在
 * 本地前置校验，避免把注定失败的请求发到线上：</p>
 * <ul>
 *   <li>{@code response_format} 只接受 {@code text} / {@code json_object}；
 *       传 {@code json_schema} 直接抛 {@link YoCapabilityException}（kind=UNSUPPORTED_PARAM）。</li>
 *   <li>{@code stop} 最多 16 项。</li>
 *   <li>{@code frequency_penalty} / {@code presence_penalty} 已废弃，编码后删除。</li>
 *   <li>{@code thinking} 对象原样透传（{@code {"type":"enabled"}} 等）。</li>
 * </ul>
 *
 * <p>依据：DeepSeek {@code create-chat-completion}（response_format / stop / frequency_penalty
 * deprecated / thinking）与 {@code chat_prefix_completion}（{@code prefix:true} + {@code /beta}）。</p>
 */
public final class DeepSeekChatPolicy implements YoChatRequestPolicy {

    public static final int MAX_HTTP_URL_LENGTH = 8192;
    public static final int MAX_IMAGES = 600;
    public static final int MAX_STOP_SEQUENCES = 16;
    public static final DeepSeekChatPolicy INSTANCE = new DeepSeekChatPolicy();

    @Override
    public void check(@NonNull YoChatCompletionRequest request) {
        YoChatResponseFormat format = request.responseFormat();
        if (format != null && format.kind() == YoChatResponseFormat.Kind.JSON_SCHEMA) {
            throw DialectRejections.unsupportedParam(
                    "DeepSeek Chat does not support response_format=json_schema; use json_object");
        }
        if (request.stop().size() > MAX_STOP_SEQUENCES) {
            throw new IllegalArgumentException(
                    "DeepSeek Chat accepts at most " + MAX_STOP_SEQUENCES + " stop sequences");
        }
        int images = 0;
        for (YoChatMessage message : request.messages()) {
            YoChatContentPart[] parts = message.parts();
            if (parts == null) {
                continue;
            }
            for (YoChatContentPart part : parts) {
                if (part.kind() != YoChatContentPart.Kind.IMAGE_URL) {
                    continue;
                }
                images++;
                String url = part.text();
                if (url != null
                        && (url.startsWith("https://") || url.startsWith("http://"))
                        && url.length() > MAX_HTTP_URL_LENGTH) {
                    throw new IllegalArgumentException(
                            "image url must be at most " + MAX_HTTP_URL_LENGTH + " characters");
                }
            }
        }
        if (images > MAX_IMAGES) {
            throw new IllegalArgumentException("at most " + MAX_IMAGES + " images are allowed");
        }
    }

    @Override
    public void apply(@NonNull JSONObject body) throws JSONException {
        // 字段名映射：DeepSeek 用 max_tokens。
        if (body.has("max_completion_tokens") && !body.has("max_tokens")) {
            body.put("max_tokens", body.get("max_completion_tokens"));
            body.remove("max_completion_tokens");
        }
        // 已废弃的惩罚项，DeepSeek 不认；直接删。
        body.remove("frequency_penalty");
        body.remove("presence_penalty");
        // DeepSeek 不支持的采样/OpenAI 专有字段。
        body.remove("top_k");
        body.remove("logit_bias");
        body.remove("safety_identifier");
        body.remove("prompt_cache_key");
        body.remove("prompt_cache_options");
        body.remove("web_search_options");
        body.remove("service_tier");
        body.remove("store");
        body.remove("reasoning_effort");
        // thinking 对象保持原样透传（options 已写入，这里不删）。
        // 默认值：流式请求补 include_usage。
        if (body.optBoolean("stream", false)) {
            JSONObject streamOptions = body.optJSONObject("stream_options");
            if (streamOptions == null) {
                streamOptions = new JSONObject();
                body.put("stream_options", streamOptions);
            }
            if (!streamOptions.has("include_usage")) {
                streamOptions.put("include_usage", true);
            }
        }
    }
}

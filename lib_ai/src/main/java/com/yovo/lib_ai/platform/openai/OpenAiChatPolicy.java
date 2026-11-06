package com.yovo.lib_ai.platform.openai;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 官方 OpenAI Chat Completions 的方言改写。
 *
 * <p>OpenAI 只认<b>自己的</b>参数名：</p>
 * <ul>
 *   <li>单次输出上限叫 {@code max_completion_tokens}，不是 {@code max_tokens}；
 *       兼容网关常常反过来，所以这里把误传的 {@code max_tokens} 归一。</li>
 *   <li>没有 {@code top_k}，思考强度用 {@code reasoning_effort}，没有 {@code thinking} 对象，
 *       也没有 Qwen 的 {@code enable_thinking}。这些字段即使被通用 options 带上也要删掉，
 *       否则官方端点会 400 unknown_parameter。</li>
 *   <li>流式请求固定补 {@code stream_options.include_usage = true}，保证能拿到用量。</li>
 * </ul>
 *
 * <p>依据：OpenAI Chat Completions 参数清单
 * <a href="https://platform.openai.com/docs/api-reference/chat/create">chat/create</a>。</p>
 */
public final class OpenAiChatPolicy implements YoChatRequestPolicy {

    public static final OpenAiChatPolicy INSTANCE = new OpenAiChatPolicy();

    @Override
    public void check(@NonNull YoChatCompletionRequest request) {
        // OpenAI 没有本地必须拒绝的 Chat 参数；非法值在各领域对象的 Builder 里已经拦下。
    }

    @Override
    public void apply(@NonNull JSONObject body) throws JSONException {
        // 字段名映射：max_tokens -> max_completion_tokens（不覆盖已存在的显式字段）。
        if (body.has("max_tokens") && !body.has("max_completion_tokens")) {
            body.put("max_completion_tokens", body.get("max_tokens"));
            body.remove("max_tokens");
        }
        // 删除 OpenAI 不认识的方言字段。
        body.remove("top_k");
        body.remove("thinking");
        body.remove("enable_thinking");
        // 默认值：流式请求补 include_usage，保证 usage 可解析。
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

package com.yovo.lib_ai.platform.qwen;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 阿里云百炼 DashScope 的 OpenAI 兼容方言。
 *
 * <p>DashScope 的 OpenAI 兼容端点大体按 OpenAI 的形状收发，但有几处固定差异：</p>
 * <ul>
 *   <li>单次输出上限用 {@code max_tokens}，不是 {@code max_completion_tokens}；</li>
 *   <li>思考开关是布尔字段 {@code enable_thinking}，不是 OpenAI 的 {@code reasoning_effort}，
 *       也不是 DeepSeek 的 {@code thinking} 对象；</li>
 *   <li>{@code top_k} 是 DashScope 真正支持的采样参数，保留；</li>
 *   <li>不接受 {@code logit_bias}、{@code safety_identifier}、{@code prompt_cache_key}、
 *       {@code prompt_cache_options}、{@code web_search_options}、{@code service_tier}、
 *       {@code store}、{@code reasoning_effort} 等 OpenAI 专有字段。</li>
 * </ul>
 *
 * <p>依据：百炼「OpenAI 兼容」与 {@code enable_thinking}/{@code top_k} 参数说明。</p>
 */
public final class QwenChatPolicy implements YoChatRequestPolicy {

    public static final QwenChatPolicy INSTANCE = new QwenChatPolicy();

    @Override
    public void check(@NonNull YoChatCompletionRequest request) {
        // DashScope 的 Chat 参数在领域对象 Builder 里已校验；这里没有额外本地拒绝项。
    }

    @Override
    public void apply(@NonNull JSONObject body) throws JSONException {
        // 字段名映射：max_completion_tokens -> max_tokens（不覆盖显式 max_tokens）。
        if (body.has("max_completion_tokens") && !body.has("max_tokens")) {
            body.put("max_tokens", body.get("max_completion_tokens"));
            body.remove("max_completion_tokens");
        }
        // thinking 对象 -> enable_thinking 布尔。
        JSONObject thinking = body.optJSONObject("thinking");
        if (thinking != null) {
            String type = thinking.optString("type", "enabled");
            body.put("enable_thinking", "disabled".equalsIgnoreCase(type) ? false : true);
            body.remove("thinking");
        }
        // 删除 OpenAI 专有、DashScope 不接受的字段。
        body.remove("logit_bias");
        body.remove("safety_identifier");
        body.remove("prompt_cache_key");
        body.remove("prompt_cache_options");
        body.remove("web_search_options");
        body.remove("service_tier");
        body.remove("store");
        body.remove("reasoning_effort");
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

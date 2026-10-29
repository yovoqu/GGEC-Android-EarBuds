package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 语音交流模式可选的模型。
 *
 * <p><b>为什么不复用文本平台里的模型</b>：文本模型是用户从平台拉回来的一串 id，
 * 语音模型则是"一条实时连接 + 固定端点 + 固定协议"的组合。把两者塞进同一个列表，
 * 用户会看到一堆连不上的对话模型，选中之后要到拨号时才失败。所以语音模型由 APP
 * 在代码里预设成一张表：换模型只是换一项预设，不依赖用户先配好哪个文本平台。</p>
 *
 * <p>这张表是语音模式的唯一真相：端点与模型 id 都只写在这里，界面只认
 * {@link #displayName()}，不自己拼地址。新增一家只需要加一个枚举值。</p>
 */
public enum AiVoiceModel {

    /** OpenAI Realtime。 */
    CHATGPT("chatgpt", "ChatGPT", "https://api.openai.com",
            "gpt-4o-realtime-preview", "sk-…"),

    /** 阿里云百炼的 Qwen Omni 实时模型，走 OpenAI 兼容模式。 */
    QWEN("qwen", "Qwen", "https://dashscope.aliyuncs.com/compatible-mode",
            "qwen3-omni-flash-realtime", "sk-…");

    private final String id;
    private final String displayName;
    private final String baseUrl;
    private final String realtimeModel;
    private final String keyHint;

    AiVoiceModel(String id, String displayName, String baseUrl, String realtimeModel, String keyHint) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.realtimeModel = realtimeModel;
        this.keyHint = keyHint;
    }

    /** 存进设置文档的稳定标识；改显示名不会让用户的选择失效。 */
    @NonNull
    public String id() {
        return id;
    }

    @NonNull
    public String displayName() {
        return displayName;
    }

    /** 实时链路的主机基址；{@code /v1/realtime} 由传输层追加。 */
    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    /** 实时模型 id：它是端点地址的一部分，连上之后不能再换。 */
    @NonNull
    public String realtimeModel() {
        return realtimeModel;
    }

    @NonNull
    public String keyHint() {
        return keyHint;
    }

    /** 新装或旧文档读不出选择时的起点：ChatGPT 的实时接口最通用。 */
    @NonNull
    public static AiVoiceModel defaultModel() {
        return CHATGPT;
    }

    @Nullable
    public static AiVoiceModel fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (AiVoiceModel model : values()) {
            if (model.id.equalsIgnoreCase(id) || model.name().equalsIgnoreCase(id)) {
                return model;
            }
        }
        return null;
    }
}

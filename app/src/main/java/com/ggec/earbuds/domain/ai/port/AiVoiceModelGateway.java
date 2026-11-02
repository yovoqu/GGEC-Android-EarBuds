package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiModel;

import java.util.List;

/**
 * 按 APP 给定的地址与密钥，向服务商拉取模型列表。
 *
 * <p>这里不做任何筛选：平台返回什么就交回什么。语音设置要能看见该账号下<b>全部</b>模型，
 * 再让用户自己挑一个能接实时会话的；由网关替用户挑会给"网页里有、列表里没有"的模型
 * 制造黑洞。哪些模型真正支持实时，由 {@link AiModel#realtime()} 这类能力位在界面层标注。</p>
 *
 * <p>阻塞调用，不要在主线程执行。</p>
 */
public interface AiVoiceModelGateway {

    @NonNull
    List<AiModel> fetch(@NonNull String baseUrl, @NonNull String apiKey);
}

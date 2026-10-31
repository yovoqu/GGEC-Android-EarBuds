package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiModel;

import java.util.List;

/**
 * 按 APP 给定的地址和密钥，向 ChatGPT 拉模型，并只留下实时模型。
 * 阻塞调用，不要在主线程执行。
 */
public interface AiChatGptRealtimeGateway {

    @NonNull
    List<AiModel> fetch(@NonNull String baseUrl, @NonNull String apiKey);
}

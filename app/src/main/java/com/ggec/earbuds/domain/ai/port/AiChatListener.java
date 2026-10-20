package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiFailure;

/**
 * 网关回调的线程不做保证，使用方自己切回需要的线程。取消之后不再有任何回调。
 */
public interface AiChatListener {

    void onReasoning(String delta);

    void onText(String delta);

    void onComplete(AiChatResult result);

    void onFailure(AiFailure failure);
}

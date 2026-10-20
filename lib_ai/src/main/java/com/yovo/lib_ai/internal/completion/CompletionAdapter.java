package com.yovo.lib_ai.internal.completion;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;

/**
 * 把统一的补全请求翻译成某一条协议的请求，再把该协议的流式事件和结果翻译回统一类型。
 * 适配器只依赖公开的协议端口，不接触传输层。
 */
interface CompletionAdapter {

    YoCompletionResult complete(YoCompletionRequest request);

    YoAiCall stream(YoCompletionRequest request, YoCompletionListener listener);
}

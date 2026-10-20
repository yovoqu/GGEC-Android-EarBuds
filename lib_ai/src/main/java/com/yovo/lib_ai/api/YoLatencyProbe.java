package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.probe.YoProbeListener;
import com.yovo.lib_ai.probe.YoProbePlan;

/**
 * 给一批模型各发一个极短的流式请求，量首字延迟和总耗时。
 * 探针从不重试，失败如实上报；回调按模型完成顺序送到客户端的回调执行器上。
 */
public interface YoLatencyProbe {

    /**
     * 立即返回。{@link YoAiCall#cancel()} 会中断进行中的请求并丢弃排队的模型，
     * 之后监听器仍会收到一次 {@code onComplete}，摘要标记为已取消。
     */
    @NonNull
    YoAiCall run(@NonNull YoProbePlan plan, @NonNull YoProbeListener listener);
}

package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.policy.AiBatchSpeedPolicy;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestGateway;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.probe.YoProbeListener;
import com.yovo.lib_ai.probe.YoProbePlan;
import com.yovo.lib_ai.probe.YoProbeResult;
import com.yovo.lib_ai.probe.YoProbeSummary;

import java.util.List;
import java.util.Objects;

/**
 * 测速与连接测试都交给 lib_ai 的探针：它不重试、按完成顺序回调，应用只负责把结果翻译成领域类型，
 * 并保证租约在探针结束（含被取消）时归还。
 *
 * <p>请求体固定为最小流式请求（CON-02、SPD-B-01）：用户消息 {@code Hi}、最大输出 16、
 * 不带系统提示词与生成参数；收到第一个输出片段即记首字并断开，15 s 无片段判超时。</p>
 */
public final class LibAiSpeedTestGateway implements AiSpeedTestGateway {

    static final int CONCURRENCY = AiBatchSpeedPolicy.CONCURRENCY;
    static final long TIMEOUT_MILLIS = AiBatchSpeedPolicy.TIMEOUT_MILLIS;
    static final String PROMPT = "Hi";
    static final int MAX_OUTPUT_TOKENS = 16;

    private final LibAiClientPool pool;

    public LibAiSpeedTestGateway(LibAiClientPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public AiCancellable run(AiPlatformConfig platform, List<String> modelIds, AiSpeedTestListener listener) {
        LibAiClientPool.Lease lease = null;
        try {
            lease = pool.acquire(platform);
            LibAiClientPool.Lease held = lease;
            YoProbePlan plan = YoProbePlan.builder()
                    .protocol(LibAiPlatformCatalog.protocol(platform.protocol()))
                    .models(modelIds)
                    .concurrency(CONCURRENCY)
                    .timeoutMillis(TIMEOUT_MILLIS)
                    .prompt(PROMPT)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .firstTokenOnly(true)
                    .build();
            YoAiCall call = lease.client().latencyProbe(platform.id()).run(plan, new YoProbeListener() {
                @Override
                public void onStarted(String model) {
                    listener.onStarted(model);
                }

                @Override
                public void onResult(YoProbeResult result) {
                    listener.onResult(toResult(result));
                }

                @Override
                public void onComplete(YoProbeSummary summary) {
                    held.close();
                    listener.onComplete(summary.cancelled());
                }
            });
            return call::cancel;
        } catch (RuntimeException error) {
            if (lease != null) {
                lease.close();
            }
            throw LibAiFailures.map(error, platform.apiKey());
        }
    }

    static AiSpeedResult toResult(YoProbeResult result) {
        AiSpeedResult.Status status = switch (result.status()) {
            case OK -> AiSpeedResult.Status.OK;
            case FAILED -> AiSpeedResult.Status.FAILED;
            case TIMEOUT -> AiSpeedResult.Status.TIMEOUT;
        };
        AiFailureKind kind = result.errorKind() == null ? null : LibAiFailures.kind(result.errorKind());
        return new AiSpeedResult(result.model(), status, result.firstTokenMillis(), result.totalMillis(),
                kind, result.message(), 0L, result.outputTokens());
    }
}

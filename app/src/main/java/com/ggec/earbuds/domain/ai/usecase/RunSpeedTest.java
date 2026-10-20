package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestGateway;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;

import java.util.List;
import java.util.Objects;

/**
 * 对平台里的一批模型测速。每个结果一到就写回配置，所以即使中途离开页面，已测完的数据也不会丢；
 * 同一个结果再转给界面的监听器。立即返回，进度经监听器送达。
 */
public final class RunSpeedTest {

    private final AiSettingsRepository repository;
    private final AiSpeedTestGateway gateway;
    private final AiClock clock;

    public RunSpeedTest(AiSettingsRepository repository, AiSpeedTestGateway gateway, AiClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AiCancellable execute(String platformId, List<String> modelIds, AiSpeedTestListener observer) {
        if (modelIds.isEmpty()) {
            throw new AiValidationException(AiValidationError.MODEL_UNKNOWN);
        }
        AiPlatformConfig platform = PlatformLookup.configured(repository.load(), platformId);
        for (String modelId : modelIds) {
            if (platform.model(modelId).isEmpty()) {
                throw new AiValidationException(AiValidationError.MODEL_UNKNOWN);
            }
        }
        return gateway.run(platform, List.copyOf(modelIds), new AiSpeedTestListener() {
            @Override
            public void onStarted(String modelId) {
                observer.onStarted(modelId);
            }

            @Override
            public void onResult(AiSpeedResult result) {
                AiSpeedResult stamped = new AiSpeedResult(result.modelId(), result.status(),
                        result.firstTokenMillis(), result.totalMillis(), result.failureKind(),
                        result.message(), clock.now());
                repository.update(settings -> settings.platform(platformId)
                        .map(current -> settings.withPlatform(current.withSpeedResult(stamped)))
                        .orElse(settings));
                observer.onResult(stamped);
            }

            @Override
            public void onComplete(boolean cancelled) {
                observer.onComplete(cancelled);
            }
        });
    }
}

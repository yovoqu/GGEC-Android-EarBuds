package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.port.AiModelGateway;

import java.util.List;
import java.util.Objects;

public final class LibAiModelGateway implements AiModelGateway {

    private final LibAiClientPool pool;

    public LibAiModelGateway(LibAiClientPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public List<AiModel> fetch(AiPlatformConfig platform) {
        try (LibAiClientPool.Lease lease = pool.acquire(platform)) {
            return LibAiModels.chatModels(lease.client().models(platform.id()).list());
        } catch (RuntimeException error) {
            throw LibAiFailures.map(error, platform.apiKey());
        }
    }
}

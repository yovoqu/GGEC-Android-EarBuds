package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;
import com.ggec.earbuds.domain.ai.port.AiModelGateway;

import java.util.List;
import java.util.Objects;

public final class LibAiModelGateway implements AiModelGateway {

    private final LibAiClientPool pool;
    private final AiCredentialStore credentials;

    public LibAiModelGateway(LibAiClientPool pool, AiCredentialStore credentials) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    @Override
    public List<AiModel> fetch(AiPlatformConfig platform) {
        String apiKey = credentials.platformKey(platform.id());
        if (apiKey == null) {
            throw new AiFailure(AiFailureKind.AUTHENTICATION, "API key is not configured", null);
        }
        try (LibAiClientPool.Lease lease = pool.acquire(platform, apiKey)) {
            return LibAiModels.models(lease.client().models(platform.id()).list());
        } catch (RuntimeException error) {
            throw LibAiFailures.map(error, apiKey);
        }
    }
}

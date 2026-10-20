package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;

/**
 * 几个用例都要先取出"存在且已配置密钥"的平台，规则只写一处。
 */
final class PlatformLookup {

    private PlatformLookup() {
    }

    static AiPlatformConfig configured(AiSettings settings, String platformId) {
        AiPlatformConfig platform = settings.platform(platformId)
                .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
        if (!platform.configured()) {
            throw new AiValidationException(AiValidationError.PLATFORM_NOT_CONFIGURED);
        }
        return platform;
    }
}

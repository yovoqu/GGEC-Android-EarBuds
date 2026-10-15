package com.yovo.lib_bluetooth.model.classic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CbtSppConfigTest {

    @Test
    public void defaultStrategyHonorsCallerSpecifiedEndpoint() {
        CbtSppConfig config = CbtSppConfig.builder().build();

        assertEquals(CbtSppConnectionStrategy.USER_SPECIFIED_ONLY, config.connectionStrategy());
    }

    @Test
    public void factoryChannelStrategyIsExplicit() {
        CbtSppConfig config = CbtSppConfig.builder()
                .allowHiddenApiChannel(true)
                .connectionStrategy(CbtSppConnectionStrategy.CHANNEL_ONLY)
                .build();

        assertTrue(config.allowHiddenApiChannel());
        assertEquals(CbtSppConnectionStrategy.CHANNEL_ONLY, config.connectionStrategy());
    }
}

package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionAttemptPlanner;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionPolicy;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionPolicyValidator;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionStrategy;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtSppConnectionPolicyValidator} 策略校验的单元测试。
 */

public class YoCbtSppConnectionPolicyValidatorTest {

    @Test
    public void uuidOnly_rejectsChannelEndpoint() {
        YoCbtSppConnectionPolicy policy = YoCbtSppConnectionPolicy.defaultUuidOnly();
        YoCbtSppEndpoint channel = YoCbtSppEndpoint.channel(5,
                YoCbtSppEndpointSource.USER_SPECIFIED, YoCbtSppSecurityMode.SECURE);

        YoResult<Void> result = YoCbtSppConnectionPolicyValidator.validate(policy, channel);

        assertTrue(result.isFailure());
    }

    @Test
    public void userSpecifiedOnly_acceptsChannelEndpoint() {
        YoCbtSppConnectionPolicy policy = YoCbtSppConnectionPolicy.defaultUserSpecified();
        YoCbtSppEndpoint channel = YoCbtSppEndpoint.channel(15,
                YoCbtSppEndpointSource.USER_SPECIFIED, YoCbtSppSecurityMode.SECURE);

        assertTrue(YoCbtSppConnectionPolicyValidator.validate(policy, channel).isSuccess());
    }

    @Test
    public void channelOnly_acceptsChannelEndpoint() {
        YoCbtSppConnectionPolicy policy = new YoCbtSppConnectionPolicy(
                YoCbtSppConnectionStrategy.CHANNEL_ONLY, false);
        YoCbtSppEndpoint channel = YoCbtSppEndpoint.channel(28,
                YoCbtSppEndpointSource.USER_SPECIFIED, YoCbtSppSecurityMode.SECURE);

        assertTrue(YoCbtSppConnectionPolicyValidator.validate(policy, channel).isSuccess());
    }

    @Test
    public void uuidOnly_acceptsUuidEndpoint() {
        YoCbtSppConnectionPolicy policy = YoCbtSppConnectionPolicy.defaultUuidOnly();
        YoCbtSppEndpoint uuid = YoCbtSppEndpoint.uuid(UUID.randomUUID());

        assertTrue(YoCbtSppConnectionPolicyValidator.validate(policy, uuid).isSuccess());
    }

    @Test
    public void channelThenUuid_fallbackPlannerAddsStandardUuid() {
        YoCbtSppConnectionPolicy policy = new YoCbtSppConnectionPolicy(
                YoCbtSppConnectionStrategy.CHANNEL_THEN_UUID, true);
        YoCbtSppConfig config = YoCbtSppConfig.builder()
                .connectionPolicy(policy)
                .build();
        YoCbtSppEndpoint channel = YoCbtSppEndpoint.channel(3,
                YoCbtSppEndpointSource.USER_SPECIFIED, YoCbtSppSecurityMode.INSECURE);

        List<YoCbtSppEndpoint> attempts = YoCbtSppConnectionAttemptPlanner.plan(config, channel);

        assertEquals(2, attempts.size());
        assertTrue(attempts.get(0).isChannel());
        assertTrue(attempts.get(1).isUuid());
        assertEquals(YoCbtSppEndpoint.STANDARD_SPP_UUID, attempts.get(1).getUuid());
        assertEquals(YoCbtSppSecurityMode.INSECURE, attempts.get(1).getSecurityMode());
    }

    @Test
    public void fallbackDisabled_returnsPrimaryOnly() {
        YoCbtSppConnectionPolicy policy = new YoCbtSppConnectionPolicy(
                YoCbtSppConnectionStrategy.CHANNEL_THEN_UUID, false);
        YoCbtSppConfig config = YoCbtSppConfig.builder().connectionPolicy(policy).build();
        YoCbtSppEndpoint channel = YoCbtSppEndpoint.channel(3,
                YoCbtSppEndpointSource.USER_SPECIFIED, YoCbtSppSecurityMode.SECURE);

        List<YoCbtSppEndpoint> attempts = YoCbtSppConnectionAttemptPlanner.plan(config, channel);

        assertEquals(1, attempts.size());
    }
}

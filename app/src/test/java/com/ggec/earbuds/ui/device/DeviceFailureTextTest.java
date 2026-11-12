package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertNotEquals;

import com.ggec.earbuds.domain.device.model.DeviceFailure;

import org.junit.Test;

/** 错误只分类：每个分类都必须能落到一句用户文案，不允许空映射。 */
public final class DeviceFailureTextTest {

    @Test
    public void everyFailureHasUserFacingText() {
        for (DeviceFailure failure : DeviceFailure.values()) {
            assertNotEquals("missing text for " + failure, 0,
                    DeviceFailureText.message(failure));
        }
    }
}

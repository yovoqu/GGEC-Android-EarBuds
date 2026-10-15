package com.yovo.lib_bluetooth.internal.classic.profiles.extensions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtProfileExtensionRegistry} 扩展注册与查询的单元测试。
 */

public class YoCbtProfileExtensionRegistryTest {

    @Test
    public void registerAndLookupExtension() {
        YoCbtProfileExtensionRegistry registry = new YoCbtProfileExtensionRegistry();
        YoCbtProfileExtension extension = stub(YoCbtProfileType.MAP);

        registry.register(extension);

        assertSame(extension, registry.get(YoCbtProfileType.MAP));
        assertTrue(registry.hasExtension(YoCbtProfileType.MAP));
        assertTrue(registry.isSupported(YoCbtProfileType.MAP));
    }

    @Test
    public void hasExtensionReturnsFalseForCoreProfiles() {
        YoCbtProfileExtensionRegistry registry = new YoCbtProfileExtensionRegistry();
        registry.register(stub(YoCbtProfileType.MAP));

        assertFalse(registry.hasExtension(YoCbtProfileType.A2DP));
        assertFalse(registry.hasExtension(YoCbtProfileType.SPP));
        assertTrue(registry.hasExtension(YoCbtProfileType.MAP));
    }

    @NonNull
    private static YoCbtProfileExtension stub(@NonNull YoCbtProfileType type) {
        return new YoCbtProfileExtension() {
            @NonNull
            @Override
            public YoCbtProfileType getProfileType() {
                return type;
            }

            @Override
            public boolean isSupported() {
                return true;
            }

            @NonNull
            @Override
            public YoResult<Boolean> isConnected(@NonNull YoCbtDeviceId deviceId) {
                return YoResult.success(false);
            }

            @Override
            public void release() {
            }
        };
    }
}

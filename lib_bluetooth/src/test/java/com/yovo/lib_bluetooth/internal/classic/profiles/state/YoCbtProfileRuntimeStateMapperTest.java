package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtProfileRuntimeStateMapper} 状态映射的单元测试。
 */

public class YoCbtProfileRuntimeStateMapperTest {

    @Test
    public void mapsConnectionStatesToRuntimeStates() {
        assertEquals(YoCbtProfileRuntimeState.CONNECTED,
                YoCbtProfileRuntimeStateMapper.fromConnectionState(
                        YoCbtProfileConnectionState.CONNECTED));
        assertEquals(YoCbtProfileRuntimeState.DISCONNECTED,
                YoCbtProfileRuntimeStateMapper.fromConnectionState(
                        YoCbtProfileConnectionState.DISCONNECTED));
        assertEquals(YoCbtProfileRuntimeState.UNKNOWN,
                YoCbtProfileRuntimeStateMapper.fromConnectionState(
                        YoCbtProfileConnectionState.UNKNOWN));
    }
}

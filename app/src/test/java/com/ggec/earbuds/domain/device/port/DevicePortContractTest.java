package com.ggec.earbuds.domain.device.port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.ggec.earbuds.provider.lib_bluetooth.HsDevicePort;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 端口契约的结构证据：扫描入口唯一签名、稳定关闭契约，以及 App provider 不持有连接超时编排。
 */
public final class DevicePortContractTest {

    @Test
    public void startScanBatchExposesSingleFourArgumentSignature() {
        Method[] matches = Arrays.stream(DevicePort.class.getMethods())
                .filter(method -> method.getName().equals("startScanBatch"))
                .toArray(Method[]::new);

        assertEquals("扫描入口必须唯一", 1, matches.length);
        assertEquals("四类回调全部必填", 4, matches[0].getParameterCount());
    }

    @Test
    public void closeIsStableLifecycleContract() throws NoSuchMethodException {
        Method close = DevicePort.class.getMethod("close");
        assertEquals(void.class, close.getReturnType());
        assertEquals(0, close.getParameterCount());
    }

    @Test
    public void hsDevicePortDoesNotOwnConnectionTimeoutOrMainThreadHandler() {
        Set<String> fieldNames = Arrays.stream(HsDevicePort.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fieldNames.contains("CONNECT_TIMEOUT_MILLIS"));
        assertFalse(fieldNames.contains("connectAttempt"));
        assertFalse(fieldNames.contains("connectTimeoutHandler"));

        Set<String> methodNames = Arrays.stream(HsDevicePort.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertFalse(methodNames.contains("scheduleConnectTimeout"));
        assertFalse(methodNames.contains("clearConnectTimeout"));
        assertFalse(methodNames.contains("ensureConnectHandler"));
    }
}

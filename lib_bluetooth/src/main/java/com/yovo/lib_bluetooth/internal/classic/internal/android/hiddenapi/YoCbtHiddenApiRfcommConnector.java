package com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 通过隐藏 API 建立 RFCOMM 通道 Socket 连接器。
 */

public final class YoCbtHiddenApiRfcommConnector {
    private YoCbtHiddenApiRfcommConnector() {
    }

    @NonNull
    public static BluetoothSocket createChannelSocket(@NonNull BluetoothDevice device,
                                                      int channel,
                                                      @NonNull YoCbtSppSecurityMode mode,
                                                      @NonNull YoCbtHiddenApiPolicy policy) throws IOException {
        if (policy != YoCbtHiddenApiPolicy.EXPLICITLY_ENABLED) {
            throw new YoCbtHiddenApiException(YoCbtHiddenApiException.Reason.DISABLED_BY_POLICY,
                    "RFCOMM channel hidden API is disabled by policy");
        }
        if (channel <= 0) {
            throw new YoCbtHiddenApiException(YoCbtHiddenApiException.Reason.INVALID_ARGUMENT,
                    "invalid RFCOMM channel");
        }
        String methodName = mode == YoCbtSppSecurityMode.INSECURE
                ? "createInsecureRfcommSocket" : "createRfcommSocket";
        try {
            Method method = device.getClass().getMethod(methodName, int.class);
            return (BluetoothSocket) method.invoke(device, channel);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new YoCbtHiddenApiException(YoCbtHiddenApiException.Reason.INVOCATION_FAILED,
                    methodName + " failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new YoCbtHiddenApiException(YoCbtHiddenApiException.Reason.METHOD_NOT_AVAILABLE,
                    methodName + " unavailable", e);
        } catch (SecurityException e) {
            throw new YoCbtHiddenApiException(YoCbtHiddenApiException.Reason.REFLECTION_FAILED,
                    methodName + " blocked", e);
        }
    }
}

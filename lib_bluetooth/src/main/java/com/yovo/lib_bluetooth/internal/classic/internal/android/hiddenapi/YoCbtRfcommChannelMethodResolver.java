package com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi;

import android.bluetooth.BluetoothDevice;
import android.os.Build;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;

import java.lang.reflect.Method;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 解析 RFCOMM 通道 Socket 创建所需的隐藏 API 方法，兼容不同 Android 版本。
 */
public final class YoCbtRfcommChannelMethodResolver {

    private static final String TAG = "YoCbtRfcommChannelMethodResolver";

    private static final String METHOD_RFCOMM = "createRfcommSocket";
    private static final String METHOD_RFCOMM_INSECURE = "createInsecureRfcommSocket";

    // 候选方法名，用于平台特定重命名时的兜底。
    // 先尝试标准名称，再尝试部分厂商 ROM 或较新 API 级别上报的替代名。
    private static final String[] CANDIDATES_SECURE = {
            METHOD_RFCOMM,
            "createRfcommSocket",
            "createRfcommSocket"
    };

    private static final String[] CANDIDATES_INSECURE = {
            METHOD_RFCOMM_INSECURE,
            "createInsecureRfcommSocket",
            "createInsecureRfcommSocket"
    };

    private static Method cachedSecureMethod;
    private static Method cachedInsecureMethod;
    private static final Object CACHE_LOCK = new Object();

    /**
     * 解析并返回用于创建指定安全模式 RFCOMM 通道 Socket 的 {@link Method}。
     *
     * <p>解析出的方法签名为 {@link BluetoothDevice} 上的
     * {@code BluetoothSocket method(int)}。
     *
     * @param secure 是否使用安全变体
     *               （{@code true} = {@code createRfcommSocket}，
     *               {@code false} = {@code createInsecureRfcommSocket}）
     * @return 可用于调用的反射 {@link Method} 句柄
     * @throws YoCbtHiddenApiException 若方法无法找到或访问
     */
    @NonNull
    public static Method resolveMethod(boolean secure) throws YoCbtHiddenApiException {
        Method cached = secure ? cachedSecureMethod : cachedInsecureMethod;
        if (cached != null) {
            return cached;
        }

        synchronized (CACHE_LOCK) {
            // 双重检查锁定
            cached = secure ? cachedSecureMethod : cachedInsecureMethod;
            if (cached != null) {
                return cached;
            }

            Method resolved = resolveInternal(secure);
            if (secure) {
                cachedSecureMethod = resolved;
            } else {
                cachedInsecureMethod = resolved;
            }
            return resolved;
        }
    }

    /**
     * 解析与给定安全模式匹配的 Socket 创建方法，
     * 并在候选方法名间兜底尝试。
     */
    @NonNull
    private static Method resolveInternal(boolean secure) throws YoCbtHiddenApiException {
        String[] candidates = secure ? CANDIDATES_SECURE : CANDIDATES_INSECURE;
        String primaryName = secure ? METHOD_RFCOMM : METHOD_RFCOMM_INSECURE;

        // 依次尝试每个候选方法名
        for (String candidateName : candidates) {
            try {
                Method method = BluetoothDevice.class.getDeclaredMethod(candidateName, int.class);
                method.setAccessible(true);
                YoBluetoothLogger.d(TAG, "已解析 RFCOMM 方法: " + candidateName
                        + "（安全=" + secure + "，SDK=" + Build.VERSION.SDK_INT + "）");
                return method;
            } catch (NoSuchMethodException ignored) {
                YoBluetoothLogger.d(TAG, "未找到候选方法: " + candidateName);
            } catch (SecurityException e) {
                YoBluetoothLogger.w(TAG, "候选方法被平台拦截: " + candidateName, e);
            }
        }

        // 最后尝试略有不同的参数或方法名模式
        // 部分厂商 ROM 使用不同大小写或参数类型。
        try {
            Method method = BluetoothDevice.class.getDeclaredMethod(primaryName, Integer.TYPE);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            // 继续抛出异常
        }

        // 部分 Android 13+ 构建中，该方法可能通过
        // getMethod() 可用（部分厂商框架中公开暴露）。
        try {
            Method method = BluetoothDevice.class.getMethod(primaryName, int.class);
            method.setAccessible(true);
            YoBluetoothLogger.i(TAG, "已通过 getMethod（公共）解析 " + primaryName);
            return method;
        } catch (NoSuchMethodException ignored) {
            // 继续抛出异常
        }

        // 部分厂商框架上方法为公开时，也尝试不设 accessible。
        try {
            Method method = BluetoothDevice.class.getMethod(primaryName, Integer.TYPE);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.METHOD_NOT_AVAILABLE,
                    "RFCOMM socket method not found for secure=" + secure
                            + " on SDK " + Build.VERSION.SDK_INT
                            + ". Tried candidates: " + java.util.Arrays.toString(candidates), e);
        }
    }

    /**
     * 便捷方法：解析给定安全模式对应的 RFCOMM 方法。
     *
     * @param mode 所需的安全模式
     * @return 解析出的方法
     * @throws YoCbtHiddenApiException 若安全与非安全方法均未找到
     */
    @NonNull
    public static Method resolveMethod(@NonNull YoCbtSppSecurityMode mode)
            throws YoCbtHiddenApiException {
        return resolveMethod(mode == YoCbtSppSecurityMode.SECURE);
    }

    /**
     * 清除内部方法缓存。在测试中模拟不同 API 级别时有用。
     */
    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            cachedSecureMethod = null;
            cachedInsecureMethod = null;
        }
    }
}

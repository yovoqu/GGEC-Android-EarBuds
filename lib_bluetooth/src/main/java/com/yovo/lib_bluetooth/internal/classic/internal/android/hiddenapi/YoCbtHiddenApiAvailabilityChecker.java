package com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi;

import android.bluetooth.BluetoothDevice;
import android.os.Build;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 检测当前设备隐藏 API 方法是否可用，结果缓存以避免重复反射。
 */
public final class YoCbtHiddenApiAvailabilityChecker {

    private static final String TAG = "YoCbtHiddenApiAvailabilityChecker";

    private static final ConcurrentHashMap<String, Boolean> CACHE =
            new ConcurrentHashMap<>(16);

    private YoCbtHiddenApiAvailabilityChecker() {
    }

    /**
     * 检测给定方法在当前设备上是否可用。
     *
     * <p>当满足以下条件时，方法视为“可用”：
     * <ol>
     *   <li>可通过反射在目标类上找到，且</li>
     *   <li>可在无 {@link SecurityException} 的情况下设为可访问
     *       （即未被隐藏 API 黑名单拦截）。</li>
     * </ol>
     *
     * <p>结果按（类名、方法名、参数个数）缓存。
     *
     * @param clazz      待检查的类
     * @param methodName 要查找的方法名
     * @param paramTypes 方法的参数类型
     * @return 若方法存在且可访问则返回 {@code true}
     */
    public static boolean isMethodAvailable(@NonNull Class<?> clazz,
                                            @NonNull String methodName,
                                            @NonNull Class<?>... paramTypes) {
        String cacheKey = cacheKey(clazz, methodName, paramTypes);
        Boolean cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean available = checkAvailability(clazz, methodName, paramTypes);
        CACHE.put(cacheKey, available);
        if (!available) {
            YoBluetoothLogger.w(TAG, "隐藏 API 方法不可用: " + clazz.getSimpleName()
                    + "." + methodName + "，SDK " + Build.VERSION.SDK_INT);
        }
        return available;
    }

    /**
     * 便捷检查：{@link BluetoothDevice#createRfcommSocket(int)}
     * （或其 insecure 变体）在本设备上是否可用。
     *
     * @return 若隐藏 RFCOMM socket 方法可访问则返回 {@code true}
     */
    public static boolean isCreateRfcommSocketAvailable() {
        return isMethodAvailable(BluetoothDevice.class, "createRfcommSocket", int.class)
                || isMethodAvailable(BluetoothDevice.class, "createInsecureRfcommSocket", int.class);
    }

    /**
     * 便捷检查：是否可通过反射在本设备上访问
     * {@link BluetoothDevice#createBond()}。
     *
     * @return 若隐藏的 createBond 方法可访问则返回 {@code true}，
     *         或存在替代安全方案（无需隐藏 API）时也返回 {@code true}
     */
    public static boolean isCreateBondAvailable() {
        // createBond() 实际上是 BluetoothDevice 的公开 API，因此
        // 始终可用。但该方法也以隐藏 API 形式暴露其他签名
        // （例如 createBond(int)）。
        return isMethodAvailable(BluetoothDevice.class, "createBond");
    }

    /**
     * 便捷检查：{@link BluetoothDevice#cancelBondProcess()}
     * 是否可用。
     *
     * @return 若隐藏的 cancelBondProcess 方法可访问则返回 {@code true}
     */
    public static boolean isCancelBondProcessAvailable() {
        return isMethodAvailable(BluetoothDevice.class, "cancelBondProcess");
    }

    /**
     * 便捷检查：{@link BluetoothDevice#removeBond()}
     * 是否可用。
     *
     * @return 若隐藏的 removeBond 方法可访问则返回 {@code true}
     */
    public static boolean isRemoveBondAvailable() {
        return isMethodAvailable(BluetoothDevice.class, "removeBond");
    }

    /**
     * 清除结果缓存。主要用于测试。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    // ── 内部 ──────────────────────────────────────────────────────

    private static boolean checkAvailability(@NonNull Class<?> clazz,
                                              @NonNull String methodName,
                                              @NonNull Class<?>[] paramTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (SecurityException e) {
            // 隐藏 API 黑名单拦截了访问
            YoBluetoothLogger.w(TAG, "隐藏 API '" + methodName + "' 被平台拦截", e);
            return false;
        } catch (Exception e) {
            YoBluetoothLogger.w(TAG, "检查 " + methodName + " 时发生意外错误", e);
            return false;
        }
    }

    private static String cacheKey(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append('#').append(methodName);
        for (Class<?> pt : paramTypes) {
            sb.append(',').append(pt.getName());
        }
        return sb.toString();
    }
}

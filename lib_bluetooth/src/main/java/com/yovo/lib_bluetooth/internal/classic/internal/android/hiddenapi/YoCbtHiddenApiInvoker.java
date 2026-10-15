package com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 基于反射的 Android 隐藏 API 通用调用器，异常统一封装为 {@link YoCbtHiddenApiException}。
 */
public final class YoCbtHiddenApiInvoker {

    private static final String TAG = "YoCbtHiddenApiInvoker";

    private YoCbtHiddenApiInvoker() {
    }

    /**
     * 在指定目标对象上调用隐藏方法。
     *
     * <p>按方法名与参数类型查找方法，设为可访问，
     * 并使用提供的参数进行调用。
     *
     * @param target     要调用方法的对象
     * @param methodName 精确的方法名（区分大小写）
     * @param paramTypes 方法的参数类型（无参方法可为空）
     * @param args       传入的参数（须与 paramTypes 匹配）
     * @return 被调用方法的返回值，void 方法返回 {@code null}
     * @throws YoCbtHiddenApiException 若方法无法找到、访问或调用
     */
    @Nullable
    public static Object invoke(@NonNull Object target,
                                @NonNull String methodName,
                                @Nullable Class<?>[] paramTypes,
                                @Nullable Object[] args) throws YoCbtHiddenApiException {
        if (target == null) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.INVALID_ARGUMENT,
                    "target must not be null");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.INVALID_ARGUMENT,
                    "methodName must not be null or empty");
        }

        Class<?> clazz = target.getClass();
        Class<?>[] resolvedParamTypes = paramTypes != null ? paramTypes : new Class<?>[0];
        Object[] resolvedArgs = args != null ? args : new Object[0];

        Method method;
        try {
            method = findMethod(clazz, methodName, resolvedParamTypes);
        } catch (NoSuchMethodException e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.METHOD_NOT_AVAILABLE,
                    "Method " + methodName + " not found on " + clazz.getName(), e);
        }

        try {
            method.setAccessible(true);
            return method.invoke(target, resolvedArgs);
        } catch (IllegalAccessException e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.REFLECTION_FAILED,
                    "Cannot access method " + methodName + " on " + clazz.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.INVOCATION_FAILED,
                    "Invocation of " + methodName + " failed: "
                            + (cause != null ? cause.getMessage() : "unknown"),
                    cause != null ? cause : e);
        } catch (SecurityException e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.REFLECTION_FAILED,
                    "SecurityException accessing " + methodName + " on "
                            + clazz.getName() + " — hidden API may be blocked", e);
        } catch (Exception e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.REFLECTION_FAILED,
                    "Unexpected error invoking " + methodName + ": " + e.getMessage(), e);
        }
    }

    /**
     * 在指定类上调用静态隐藏方法。
     *
     * @param clazz      声明该方法的类
     * @param methodName 精确的方法名
     * @param paramTypes 参数类型（可为空）
     * @param args       传入的参数
     * @return 返回值
     * @throws YoCbtHiddenApiException 若方法无法找到或调用
     */
    @Nullable
    public static Object invokeStatic(@NonNull Class<?> clazz,
                                      @NonNull String methodName,
                                      @Nullable Class<?>[] paramTypes,
                                      @Nullable Object[] args) throws YoCbtHiddenApiException {
        if (clazz == null) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.INVALID_ARGUMENT,
                    "clazz must not be null");
        }

        Class<?>[] resolvedParamTypes = paramTypes != null ? paramTypes : new Class<?>[0];
        Object[] resolvedArgs = args != null ? args : new Object[0];

        Method method;
        try {
            method = findMethod(clazz, methodName, resolvedParamTypes);
        } catch (NoSuchMethodException e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.METHOD_NOT_AVAILABLE,
                    "Static method " + methodName + " not found on " + clazz.getName(), e);
        }

        try {
            method.setAccessible(true);
            return method.invoke(null, resolvedArgs);
        } catch (IllegalAccessException e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.REFLECTION_FAILED,
                    "Cannot access static method " + methodName
                            + " on " + clazz.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.INVOCATION_FAILED,
                    "Invocation of static " + methodName + " failed: "
                            + (cause != null ? cause.getMessage() : "unknown"),
                    cause != null ? cause : e);
        } catch (SecurityException e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.REFLECTION_FAILED,
                    "SecurityException accessing static " + methodName
                            + " on " + clazz.getName(), e);
        } catch (Exception e) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.REFLECTION_FAILED,
                    "Unexpected error invoking static " + methodName + ": " + e.getMessage(), e);
        }
    }

    /**
     * 在指定类上查找方法，沿父类层次向上遍历。
     *
     * <p>用于处理隐藏方法声明在父类而非具体类上的情况。
     */
    @NonNull
    private static Method findMethod(@NonNull Class<?> clazz,
                                     @NonNull String methodName,
                                     @NonNull Class<?>[] paramTypes)
            throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        // 对公开方法尝试 getMethod 作为兜底
        return clazz.getMethod(methodName, paramTypes);
    }

    /**
     * 尝试按顺序调用多个候选方法名，
     * 返回首次成功调用的结果。
     *
     * <p>适用于方法在不同 Android API 级别间被重命名的情况。
     *
     * @param target       要调用方法的对象
     * @param methodNames  候选方法名，按顺序尝试
     * @param paramTypes   参数类型
     * @param args         传入的参数
     * @return 首次成功调用的返回值
     * @throws YoCbtHiddenApiException 若所有候选均失败
     */
    @Nullable
    public static Object invokeAny(@NonNull Object target,
                                   @NonNull String[] methodNames,
                                   @Nullable Class<?>[] paramTypes,
                                   @Nullable Object[] args) throws YoCbtHiddenApiException {
        if (methodNames == null || methodNames.length == 0) {
            throw new YoCbtHiddenApiException(
                    YoCbtHiddenApiException.Reason.INVALID_ARGUMENT,
                    "At least one method name must be provided");
        }

        YoCbtHiddenApiException lastException = null;
        for (String name : methodNames) {
            try {
                return invoke(target, name, paramTypes, args);
            } catch (YoCbtHiddenApiException e) {
                lastException = e;
                YoBluetoothLogger.d(TAG, "方法 '" + name + "' 不可用，尝试下一个候选");
            }
        }

        throw new YoCbtHiddenApiException(
                YoCbtHiddenApiException.Reason.METHOD_NOT_AVAILABLE,
                "None of the candidate methods " + java.util.Arrays.toString(methodNames)
                        + " are available",
                lastException);
    }
}

package com.ggec.earbuds.domain.device.port;

/**
 * 扫描的宿主权限能力：Android 运行时权限只能由 Activity 发起，因此这一步由宿主持有实现，
 * 由装配根按宿主构造并注入。
 *
 * <p>本接口只表达「宿主能做的那一步」：把系统请求发出去、把结果收回来。是否开扫、是否重试
 * 仍由 ViewModel 决定，这里不做扫描决策。</p>
 */
public interface ScanPermissionRequest {

    /**
     * 确保扫描所需权限已具备。
     *
     * @return {@code true} 表示权限已经具备、无需弹窗；{@code false} 表示已发起系统请求，
     *         结果稍后经 {@link #onResult(int, String[], int[])} 回传。
     */
    boolean ensureGranted();

    /**
     * 转发 {@code Activity.onRequestPermissionsResult}。
     *
     * @return 本次请求的结论；{@code handled == false} 表示不是本请求的 requestCode，
     *         宿主可原样交给系统或其他请求处理。
     */
    ScanPermissionResult onResult(int requestCode, String[] permissions, int[] grantResults);
}

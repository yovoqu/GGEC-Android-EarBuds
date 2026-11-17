package com.yovo.lib_bluetooth.internal.platform;

import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;

/**
 * Android 多版本适配的**唯一事实源**：以“语义能力”而非散落版本号表达差异。
 *
 * <p>架构约定：除本类外，全库不得直接使用 {@code Build.VERSION.SDK_INT} 做运行时分支；
 * 业务/适配代码只询问能力（如 {@link #supportsExtendedAdvertising()}），由本类统一把 API level
 * 映射为能力。每个能力方法带 {@link ChecksSdkIntAtLeast} 注解，等价于把“版本守卫”显式声明给
 * lint/编译器，从而在调用点安全访问对应 API 而无需散点判断。新增/调整 Android 版本适配只改这里。</p>
 *
 * <p>能力 → API 级别对照（minSdk 24 / API 37 目标）：</p>
 * <ul>
 *   <li>LE PHY / 扩展广播 / ScanResult PHY 字段 / GATT PHY：API 26 (O)</li>
 *   <li>扫描服务请求 UUID / L2CAP CoC：API 29 (Q)</li>
 *   <li>设备别名 getAlias / A2DP 可选编码：API 30 (R)</li>
 *   <li>广播 Service Solicitation UUID / 现代 Discovery / onServiceChanged 回调：API 31 (S)</li>
 *   <li>类型化 Parcelable Extra / GATT 值回调 / Peripheral notify 带值 / LeAudio：API 33 (Tiramisu)</li>
 *   <li>GATT DCK 连接优先级：API 34 (UpsideDownCake)</li>
 *   <li>设备地址类型 getAddressType：API 35 (VanillaIceCream)</li>
 * </ul>
 *
 * <p>「反向」语义（{@code requiresXxx()} / {@code usesImplicitXxx()}）表达“该版本以下才需要额外步骤”，
 * 这类方法不加 {@link ChecksSdkIntAtLeast}：注解只声明“为真则 API level ≥ 给定值”，反向谓词不成立。
 * 业务类只能调用本类的语义方法，不得再自己比较 API level 或向能力检查器索要裸 SDK 整数。</p>
 */
public final class AndroidPlatform {

    private AndroidPlatform() {
    }

    /** 当前系统 API level（仅供诊断，禁止用于业务散点分支）。 */
    public static int level() {
        return Build.VERSION.SDK_INT;
    }

    // ---- BLE 扫描 ----
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    public static boolean supportsLePhy() {
        return level() >= Build.VERSION_CODES.O;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    public static boolean supportsScanSolicitationUuid() {
        return level() >= Build.VERSION_CODES.Q;
    }

    /**
     * BLE 扫描在 Android 12 (API 31) 之前必须同时具备位置权限与系统定位开关，之后扫描不再绑定位置。
     */
    public static boolean requiresLocationForBleScan() {
        return level() < Build.VERSION_CODES.S;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    public static boolean supportsDeviceAlias() {
        return level() >= Build.VERSION_CODES.R;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static boolean supportsDeviceAddressType() {
        return level() >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    // ---- GATT ----
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean usesTypedGattValueCallbacks() {
        return level() >= Build.VERSION_CODES.TIRAMISU;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static boolean supportsDckConnectionPriority() {
        return level() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    public static boolean supportsGattPhyControl() {
        return level() >= Build.VERSION_CODES.O;
    }

    /**
     * {@code BluetoothGattCallback#onServiceChanged} 自 Android 12 (API 31) 起由系统回调；
     * 之前必须自行订阅 Generic Attribute 的 Service Changed 特征才能感知服务表变化。
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    public static boolean usesServiceChangedCallback() {
        return level() >= Build.VERSION_CODES.S;
    }

    /**
     * Android 13 (API 33) 的 {@code setPreferredPhy} 在某些控制器上不回报 {@code onPhyUpdate}，
     * 该版本上必须补一次 {@code readPhy} 才能拿到生效 PHY。
     */
    public static boolean requiresPhyReadAfterSetPreferred() {
        return level() == Build.VERSION_CODES.TIRAMISU;
    }

    /**
     * Android 8.0 (API 26) 之前的协议栈在 GATT 认证失败时会隐式触发配对，
     * 因此未声明显式配对策略时也要先建链配对再重试；之后由显式策略决定。
     */
    public static boolean requiresBondRetryAfterAuthError() {
        return level() < Build.VERSION_CODES.O;
    }

    // ---- 广播（Peripheral / AdvertisingSet）----
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    public static boolean supportsExtendedAdvertising() {
        return level() >= Build.VERSION_CODES.O;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    public static boolean supportsAdvertisingSolicitationUuid() {
        return level() >= Build.VERSION_CODES.S;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean usesTypedPeripheralNotify() {
        return level() >= Build.VERSION_CODES.TIRAMISU;
    }

    // ---- L2CAP ----
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    public static boolean supportsL2capCoc() {
        return level() >= Build.VERSION_CODES.Q;
    }

    // ---- Intent / 广播接收 ----
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean usesTypedParcelableExtras() {
        return level() >= Build.VERSION_CODES.TIRAMISU;
    }

    // ---- 运行时权限 ----
    /**
     * {@code BLUETOOTH_SCAN} / {@code BLUETOOTH_CONNECT} / {@code BLUETOOTH_ADVERTISE} 自 Android 12
     * (API 31) 起是运行时权限；之前扫描/连接用位置权限与安装时权限。
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    public static boolean usesRuntimeBluetoothPermissions() {
        return level() >= Build.VERSION_CODES.S;
    }

    // ---- 定位 ----
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    public static boolean supportsLocationEnabledApi() {
        return level() >= Build.VERSION_CODES.P;
    }

    // ---- Classic 剖面 ----
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    public static boolean supportsA2dpOptionalCodecApi() {
        return level() >= Build.VERSION_CODES.R;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    public static boolean supportsHearingAidProfile() {
        return level() >= Build.VERSION_CODES.P;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean supportsLeAudioProfile() {
        return level() >= Build.VERSION_CODES.TIRAMISU;
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    public static boolean supportsModernDiscovery() {
        return level() >= Build.VERSION_CODES.S;
    }
}

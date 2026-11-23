package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;


/**
 * 底层硬件与蓝牙适配器交互平台接口，安全执行 cancelDiscovery、isDiscovering 等。
 */
public interface YoBtRadioPlatform {

    /**
     * 当前蓝牙适配器是否已开启。
     */
    boolean isAdapterEnabled();

    /**
     * 底层是否正处于经典蓝牙发现（Inquiry）状态。
     */
    boolean isDiscovering();

    /**
     * 安全取消经典蓝牙发现。
     *
     * @return 平台取消操作是否成功发出或当前已不在发现中
     */
    boolean cancelDiscovery();

    /**
     * 启动经典蓝牙发现。
     *
     * @return 平台启动操作是否成功
     */
    boolean startDiscovery();
}

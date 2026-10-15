package com.yovo.lib_bluetooth.internal.common.lifecycle;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 可释放资源接口，统一 {@link #release()} 生命周期语义。
 */
public interface YoReleasable {
    void release();
}

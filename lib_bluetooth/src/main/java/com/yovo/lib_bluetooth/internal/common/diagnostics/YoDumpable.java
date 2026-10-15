package com.yovo.lib_bluetooth.internal.common.diagnostics;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 可输出诊断快照（dump）的组件接口。
 */
public interface YoDumpable {
    @NonNull
    String dump();
}

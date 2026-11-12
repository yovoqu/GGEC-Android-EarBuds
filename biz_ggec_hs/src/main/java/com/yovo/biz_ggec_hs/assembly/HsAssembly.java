package com.yovo.biz_ggec_hs.assembly;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.HsRuntime;
import com.yovo.biz_ggec_hs.internal.graph.HsGraph;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.HsNearbyScanner;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.YoBleCommandLink;

/**
 * HS 运行时组合根：把同一条 BLE 命令链路装配成附近扫描与协议会话共用的 {@link HsRuntime}。
 * 门面只依赖这里，不触碰 provider 或 internal 实现。
 */
public final class HsAssembly {
    private HsAssembly() {
    }

    @NonNull
    public static HsRuntime open(@NonNull Context context) {
        YoBleCommandLink link = YoBleCommandLink.open(context);
        return new HsRuntime(new HsNearbyScanner(link.bleClient()), HsGraph.open(link));
    }
}

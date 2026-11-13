package com.yovo.biz_ggec_hs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import com.yovo.biz_ggec_hs.internal.center.HsGraph;

import java.util.List;

/**
 * HS 的唯一入口。扫描、连接、读设备信息和按能力写入都从这里进。
 */
public final class YoHsSdk {

    private final HsGraph graph;

    private YoHsSdk(@NonNull HsGraph graph) {
        this.graph = graph;
    }

    @NonNull
    public static YoHsSdk open(@NonNull Context context) {
        return new YoHsSdk(HsGraph.open(context));
    }

    public boolean scanning() {
        return graph.scan().isScanning();
    }

    @NonNull
    public HsControl.State connection() {
        return graph.link().connectionState();
    }

    public void close() {
        graph.close();
    }

    @NonNull
    public List<HsDevice.Product> products() {
        return graph.catalog().models();
    }

    @NonNull
    public HsDevice.Feature feature(@NonNull String modelId, @NonNull HsDevice.Capability capability) {
        return graph.catalog().feature(modelId, capability);
    }

    public void startScan(@NonNull HsScan.Listener listener) {
        graph.scan().start(listener);
    }

    public void stopScan() {
        graph.scan().stop();
    }

    public void filter(@NonNull HsScan.Rule rule) {
        graph.scan().setFilterRule(rule);
    }

    @NonNull
    public HsScan.View scanView() {
        return graph.scan().view();
    }

    @NonNull
    public HsScan.Health scanHealth() {
        return graph.scan().health();
    }

    /**
     * 先确认 {@code radios.classicAddress()} 的经典音频链路，没有就先连经典，再对
     * {@code radios.bleAddress()} 开 GATT。两边地址都来自扫描结果，这里不重算。
     */
    public void connect(@NonNull String modelId, @NonNull HsDevice.Radios radios) {
        graph.link().connect(modelId, radios);
    }

    @NonNull
    public HsControl.Peer peer() {
        return graph.link().peer();
    }

    @Nullable
    public HsControl.Dual dual() {
        return graph.link().dual();
    }

    @NonNull
    public List<HsControl.Recording> recordings() {
        return graph.link().recordings();
    }

    public void disconnect() {
        graph.link().disconnect();
    }

    public void watch(@NonNull HsControl.Listener listener) {
        graph.link().observe(listener);
    }

    public void unwatch(@NonNull HsControl.Listener listener) {
        graph.link().removeObserver(listener);
    }

    @NonNull
    public HsDevice.Snapshot snapshot() {
        return graph.link().snapshot();
    }

    @NonNull
    public HsControl.Outcome<HsControl.Value> read(@NonNull HsDevice.Capability capability) {
        return graph.link().read(capability);
    }

    public void write(@NonNull HsDevice.Capability capability, @NonNull HsControl.Value value,
                      @NonNull HsControl.Callback callback) {
        graph.link().write(capability, value, callback);
    }

    public void perform(@NonNull HsControl.Action action, @NonNull HsControl.Callback callback) {
        graph.link().perform(action, callback);
    }
}

package com.yovo.biz_ggec_wy;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyDevice;
import com.yovo.biz_ggec_wy.api.WyRecordApi;
import com.yovo.biz_ggec_wy.api.WyScan;
import com.yovo.biz_ggec_wy.internal.center.WyGraph;

import java.util.List;

/**
 * WY 耳机业务模块对外唯一静态门面。
 */
public final class YoWySdk {

    private final WyGraph graph;

    private YoWySdk(@NonNull WyGraph graph) {
        this.graph = graph;
    }

    @NonNull
    public static YoWySdk open(@NonNull Context context) {
        return new YoWySdk(WyGraph.open(context));
    }

    public boolean scanning() {
        return graph.scan().isScanning();
    }

    @NonNull
    public WyControl.State connection() {
        return graph.link().connectionState();
    }

    public void close() {
        graph.close();
    }

    @NonNull
    public List<WyDevice.Product> products() {
        return graph.catalog().models();
    }

    @NonNull
    public WyDevice.Feature feature(@NonNull String modelId, @NonNull WyDevice.Capability capability) {
        return graph.catalog().feature(modelId, capability);
    }

    public void startScan(@NonNull WyScan.Listener listener) {
        graph.scan().start(listener);
    }

    public void stopScan() {
        graph.scan().stop();
    }

    public void filter(@NonNull WyScan.Rule rule) {
        graph.scan().setFilterRule(rule);
    }

    @NonNull
    public WyScan.View scanView() {
        return graph.scan().view();
    }

    @NonNull
    public WyScan.Health scanHealth() {
        return graph.scan().health();
    }

    public void connect(@NonNull String modelId, @NonNull WyDevice.Radios radios) {
        graph.link().connect(modelId, radios);
    }

    @NonNull
    public WyControl.Peer peer() {
        return graph.link().peer();
    }

    public void disconnect() {
        graph.link().disconnect();
    }

    public void watch(@NonNull WyControl.Listener listener) {
        graph.link().observe(listener);
    }

    public void unwatch(@NonNull WyControl.Listener listener) {
        graph.link().removeObserver(listener);
    }

    @NonNull
    public WyDevice.Snapshot snapshot() {
        return graph.link().snapshot();
    }

    @NonNull
    public WyControl.Outcome<WyControl.Value> read(@NonNull WyDevice.Capability capability) {
        return graph.link().read(capability);
    }

    public void write(@NonNull WyDevice.Capability capability, @NonNull WyControl.Value value,
                      @NonNull WyControl.Callback callback) {
        graph.link().write(capability, value, callback);
    }

    public void perform(@NonNull WyControl.Action action, @NonNull WyControl.Callback callback) {
        graph.link().perform(action, callback);
    }

    @NonNull
    public WyRecordApi record() {
        return graph.record();
    }
}

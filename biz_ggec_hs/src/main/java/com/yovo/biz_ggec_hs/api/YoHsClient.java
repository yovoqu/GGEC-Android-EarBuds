package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.model.HsBattery;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;
import com.yovo.biz_ggec_hs.model.HsResultCallback;

/**
 * HS01 / HS02 协议会话。
 * 调用方只提交业务动作和接收结果，不接触 GATT、特征 UUID 或 lib_bluetooth。
 */
public interface YoHsClient {

    void connect(@NonNull String macAddress);

    void disconnect();

    void close();

    boolean isReady();

    @NonNull
    HsBattery battery();

    void setDeviceObserver(@Nullable HsDeviceObserver observer);

    /**
     * 设置连接生命周期回调（主线程派发）。用于替代对 {@link #isReady()} 的轮询。
     */
    void setConnectionObserver(@Nullable HsConnectionObserver observer);

    /**
     * 设备主动结束录音、随录随传或 AI 翻译时回调。协议层不持有音频实现。
     */
    void setRemoteStreamStop(@Nullable Runnable trigger);

    void setEqMode(int mode, @Nullable HsResultCallback callback);

    void setHighSoundQuality(boolean highQuality, @Nullable HsResultCallback callback);

    void setLdacEnabled(boolean enabled, @Nullable HsResultCallback callback);

    void setVolumeAdaptive(boolean enabled, @Nullable HsResultCallback callback);

    void setGameMode(boolean enabled, @Nullable HsResultCallback callback);

    void setWearDetection(boolean enabled, @Nullable HsResultCallback callback);

    void startFindAlert(boolean leftEar, @Nullable HsResultCallback callback);

    void stopFindAlert(@Nullable HsResultCallback callback);

    /**
     * 查询耳机颜色。回包值原样给出（十进制数字字符串），同时以
     * {@link HsDeviceObserver#onDeviceInfo} 推给设备观察者。
     */
    void readEarColor(@Nullable HsResultCallback callback);

    void reboot(@Nullable HsResultCallback callback);
}

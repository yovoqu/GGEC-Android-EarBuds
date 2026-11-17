package com.yovo.lib_bluetooth.internal.classic.platform.profiles.headset;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothDiagnosticsBus;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 灏?Android {@link BluetoothHeadset} 浠ｇ悊閫傞厤涓?SDK 棰嗗煙绫诲瀷銆?
 */
@SuppressLint("MissingPermission")
public final class YoCbtAndroidHeadsetPlatformProxyAdapter {

    private static final String TAG = "YoCbtHeadsetProxyAdapter";

    private final BluetoothHeadset headsetProxy;

    @NonNull
    private final BluetoothDiagnosticsBus diagnostics;

    /**
     * 鍒涘缓鍖呰缁欏畾 Android Profile 浠ｇ悊鐨勬柊閫傞厤鍣ㄣ€?
     *
     * @param proxy Android {@link BluetoothProfile} 浠ｇ悊锛岄』涓?
     *              {@link BluetoothHeadset} 瀹炰緥锛堝唴閮ㄥ己鍒惰浆鎹級
     * @param diagnostics 瑁呴厤鐐规敞鍏ョ殑璇婃柇鎬荤嚎
     * @throws IllegalArgumentException 鑻?{@code proxy} 涓嶆槸 {@link BluetoothHeadset}
     */
    public YoCbtAndroidHeadsetPlatformProxyAdapter(@NonNull BluetoothProfile proxy,
                                                   @NonNull BluetoothDiagnosticsBus diagnostics) {
        if (!(proxy instanceof BluetoothHeadset)) {
            throw new IllegalArgumentException(
                    "Proxy must be an instance of BluetoothHeadset, got " + proxy.getClass().getName());
        }
        this.headsetProxy = (BluetoothHeadset) proxy;
        this.diagnostics = diagnostics;
    }

    /**
     * 杩斿洖缁欏畾璁惧鐨勮繛鎺ョ姸鎬侊紝鏄犲皠涓?SDK 绫诲瀷銆?
     *
     * <p>濮旀墭 {@link BluetoothHeadset#getConnectionState(BluetoothDevice)}锛?
     * 骞跺皢 Android 鏁村瀷甯搁噺鏄犲皠涓?{@link YoCbtProfileConnectionState}銆?
     *
     * @param deviceId 鐩爣璁惧
     * @return 鏄犲皠鍚庣殑杩炴帴鐘舵€侊紙浠ｇ悊杩斿洖鏃犳硶璇嗗埆鍊兼椂涓?
     *         {@link YoCbtProfileConnectionState#UNKNOWN}锛?
     */
    @NonNull
    public YoCbtProfileConnectionState getConnectionState(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId, diagnostics);
        if (device == null) {
            return YoCbtProfileConnectionState.UNKNOWN;
        }
        int state = headsetProxy.getConnectionState(device);
        return mapConnectionState(state);
    }

    /**
     * 杩斿洖褰撳墠閫氳繃 Headset/HFP Profile 杩炴帴鐨勮澶囧垪琛ㄣ€?
     *
     * <p>濮旀墭 {@link BluetoothHeadset#getConnectedDevices()}锛屽苟灏嗘瘡涓?
     * Android {@link BluetoothDevice} 鏄犲皠涓?{@link YoCbtDeviceId}銆?
     *
     * @return 宸茶繛鎺ヨ澶?ID 鍒楄〃锛堟案涓嶄负 null锛屽彲鑳戒负绌猴級
     */
    @NonNull
    public List<YoCbtDeviceId> getConnectedDevices() {
        List<BluetoothDevice> androidDevices = headsetProxy.getConnectedDevices();
        List<YoCbtDeviceId> result = new ArrayList<>(androidDevices.size());
        for (BluetoothDevice device : androidDevices) {
            result.add(YoCbtDeviceId.fromAddress(device.getAddress()));
        }
        return result;
    }

    /**
     * 杩斿洖缁欏畾璁惧褰撳墠鐨勯煶棰戯紙SCO锛夌姸鎬併€?
     *
     * <p>濮旀墭 {@link BluetoothHeadset#getAudioState(BluetoothDevice)}锛?
     * 骞跺皢 Android 鏁村瀷甯搁噺鏄犲皠涓?{@link YoCbtHeadsetAudioState}銆?
     *
     * @param deviceId 鐩爣璁惧
     * @return 鏄犲皠鍚庣殑闊抽鐘舵€侊紙浠ｇ悊杩斿洖鏃犳硶璇嗗埆鍊兼椂涓?
     *         {@link YoCbtHeadsetAudioState#UNKNOWN}锛?
     */
    @NonNull
    public YoCbtHeadsetAudioState getAudioState(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId, diagnostics);
        if (device == null) {
            return YoCbtHeadsetAudioState.UNKNOWN;
        }
        int audioState;
        try {
            audioState = (int) BluetoothHeadset.class
                    .getMethod("getAudioState", BluetoothDevice.class)
                    .invoke(headsetProxy, device);
        } catch (ReflectiveOperationException | SecurityException reflectionBlocked) {
            // 閮ㄥ垎 ROM 鏈毚闇茶闅愯棌鏂规硶锛屾垨琚殣钘?API 绛栫暐鎷︽埅锛氶煶棰戠姸鎬佹寜濂戠害鎶モ€滄湭鐭モ€濓紝
            // 涓嶈兘鍋囪鎴?AUDIO_DISCONNECTED銆?
            YoBtLogger.w(TAG, "BluetoothHeadset.getAudioState() unavailable on this ROM", reflectionBlocked);
            return YoCbtHeadsetAudioState.UNKNOWN;
        }
        return mapAudioState(audioState);
    }

    /**
     * 妫€鏌ョ粰瀹氳澶囧綋鍓嶆槸鍚﹀凡杩炴帴闊抽锛圫CO锛夈€?
     *
     * <p>绛変环浜庢鏌?{@link #getAudioState} 鏄惁杩斿洖
     * {@link YoCbtHeadsetAudioState#AUDIO_CONNECTED}銆?
     *
     * @param deviceId 鐩爣璁惧
     * @return 闊抽宸茶繛鎺ユ椂涓?{@code true}锛屽惁鍒欎负 {@code false}
     */
    public boolean isAudioConnected(@NonNull YoCbtDeviceId deviceId) {
        return getAudioState(deviceId) == YoCbtHeadsetAudioState.AUDIO_CONNECTED;
    }

    /**
     * 鍦ㄦ寚瀹氳澶囦笂鍚姩璇煶璇嗗埆锛圚F 鎸囩ず锛夈€?
     *
     * <p>濮旀墭 {@link BluetoothHeadset#startVoiceRecognition(BluetoothDevice)}銆?
     *
     * @param deviceId 鐩爣璁惧
     * @return 鍛戒护鎴愬姛鍙戦€佹椂涓?{@code true}锛屽惁鍒欎负 {@code false}
     */
    public boolean startVoiceRecognition(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId, diagnostics);
        if (device == null) {
            YoBtLogger.w(TAG, "鍚姩璇煶璇嗗埆澶辫触锛氭棤娉曡В鏋愯澶?" + deviceId);
            return false;
        }
        try {
            return headsetProxy.startVoiceRecognition(device);
        } catch (Exception e) {
            diagnostics.recordException(TAG, "鍚姩璇煶璇嗗埆鏃惰澶?" + deviceId + " 鎶涘嚭寮傚父", e);
            return false;
        }
    }

    /**
     * 鍦ㄦ寚瀹氳澶囦笂鍋滄璇煶璇嗗埆锛圚F 鎸囩ず锛夈€?
     *
     * <p>濮旀墭 {@link BluetoothHeadset#stopVoiceRecognition(BluetoothDevice)}銆?
     *
     * @param deviceId 鐩爣璁惧
     * @return 鍛戒护鎴愬姛鍙戦€佹椂涓?{@code true}锛屽惁鍒欎负 {@code false}
     */
    public boolean stopVoiceRecognition(@NonNull YoCbtDeviceId deviceId) {
        BluetoothDevice device = toAndroidDevice(deviceId, diagnostics);
        if (device == null) {
            YoBtLogger.w(TAG, "鍋滄璇煶璇嗗埆澶辫触锛氭棤娉曡В鏋愯澶?" + deviceId);
            return false;
        }
        try {
            return headsetProxy.stopVoiceRecognition(device);
        } catch (Exception e) {
            diagnostics.recordException(TAG, "鍋滄璇煶璇嗗埆鏃惰澶?" + deviceId + " 鎶涘嚭寮傚父", e);
            return false;
        }
    }

    // 鈹€鈹€ 鍐呴儴鏄犲皠杈呭姪鏂规硶 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
     * 灏?Android {@link BluetoothProfile} 杩炴帴鐘舵€佹暣鍨嬭浆鎹负
     * SDK 鐨?{@link YoCbtProfileConnectionState} 鏋氫妇銆?
     */
    @NonNull
    private static YoCbtProfileConnectionState mapConnectionState(int state) {
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return YoCbtProfileConnectionState.DISCONNECTED;
            case BluetoothProfile.STATE_CONNECTING:
                return YoCbtProfileConnectionState.CONNECTING;
            case BluetoothProfile.STATE_CONNECTED:
                return YoCbtProfileConnectionState.CONNECTED;
            case BluetoothProfile.STATE_DISCONNECTING:
                return YoCbtProfileConnectionState.DISCONNECTING;
            default:
                YoBtLogger.w(TAG, "鏃犳硶璇嗗埆鐨勮繛鎺ョ姸鎬? " + state);
                return YoCbtProfileConnectionState.UNKNOWN;
        }
    }

    /**
     * 灏?Android {@link BluetoothHeadset} 闊抽鐘舵€佹暣鍨嬭浆鎹负
     * SDK 鐨?{@link YoCbtHeadsetAudioState} 鏋氫妇銆?
     */
    @NonNull
    private static YoCbtHeadsetAudioState mapAudioState(int audioState) {
        switch (audioState) {
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                return YoCbtHeadsetAudioState.AUDIO_DISCONNECTED;
            case BluetoothHeadset.STATE_AUDIO_CONNECTING:
                return YoCbtHeadsetAudioState.AUDIO_CONNECTING;
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                return YoCbtHeadsetAudioState.AUDIO_CONNECTED;
            default:
                YoBtLogger.w(TAG, "鏃犳硶璇嗗埆鐨勯煶棰戠姸鎬? " + audioState);
                return YoCbtHeadsetAudioState.UNKNOWN;
        }
    }

    /**
     * 灏?{@link YoCbtDeviceId} 杞崲涓?Android {@link BluetoothDevice}銆?
     * <p>
     * 浣跨敤 {@link android.bluetooth.BluetoothAdapter#getRemoteDevice(String)}锛?
     * 姝や负杞婚噺鎿嶄綔锛屾棤闇€娲昏穬鐨勮摑鐗欓€傞厤鍣ㄣ€?
     *
     * @param deviceId SDK 璁惧 ID
     * @return 瀵瑰簲鐨?Android BluetoothDevice锛涘湴鍧€鏃犳晥鏃朵负 {@code null}
     */
    @Nullable
    private static BluetoothDevice toAndroidDevice(@NonNull YoCbtDeviceId deviceId,
                                                              @NonNull BluetoothDiagnosticsBus diagnostics) {
        try {
            return android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    .getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            diagnostics.recordException(TAG, "鏃犳晥鐨勮澶囧湴鍧€: " + deviceId.value(), e);
            return null;
        }
    }
}

package com.yovo.lib_bluetooth.internal.classic.platform;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;

import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

/**
 * Android 平台整型状态 → 内部枚举的<b>唯一映射点</b>。
 *
 * <p>此前 profile 广播处理器、A2DP/Headset 代理适配器、配对加载器、配对广播适配器
 * 各自复制了一份同样的 {@code switch} 映射（{@code mapConnectionState}/{@code mapAudioState}/
 * {@code mapBondState}），散落在 5 个文件里。它们映射的常量完全一样、默认都落到 {@code UNKNOWN}，
 * 属于纯冗余逻辑。集中到这里后：</p>
 * <ul>
 *   <li>映射规则只有一份，改一处即全局一致（高内聚）；</li>
 *   <li>各适配器只保留「取原始 int + 调本类」，不再复制 switch（低耦合、去冗余）；</li>
 *   <li>可用纯单元测试覆盖（常量编译期内联，无需 Android 运行时）。</li>
 * </ul>
 */
public final class YoCbtAndroidStateMapper {

    private YoCbtAndroidStateMapper() {
    }

    /** {@link BluetoothProfile} 连接状态 → 内部连接状态。 */
    public static YoCbtProfileConnectionState connectionState(int state) {
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
                return YoCbtProfileConnectionState.UNKNOWN;
        }
    }

    /** {@link BluetoothHeadset} 音频（SCO）状态 → 内部音频状态。 */
    public static YoCbtHeadsetAudioState audioState(int audioState) {
        switch (audioState) {
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                return YoCbtHeadsetAudioState.AUDIO_DISCONNECTED;
            case BluetoothHeadset.STATE_AUDIO_CONNECTING:
                return YoCbtHeadsetAudioState.AUDIO_CONNECTING;
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                return YoCbtHeadsetAudioState.AUDIO_CONNECTED;
            default:
                return YoCbtHeadsetAudioState.UNKNOWN;
        }
    }

    /** {@link BluetoothDevice} 配对状态 → 内部配对状态。 */
    public static YoCbtBondState bondState(int androidBondState) {
        switch (androidBondState) {
            case BluetoothDevice.BOND_NONE:
                return YoCbtBondState.NONE;
            case BluetoothDevice.BOND_BONDING:
                return YoCbtBondState.BONDING;
            case BluetoothDevice.BOND_BONDED:
                return YoCbtBondState.BONDED;
            default:
                return YoCbtBondState.UNKNOWN;
        }
    }
}

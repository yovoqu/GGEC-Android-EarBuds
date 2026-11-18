package com.yovo.lib_bluetooth.internal.classic.platform;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;

import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import org.junit.Test;

/**
 * {@link YoCbtAndroidStateMapper} 的映射契约。
 *
 * <p>这里依赖的 {@code BluetoothProfile/BluetoothHeadset/BluetoothDevice} 常量都是编译期
 * 内联的 {@code public static final int}，因此纯 JVM 单测即可覆盖，不需要 Android 运行时。
 * 集中映射后，这是全模块唯一的「平台整型 → 内部枚举」真源，必须逐值钉死。</p>
 */
public final class YoCbtAndroidStateMapperTest {

    @Test
    public void connectionState_mapsEveryKnownValue() {
        assertEquals(YoCbtProfileConnectionState.DISCONNECTED,
                YoCbtAndroidStateMapper.connectionState(BluetoothProfile.STATE_DISCONNECTED));
        assertEquals(YoCbtProfileConnectionState.CONNECTING,
                YoCbtAndroidStateMapper.connectionState(BluetoothProfile.STATE_CONNECTING));
        assertEquals(YoCbtProfileConnectionState.CONNECTED,
                YoCbtAndroidStateMapper.connectionState(BluetoothProfile.STATE_CONNECTED));
        assertEquals(YoCbtProfileConnectionState.DISCONNECTING,
                YoCbtAndroidStateMapper.connectionState(BluetoothProfile.STATE_DISCONNECTING));
    }

    @Test
    public void connectionState_unknownFallsBackToUnknown() {
        assertEquals(YoCbtProfileConnectionState.UNKNOWN,
                YoCbtAndroidStateMapper.connectionState(9999));
    }

    @Test
    public void audioState_mapsEveryKnownValue() {
        assertEquals(YoCbtHeadsetAudioState.AUDIO_DISCONNECTED,
                YoCbtAndroidStateMapper.audioState(BluetoothHeadset.STATE_AUDIO_DISCONNECTED));
        assertEquals(YoCbtHeadsetAudioState.AUDIO_CONNECTING,
                YoCbtAndroidStateMapper.audioState(BluetoothHeadset.STATE_AUDIO_CONNECTING));
        assertEquals(YoCbtHeadsetAudioState.AUDIO_CONNECTED,
                YoCbtAndroidStateMapper.audioState(BluetoothHeadset.STATE_AUDIO_CONNECTED));
    }

    @Test
    public void audioState_unknownFallsBackToUnknown() {
        assertEquals(YoCbtHeadsetAudioState.UNKNOWN,
                YoCbtAndroidStateMapper.audioState(-1));
    }

    @Test
    public void bondState_mapsEveryKnownValue() {
        assertEquals(YoCbtBondState.NONE,
                YoCbtAndroidStateMapper.bondState(BluetoothDevice.BOND_NONE));
        assertEquals(YoCbtBondState.BONDING,
                YoCbtAndroidStateMapper.bondState(BluetoothDevice.BOND_BONDING));
        assertEquals(YoCbtBondState.BONDED,
                YoCbtAndroidStateMapper.bondState(BluetoothDevice.BOND_BONDED));
    }

    @Test
    public void bondState_unknownFallsBackToUnknown() {
        assertEquals(YoCbtBondState.UNKNOWN,
                YoCbtAndroidStateMapper.bondState(-1));
    }
}

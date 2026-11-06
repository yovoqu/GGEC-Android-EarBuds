package com.ggec.earbuds.platform.ai.voice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.port.AiAudioRoute;

import java.util.Objects;

/**
 * Android 输出路由探测。
 *
 * <p>只查"有没有私密输出设备"：蓝牙 A2DP/SCO、有线耳机/耳麦、USB 耳机。
 * 有任意一个在连接，就认为用户很可能戴在耳朵上，声音不会进麦克风。</p>
 *
 * <p>不查 {@code getCommunicationDevice()}：通话开始前通信设备还没被选定，
 * 查它只会得到 null。这里要的是"物理上有没有耳机"，用 {@code getDevices} 更稳。</p>
 */
public final class AndroidAudioRoute implements AiAudioRoute {

    private final Context context;

    public AndroidAudioRoute(@NonNull Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public boolean hasPrivateOutput() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // 老系统拿不到设备列表：保守当外放，走半双工。
            return false;
        }
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) {
            return false;
        }
        try {
            for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (isPrivate(device.getType())) {
                    return true;
                }
            }
        } catch (RuntimeException unavailable) {
            return false;
        }
        return false;
    }

    private static boolean isPrivate(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_HEADSET;
    }
}

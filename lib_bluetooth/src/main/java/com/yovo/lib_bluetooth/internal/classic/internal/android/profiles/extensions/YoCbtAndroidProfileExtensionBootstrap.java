package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.profiles.extensions.YoCbtProfileExtensionRegistry;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.avrcp.YoCbtAndroidAvrcpExtension;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.hearingaid.YoCbtAndroidHearingAidExtension;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.hid.YoCbtAndroidHidExtension;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.leaudio.YoCbtAndroidLeAudioExtension;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.map.YoCbtAndroidMapExtension;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.opp.YoCbtAndroidOppExtension;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.pan.YoCbtAndroidPanExtension;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.pbap.YoCbtAndroidPbapExtension;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android Profile 扩展引导注册，初始化各内置扩展。
 */

public final class YoCbtAndroidProfileExtensionBootstrap {

    private YoCbtAndroidProfileExtensionBootstrap() {
    }

    public static void registerAll(@NonNull YoCbtProfileExtensionRegistry registry,
                                   @NonNull Context context,
                                   @NonNull BluetoothAdapter adapter) {
        registry.register(new YoCbtAndroidHidExtension(context, adapter));
        registry.register(new YoCbtAndroidPanExtension(context, adapter));
        registry.register(new YoCbtAndroidPbapExtension(context, adapter));
        registry.register(new YoCbtAndroidMapExtension(adapter));
        registry.register(new YoCbtAndroidOppExtension(adapter));
        registry.register(new YoCbtAndroidHearingAidExtension(context, adapter));
        registry.register(new YoCbtAndroidLeAudioExtension(context, adapter));
        registry.register(new YoCbtAndroidAvrcpExtension(adapter));
    }
}

package com.ggec.earbuds.domain.device.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 设备控件的不可变快照。未接入协议控件或未上报状态时由 {@link #empty()} 表达。
 */
public record DeviceControls(
        EqPreset eqPreset,
        boolean customEqSaved,
        String customEqName,
        float[] customEqGains,
        SoundQuality soundQuality,
        boolean ldac,
        boolean volumeAdaptive,
        boolean wearDetect,
        boolean voiceWakeup,
        boolean gameMode,
        boolean fallAlert,
        boolean volumeRemind,
        boolean beginnerMode,
        boolean interpret,
        boolean dualConnection,
        Map<GestureSlot, GestureAction> gestures) {

    public DeviceControls {
        customEqName = customEqName == null ? "" : customEqName.strip();
        float[] gains = new float[10];
        if (customEqGains != null) {
            System.arraycopy(customEqGains, 0, gains, 0, Math.min(customEqGains.length, gains.length));
        }
        customEqGains = gains;
        EnumMap<GestureSlot, GestureAction> copy = new EnumMap<>(GestureSlot.class);
        if (gestures != null) {
            for (Map.Entry<GestureSlot, GestureAction> entry : gestures.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
        }
        gestures = Collections.unmodifiableMap(copy);
    }

    public static DeviceControls empty() {
        return new DeviceControls(
                null,
                false,
                "",
                new float[10],
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Collections.emptyMap());
    }

    public GestureAction gesture(GestureSlot slot) {
        return slot == null ? null : gestures.get(slot);
    }

    public boolean enabled(DeviceSwitch key) {
        return switch (key) {
            case LDAC -> ldac;
            case VOLUME_ADAPTIVE -> volumeAdaptive;
            case WEAR_DETECT -> wearDetect;
            case VOICE_WAKEUP -> voiceWakeup;
            case GAME_MODE -> gameMode;
            case FALL_ALERT -> fallAlert;
            case VOLUME_REMIND -> volumeRemind;
            case BEGINNER_MODE -> beginnerMode;
            case INTERPRET -> interpret;
            case DUAL_CONNECTION -> dualConnection;
        };
    }

    public DeviceControls withEqPreset(EqPreset preset) {
        return copy(preset, customEqSaved, customEqName, customEqGains, soundQuality, ldac,
                volumeAdaptive, wearDetect, voiceWakeup, gameMode, fallAlert, volumeRemind,
                beginnerMode, interpret, dualConnection, gestures);
    }

    public DeviceControls withCustomEq(String name, float[] gains) {
        return copy(EqPreset.CUSTOM, true, name, gains, soundQuality, ldac, volumeAdaptive,
                wearDetect, voiceWakeup, gameMode, fallAlert, volumeRemind, beginnerMode, interpret,
                dualConnection, gestures);
    }

    public DeviceControls withSoundQuality(SoundQuality quality) {
        return copy(eqPreset, customEqSaved, customEqName, customEqGains, quality, ldac,
                volumeAdaptive, wearDetect, voiceWakeup, gameMode, fallAlert, volumeRemind,
                beginnerMode, interpret, dualConnection, gestures);
    }

    public DeviceControls withSwitch(DeviceSwitch key, boolean value) {
        return copy(
                eqPreset,
                customEqSaved,
                customEqName,
                customEqGains,
                soundQuality,
                key == DeviceSwitch.LDAC ? value : ldac,
                key == DeviceSwitch.VOLUME_ADAPTIVE ? value : volumeAdaptive,
                key == DeviceSwitch.WEAR_DETECT ? value : wearDetect,
                key == DeviceSwitch.VOICE_WAKEUP ? value : voiceWakeup,
                key == DeviceSwitch.GAME_MODE ? value : gameMode,
                key == DeviceSwitch.FALL_ALERT ? value : fallAlert,
                key == DeviceSwitch.VOLUME_REMIND ? value : volumeRemind,
                key == DeviceSwitch.BEGINNER_MODE ? value : beginnerMode,
                key == DeviceSwitch.INTERPRET ? value : interpret,
                key == DeviceSwitch.DUAL_CONNECTION ? value : dualConnection,
                gestures);
    }

    public DeviceControls withGesture(GestureSlot slot, GestureAction action) {
        if (slot == null) {
            return this;
        }
        EnumMap<GestureSlot, GestureAction> next = new EnumMap<>(GestureSlot.class);
        next.putAll(gestures);
        if (action == null) {
            next.remove(slot);
        } else {
            next.put(slot, action);
        }
        return copy(eqPreset, customEqSaved, customEqName, customEqGains, soundQuality, ldac,
                volumeAdaptive, wearDetect, voiceWakeup, gameMode, fallAlert, volumeRemind,
                beginnerMode, interpret, dualConnection, next);
    }

    private static DeviceControls copy(
            EqPreset eqPreset,
            boolean customEqSaved,
            String customEqName,
            float[] customEqGains,
            SoundQuality soundQuality,
            boolean ldac,
            boolean volumeAdaptive,
            boolean wearDetect,
            boolean voiceWakeup,
            boolean gameMode,
            boolean fallAlert,
            boolean volumeRemind,
            boolean beginnerMode,
            boolean interpret,
            boolean dualConnection,
            Map<GestureSlot, GestureAction> gestures) {
        return new DeviceControls(eqPreset, customEqSaved, customEqName, customEqGains,
                soundQuality, ldac, volumeAdaptive, wearDetect, voiceWakeup, gameMode, fallAlert,
                volumeRemind, beginnerMode, interpret, dualConnection, gestures);
    }
}

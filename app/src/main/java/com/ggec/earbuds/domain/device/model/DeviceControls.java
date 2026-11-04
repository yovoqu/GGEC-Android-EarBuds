package com.ggec.earbuds.domain.device.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 设备控件的不可变快照。默认值只在 {@link #initial()} 定义一次。
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
        Objects.requireNonNull(eqPreset, "eqPreset");
        Objects.requireNonNull(soundQuality, "soundQuality");
        customEqName = customEqName == null ? "" : customEqName.strip();
        float[] gains = new float[10];
        if (customEqGains != null) {
            System.arraycopy(customEqGains, 0, gains, 0, Math.min(customEqGains.length, gains.length));
        }
        customEqGains = gains;
        EnumMap<GestureSlot, GestureAction> copy = new EnumMap<>(GestureSlot.class);
        if (gestures != null) {
            copy.putAll(gestures);
        }
        for (GestureSlot slot : GestureSlot.values()) {
            copy.putIfAbsent(slot, GestureAction.NONE);
        }
        gestures = Collections.unmodifiableMap(copy);
    }

    public static DeviceControls initial() {
        EnumMap<GestureSlot, GestureAction> gestures = new EnumMap<>(GestureSlot.class);
        gestures.put(GestureSlot.LONG, GestureAction.VOICE_ASSISTANT);
        gestures.put(GestureSlot.LONG_LEFT, GestureAction.VOICE_ASSISTANT);
        gestures.put(GestureSlot.LONG_RIGHT, GestureAction.VOICE_ASSISTANT);
        gestures.put(GestureSlot.SHORT, GestureAction.VOLUME_UP);
        gestures.put(GestureSlot.SHORT_LEFT, GestureAction.VOLUME_UP);
        gestures.put(GestureSlot.SHORT_RIGHT, GestureAction.VOLUME_UP);
        gestures.put(GestureSlot.DOUBLE_LEFT, GestureAction.NEXT);
        gestures.put(GestureSlot.DOUBLE_RIGHT, GestureAction.NEXT);
        gestures.put(GestureSlot.TAP_TWICE, GestureAction.PLAY_PAUSE);
        gestures.put(GestureSlot.TAP_TWICE_LEFT, GestureAction.PLAY_PAUSE);
        gestures.put(GestureSlot.TAP_TWICE_RIGHT, GestureAction.PLAY_PAUSE);
        gestures.put(GestureSlot.TAP_TRIPLE, GestureAction.VOICE_ASSISTANT);
        gestures.put(GestureSlot.TAP_TRIPLE_LEFT, GestureAction.VOICE_ASSISTANT);
        gestures.put(GestureSlot.TAP_TRIPLE_RIGHT, GestureAction.VOICE_ASSISTANT);
        return new DeviceControls(
                EqPreset.MUSIC,
                false,
                "",
                new float[10],
                SoundQuality.HIGH_FIDELITY,
                false,
                true,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                gestures);
    }

    public GestureAction gesture(GestureSlot slot) {
        GestureAction action = gestures.get(slot);
        return action == null ? GestureAction.NONE : action;
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
        EnumMap<GestureSlot, GestureAction> next = new EnumMap<>(GestureSlot.class);
        next.putAll(gestures);
        next.put(slot, action);
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

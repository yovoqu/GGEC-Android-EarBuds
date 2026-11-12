package com.ggec.earbuds.domain.device.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 设备控件的不可变快照。未接入协议控件或未上报状态时由 {@link #empty()} 表达。
 *
 * <p><b>契约：</b>{@code customEqName}、{@code customEqGains}、{@code gestures} 非空；
 * 「没有名字」写成空串，「没有自定义曲线」写成 {@link #CUSTOM_EQ_BANDS} 个 0，
 * 「没有手势绑定」写成空表，都由调用方显式给出，本记录不把 null 折叠成默认值。
 * {@code eqPreset}/{@code soundQuality} 为 null 表示该控件尚未接入协议通道，
 * 是有语义的缺失，不是兜底。</p>
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

    /** 自定义 EQ 的频段数：协议与界面共用同一条曲线，长度不符即调用方装配错误。 */
    public static final int CUSTOM_EQ_BANDS = 10;

    public DeviceControls {
        customEqName = Objects.requireNonNull(customEqName, "customEqName").strip();
        customEqGains = copyGains(customEqGains);
        EnumMap<GestureSlot, GestureAction> copy = new EnumMap<>(GestureSlot.class);
        for (Map.Entry<GestureSlot, GestureAction> entry
                : Objects.requireNonNull(gestures, "gestures").entrySet()) {
            GestureSlot slot = Objects.requireNonNull(entry.getKey(), "gestures key");
            copy.put(slot, Objects.requireNonNull(entry.getValue(), "gestures[" + slot + "]"));
        }
        gestures = Collections.unmodifiableMap(copy);
    }

    /** 复制并校验曲线：长度必须等于 {@link #CUSTOM_EQ_BANDS}，不做截断也不补 0。 */
    private static float[] copyGains(float[] gains) {
        Objects.requireNonNull(gains, "customEqGains");
        if (gains.length != CUSTOM_EQ_BANDS) {
            throw new IllegalArgumentException(
                    "customEqGains must have " + CUSTOM_EQ_BANDS + " bands but has " + gains.length);
        }
        float[] copy = new float[CUSTOM_EQ_BANDS];
        System.arraycopy(gains, 0, copy, 0, CUSTOM_EQ_BANDS);
        return copy;
    }

    public static DeviceControls empty() {
        return new DeviceControls(
                null,
                false,
                "",
                new float[CUSTOM_EQ_BANDS],
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

    /** {@code slot} 非空：手势槽位是封闭枚举，null 没有业务含义。 */
    public GestureAction gesture(GestureSlot slot) {
        return gestures.get(Objects.requireNonNull(slot, "slot"));
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

    /** {@code name} 非空；{@code gains} 必须正好 {@link #CUSTOM_EQ_BANDS} 个频段。 */
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

    /**
     * 绑定或解绑一个手势。{@code slot} 非空；{@code action} 为 null 表示解绑（协议里该槽位没有动作），
     * 是有语义的删除，不是缺失值。
     */
    public DeviceControls withGesture(GestureSlot slot, GestureAction action) {
        Objects.requireNonNull(slot, "slot");
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

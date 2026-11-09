package com.ggec.earbuds.domain.device.policy;

import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.EqPreset;
import com.ggec.earbuds.domain.device.model.GestureAction;
import com.ggec.earbuds.domain.device.model.GestureSlot;
import com.ggec.earbuds.domain.device.model.SoundQuality;

import java.util.List;

/** 当前机型允许出现的选项。只返回枚举，不带文案。 */
public final class DeviceControlPolicy {

    private static final List<GestureAction> LONG_ACTIONS = List.of(
            GestureAction.RECORD,
            GestureAction.VOICE_ASSISTANT,
            GestureAction.EQ_SWITCH,
            GestureAction.GAME_MODE,
            GestureAction.NONE);

    private static final List<GestureAction> TAP_ACTIONS = List.of(
            GestureAction.PLAY_PAUSE,
            GestureAction.VOLUME_UP,
            GestureAction.VOLUME_DOWN,
            GestureAction.PREVIOUS,
            GestureAction.NEXT,
            GestureAction.NONE);

    private static final List<GestureAction> PRESS_ACTIONS = List.of(
            GestureAction.VOICE_ASSISTANT,
            GestureAction.EQ_SWITCH,
            GestureAction.GAME_MODE,
            GestureAction.PREVIOUS,
            GestureAction.NEXT,
            GestureAction.VOLUME_UP,
            GestureAction.VOLUME_DOWN,
            GestureAction.NONE);

    private static final List<SoundQuality> QUALITIES = List.of(
            SoundQuality.HIGH_FIDELITY,
            SoundQuality.ENDURANCE);

    private DeviceControlPolicy() {
    }

    public static List<EqPreset> presets(DeviceProfile profile) {
        if (profile == null) {
            return List.of();
        }
        if (profile.supports(Capability.CUSTOM_EQ)) {
            return List.of(EqPreset.MUSIC, EqPreset.MOVIE, EqPreset.NEWS, EqPreset.ELDER, EqPreset.SPORT);
        }
        return List.of(EqPreset.MUSIC, EqPreset.MOVIE, EqPreset.NEWS, EqPreset.ELDER);
    }

    public static List<SoundQuality> qualities() {
        return QUALITIES;
    }

    public static List<GestureAction> actions(GestureSlot slot) {
        return switch (slot) {
            case LONG, LONG_LEFT, LONG_RIGHT -> LONG_ACTIONS;
            case TAP_TWICE, TAP_TWICE_LEFT, TAP_TWICE_RIGHT -> TAP_ACTIONS;
            default -> PRESS_ACTIONS;
        };
    }
}

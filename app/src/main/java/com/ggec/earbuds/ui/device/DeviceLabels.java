package com.ggec.earbuds.ui.device;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.EqPreset;
import com.ggec.earbuds.domain.device.model.GestureAction;
import com.ggec.earbuds.domain.device.model.SoundQuality;

/** 领域取值到文案资源的唯一映射。 */
public final class DeviceLabels {

    private DeviceLabels() {
    }

    public static int eq(EqPreset preset) {
        return switch (preset) {
            case MUSIC -> R.string.device_eq_music;
            case MOVIE -> R.string.device_eq_movie;
            case NEWS -> R.string.device_eq_news;
            case ELDER -> R.string.device_eq_elder;
            case SPORT -> R.string.device_eq_sport;
            case CUSTOM -> R.string.device_eq_custom;
        };
    }

    public static int eqSubtitle(EqPreset preset) {
        return switch (preset) {
            case MOVIE -> R.string.device_eq_sub_movie;
            case NEWS -> R.string.device_eq_sub_news;
            case ELDER -> R.string.device_eq_sub_elder;
            case SPORT -> R.string.device_eq_sub_sport;
            case MUSIC, CUSTOM -> R.string.device_eq_sub_music;
        };
    }

    public static int quality(SoundQuality quality) {
        return switch (quality) {
            case HIGH_FIDELITY -> R.string.device_quality_hifi;
            case ENDURANCE -> R.string.device_quality_endurance;
        };
    }

    public static int gesture(GestureAction action) {
        return switch (action) {
            case RECORD -> R.string.device_gesture_record;
            case VOICE_ASSISTANT -> R.string.device_gesture_voice;
            case EQ_SWITCH -> R.string.device_gesture_eq;
            case GAME_MODE -> R.string.device_gesture_game;
            case PLAY_PAUSE -> R.string.device_gesture_play;
            case PREVIOUS -> R.string.device_gesture_previous;
            case NEXT -> R.string.device_gesture_next;
            case VOLUME_UP -> R.string.device_gesture_volume_up;
            case VOLUME_DOWN -> R.string.device_gesture_volume_down;
            case NONE -> R.string.device_gesture_none;
        };
    }
}

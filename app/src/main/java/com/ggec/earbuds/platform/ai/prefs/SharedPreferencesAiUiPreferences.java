package com.ggec.earbuds.platform.ai.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.port.AiUiPreferences;

import java.util.Objects;

/**
 * 用独立的 SharedPreferences 存设置页的界面偏好。
 *
 * <p>只放极小的、可丢的视图状态（当前分段）。写入用 {@code apply()}，不 fsync、不阻塞，
 * 和设置文档（需要原子写 + fsync）彻底分开。</p>
 */
public final class SharedPreferencesAiUiPreferences implements AiUiPreferences {

    private static final String FILE = "ai_ui_preferences";
    private static final String KEY_SETTINGS_MODE = "settings_mode";

    private final SharedPreferences preferences;

    public SharedPreferencesAiUiPreferences(@NonNull Context context) {
        this.preferences = Objects.requireNonNull(context, "context")
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public AiInteractionMode lastSettingsMode(@NonNull AiInteractionMode fallback) {
        String stored = preferences.getString(KEY_SETTINGS_MODE, null);
        AiInteractionMode mode = AiInteractionMode.fromName(stored);
        return mode == null ? fallback : mode;
    }

    @Override
    public void setLastSettingsMode(@NonNull AiInteractionMode mode) {
        preferences.edit().putString(KEY_SETTINGS_MODE, mode.name()).apply();
    }
}

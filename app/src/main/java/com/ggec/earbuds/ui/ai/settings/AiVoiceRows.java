package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

/** 语音面板的固定行规格。只描述每一行长什么样，不持有控件或业务状态。 */
final class AiVoiceRows {

    private final Context context;
    private final AiTheme theme;

    AiVoiceRows(@NonNull Context context, @NonNull AiTheme theme) {
        this.context = context;
        this.theme = theme;
    }

    @NonNull
    ListPrefixSpec chatPrefix() {
        return ListPrefixSpec.iconTinted(R.drawable.ic_ai_chat, theme.text2);
    }

    @NonNull
    ListPrefixSpec keyPrefix() {
        return ListPrefixSpec.iconTinted(R.drawable.ic_ai_key, theme.text2);
    }

    @NonNull
    ListTextSpec modelTitle() {
        return ListTextSpec.of(context.getString(R.string.ai_settings_voice_model));
    }

    @NonNull
    ListTextSpec urlTitle() {
        return ListTextSpec.of(context.getString(R.string.ai_settings_voice_url));
    }

    @NonNull
    ListTextSpec fetchTitle() {
        return ListTextSpec.of(context.getString(R.string.ai_settings_voice_fetch));
    }

    @NonNull
    ListTextSpec realtimeTitle() {
        return ListTextSpec.of(context.getString(R.string.ai_settings_voice_realtime_model));
    }

    @NonNull
    ListTextSpec dialectTitle() {
        return ListTextSpec.of(context.getString(R.string.ai_settings_voice_dialect));
    }

    @NonNull
    ListTextSpec keyTitle() {
        return ListTextSpec.of(context.getString(R.string.ai_settings_voice_key));
    }

    @NonNull
    ListSuffixSpec fetchAction() {
        return ListSuffixSpec.textAndArrow(
                context.getString(R.string.ai_settings_voice_fetch_action));
    }

    @NonNull
    ListSuffixSpec fetching() {
        return ListSuffixSpec.text(context.getString(R.string.ai_settings_fetching));
    }

    @NonNull
    ListSuffixSpec keyState(@NonNull AiSettingsUiState.Voice voice) {
        return voice.configured()
                ? ListSuffixSpec.text(context.getString(R.string.ai_settings_voice_key_set))
                : ListSuffixSpec.text(context.getString(R.string.ai_settings_voice_key_empty),
                theme.brand);
    }

    static void detach(@NonNull View view) {
        if (view.getParent() instanceof ViewGroup parent) {
            parent.removeView(view);
        }
    }
}

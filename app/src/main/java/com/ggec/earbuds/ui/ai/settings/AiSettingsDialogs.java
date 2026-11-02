package com.ggec.earbuds.ui.ai.settings;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.List;

/** 设置页的菜单与输入对话框。只负责展示和把确认结果交给调用方。 */
final class AiSettingsDialogs {

    private static final int MENU_ADD_PLATFORM = -1;
    private static final int MAX_VOICE_KEY_LENGTH = 256;
    private static final int MAX_VOICE_URL_LENGTH = 256;

    private final AppCompatActivity activity;
    private final YoComponents components;

    AiSettingsDialogs(@NonNull AppCompatActivity activity, @NonNull YoComponents components) {
        this.activity = activity;
        this.components = components;
    }

    void showPlatformMenu(@NonNull View anchor,
                          @NonNull List<AiSettingsUiState.PlatformRow> platforms,
                          @NonNull Listener listener) {
        YoMenu.Builder builder = YoMenu.create(activity).setAnchorView(anchor);
        for (int i = 0; i < platforms.size(); i++) {
            AiSettingsUiState.PlatformRow row = platforms.get(i);
            builder.addItem(i, row.current()
                    ? row.name() + " · " + activity.getString(R.string.ai_settings_platform_current)
                    : row.name());
        }
        builder.addItem(MENU_ADD_PLATFORM, activity.getString(R.string.ai_settings_add_platform));
        builder.setOnItemClickListener(id -> {
            if (id == MENU_ADD_PLATFORM) {
                listener.onAddPlatform();
            } else if (id >= 0 && id < platforms.size()) {
                listener.onOpenPlatform(platforms.get(id).platformId());
            }
        });
        YoMenu menu = builder.build();
        menu.setOnDismissListener(menu::destroy);
        menu.show();
    }

    void editVoiceKey(@NonNull AiSettingsUiState.Voice voice, @NonNull Listener listener) {
        YoTextInput field = YoTextInput.create(activity)
                .setPlaceholder(activity.getString(R.string.ai_settings_voice_key_dialog_hint))
                .setMaxLength(MAX_VOICE_KEY_LENGTH)
                .build();
        YoDialog.Action cancel = YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null);
        YoDialog.Action clear = YoDialog.Action.neutral(
                activity.getString(R.string.ai_settings_voice_key_clear),
                () -> listener.onVoiceKey(""));
        YoDialog.Action save = YoDialog.Action.positive(activity.getString(R.string.ai_settings_save),
                () -> listener.onVoiceKey(String.valueOf(field.getText())));
        YoDialog.Builder dialog = YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setButtonDirection(YoDialog.ButtonDirection.HORIZONTAL)
                .setTitle(activity.getString(R.string.ai_settings_voice_key_dialog_title, voice.modelName()));
        if (voice.configured()) {
            dialog.setActions(cancel, clear, save);
        } else {
            dialog.setActions(cancel, save);
        }
        dialog.show();
    }

    void editVoiceUrl(@NonNull AiSettingsUiState.Voice voice, @NonNull Listener listener) {
        YoTextInput field = YoTextInput.create(activity)
                .setText(voice.baseUrl())
                .setPlaceholder(activity.getString(R.string.ai_settings_voice_url_hint))
                .setMaxLength(MAX_VOICE_URL_LENGTH)
                .build();
        YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setButtonDirection(YoDialog.ButtonDirection.HORIZONTAL)
                .setTitle(activity.getString(R.string.ai_settings_voice_url_dialog_title))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null),
                        YoDialog.Action.neutral(activity.getString(R.string.ai_settings_voice_url_reset),
                                () -> listener.onVoiceBaseUrl("")),
                        YoDialog.Action.positive(activity.getString(R.string.ai_settings_save),
                                () -> listener.onVoiceBaseUrl(String.valueOf(field.getText())))
                )
                .show();
    }

    void editPrompt(@NonNull String prompt, @NonNull Listener listener) {
        YoTextInput field = YoTextInput.create(activity)
                .setText(prompt)
                .setPlaceholder(activity.getString(R.string.ai_settings_prompt_dialog_hint))
                .setMaxLength(AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH)
                .setShowCounter(true)
                .setShowErrorEnabled(true)
                .setMultiline(true, 4, 8)
                .build();
        YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(activity.getString(R.string.ai_settings_prompt_dialog_title))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.ai_settings_save),
                                () -> listener.onPrompt(String.valueOf(field.getText())))
                )
                .show();
    }

    void showAddPlatform(@NonNull List<AiPlatformTemplate> templates, @NonNull Listener listener) {
        if (templates.isEmpty()) {
            YoToast.show(activity, activity.getString(R.string.ai_settings_add_platform_empty));
            return;
        }
        AiAddPlatformDialog.show(activity, templates,
                template -> listener.onCreatePlatform(template.id()));
    }

    interface Listener {
        void onOpenPlatform(@NonNull String platformId);
        void onCreatePlatform(@NonNull String templateId);
        void onAddPlatform();
        void onVoiceKey(@NonNull String key);
        void onVoiceBaseUrl(@NonNull String baseUrl);
        void onPrompt(@NonNull String prompt);
    }
}

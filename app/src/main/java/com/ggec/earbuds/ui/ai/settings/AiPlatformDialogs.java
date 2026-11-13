package com.ggec.earbuds.ui.ai.settings;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
import com.ggec.earbuds.ui.ai.AiDialogs;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.navigation.YoMenu;

/** 平台页的菜单与输入对话框。只负责展示和回调。 */
final class AiPlatformDialogs {

    private static final int MENU_TEST = 31;
    private static final int MENU_DELETE = 33;

    private final AppCompatActivity activity;
    private final YoComponents components;

    AiPlatformDialogs(@NonNull AppCompatActivity activity, @NonNull YoComponents components) {
        this.activity = activity;
        this.components = components;
    }

    void showModelMenu(@NonNull View anchor, @NonNull String modelId, @NonNull Listener listener) {
        YoMenu menu = YoMenu.create(activity)
                .addItem(MENU_TEST, activity.getString(R.string.ai_settings_model_menu_test))
                .setAnchorView(anchor)
                .setOnItemClickListener(id -> {
                    if (id == MENU_TEST) {
                        listener.onTestModel(modelId);
                    } else if (id == MENU_DELETE) {
                        listener.onDeleteModel(modelId);
                    }
                })
                .build();
        menu.addDestructiveItem(MENU_DELETE, activity.getString(R.string.ai_delete));
        components.own(menu::destroy);
        menu.show();
    }

    void confirmDelete(@NonNull Listener listener) {
        AiDialogs.confirm(activity,
                activity.getString(R.string.ai_settings_delete_title),
                activity.getString(R.string.ai_settings_delete_message),
                activity.getString(R.string.ai_settings_delete_platform), true,
                listener::onDeletePlatform);
    }

    void showManualAdd(@NonNull Listener listener) {
        YoTextInput field = YoTextInput.create(activity)
                .setPlaceholder(activity.getString(R.string.ai_settings_add_model_hint))
                .setMaxLength(AiModelIdPolicy.MAX_LENGTH)
                .setShowErrorEnabled(true)
                .build();
        YoDialog created = YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setButtonDirection(YoDialog.ButtonDirection.HORIZONTAL)
                .setTitle(activity.getString(R.string.ai_settings_add_model))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.ai_settings_create),
                                () -> listener.onAddManualModel(String.valueOf(field.getText())))
                )
                .build();
        components.own(field::destroy);
        components.own(created::destroy);
        created.show();
    }

    interface Listener {
        void onAddManualModel(@NonNull String modelId);
        void onTestModel(@NonNull String modelId);
        void onDeleteModel(@NonNull String modelId);
        void onDeletePlatform();
    }
}

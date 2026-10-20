package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.ui.ai.AiKeyboard;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoSegmentButton;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.List;

/**
 * 添加自定义平台：用默认分段按钮选兼容类型，再用默认输入框填名称、服务地址和 API Key。
 * 提交被拒绝时对话框保持打开，错误显示在对应的输入框下面。
 */
final class AiAddPlatformDialog {

    interface Submit {
        @Nullable
        AiValidationError submit(@NonNull String templateId, @NonNull String name, @NonNull String baseUrl,
                                 @NonNull String apiKey);
    }

    private AiAddPlatformDialog() {
    }

    /**
     * @param owner 页面级释放集合：对话框打开时页面先销毁，这 4 个库组件也能被回收；
     *              对话框正常关闭时同样立即销毁（YoUI 的 destroy 幂等）
     */
    static void show(@NonNull Context context, @NonNull YoComponents owner,
                     @NonNull List<AiPlatformTemplate> templates, @NonNull Submit submit) {
        String[] selected = {templates.get(0).id()};
        YoSegmentButton.Builder segments = YoSegmentButton.create(context);
        for (int i = 0; i < templates.size(); i++) {
            segments.addSegment(i, label(context, templates.get(i)));
        }
        YoSegmentButton type = segments
                .setSelectedIndex(0)
                .setOnSelectionChangeListener(index -> selected[0] = templates.get(index).id())
                .build();
        owner.own(type::destroy);

        YoTextInput name = field(context, context.getString(R.string.ai_custom_name_hint), 40,
                YoTextInput.InputType.TEXT);
        YoTextInput url = field(context, context.getString(R.string.ai_custom_url_hint), 300,
                YoTextInput.InputType.TEXT);
        YoTextInput key = field(context, context.getString(R.string.ai_custom_key_hint), 512,
                YoTextInput.InputType.PASSWORD);
        owner.own(name::destroy);
        owner.own(url::destroy);
        owner.own(key::destroy);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(type.getView(), match());
        int gap = AiTheme.of(context).dp(AiTheme.SPACE_M);
        for (YoTextInput input : new YoTextInput[]{name, url, key}) {
            LinearLayout.LayoutParams params = match();
            params.topMargin = gap;
            content.addView(input.getView(), params);
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean[] keepOpen = {false};
        YoDialog.create(context)
                .setTitle(context.getString(R.string.ai_custom_dialog_title))
                .setContentView(scroll)
                .setNegativeButton(context.getString(R.string.ai_cancel), null)
                .setPositiveButton(context.getString(R.string.ai_add), () -> {
                    AiValidationError error = submit.submit(selected[0], name.getText().toString(),
                            url.getText().toString(), key.getText().toString());
                    if (error != null) {
                        String message = context.getString(AiMessages.validation(error));
                        switch (error) {
                            case NAME_EMPTY -> name.showError(message);
                            case BASE_URL_REQUIRED, BASE_URL_INVALID, BASE_URL_NOT_HTTPS -> url.showError(message);
                            default -> key.showError(message);
                        }
                        keepOpen[0] = true;
                    }
                })
                .setOnWillDismissListener(cause -> {
                    if (cause == YoDialog.DismissCause.ACTION && keepOpen[0]) {
                        keepOpen[0] = false;
                        return false;
                    }
                    return true;
                })
                .setOnDismissListener(() -> {
                    type.destroy();
                    name.destroy();
                    url.destroy();
                    key.destroy();
                    AiKeyboard.hide(context);
                })
                .show();
    }

    @NonNull
    private static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static YoTextInput field(Context context, String hint, int maxLength, YoTextInput.InputType type) {
        return YoTextInput.create(context)
                .setPlaceholder(hint)
                .setInputType(type)
                .setMaxLength(maxLength)
                .setShowCancel(true)
                .setShowErrorEnabled(true)
                .build();
    }

    private static String label(Context context, AiPlatformTemplate template) {
        return switch (template.id()) {
            case "openai-compatible" -> context.getString(R.string.ai_platform_custom_openai);
            case "anthropic-compatible" -> context.getString(R.string.ai_platform_custom_anthropic);
            default -> template.name();
        };
    }
}

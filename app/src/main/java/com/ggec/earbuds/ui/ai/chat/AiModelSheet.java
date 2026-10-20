package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.common.AiFormat;
import com.ggec.earbuds.ui.ai.common.AiTexts;
import com.yohu.youi.api.containers.YoSheet;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

/**
 * 模型选择面板：按平台分组列出能用的模型，当前模型打勾，测过速的显示首字延迟。
 * 面板内容直接用 {@code YoList}，行样式全部走默认。
 */
final class AiModelSheet {

    interface Listener {
        void onChoose(@NonNull String platformId, @NonNull String modelId, @NonNull String displayName);

        void onManage();

        void onClosed();
    }

    private final AiTheme theme;
    private final Listener listener;
    private final AiListSheet sheet;

    AiModelSheet(@NonNull AiTheme theme, @NonNull Listener listener) {
        this.theme = theme;
        this.listener = listener;
        this.sheet = new AiListSheet(theme, theme.context.getString(R.string.ai_model_sheet_title),
                YoSheet.Height.large(), null, listener::onClosed);
    }

    void show() {
        sheet.show();
    }

    void dismiss() {
        sheet.dismiss();
    }

    boolean isShowing() {
        return sheet.isShowing();
    }

    void destroy() {
        sheet.destroy();
    }

    void update(@NonNull AiModelSheetUi ui) {
        sheet.resetRows();
        if (ui.empty()) {
            sheet.addGroupFooter(theme.context.getString(R.string.ai_model_sheet_empty));
        }
        for (AiModelSheetUi.Group group : ui.groups()) {
            sheet.addGroupHeader(group.platformName());
            for (AiModelSheetUi.Row row : group.rows()) {
                String subtitle = subtitle(row);
                sheet.addItem(
                        row.selected() ? ListPrefixSpec.icon(R.drawable.ic_ai_check) : ListPrefixSpec.none(),
                        subtitle.isEmpty() ? ListTextSpec.of(row.name()) : ListTextSpec.of(row.name(), subtitle),
                        ListSuffixSpec.none(),
                        () -> listener.onChoose(group.platformId(), row.modelId(), row.name()));
            }
        }
        sheet.addItem(ListPrefixSpec.icon(R.drawable.ic_ai_settings),
                ListTextSpec.of(theme.context.getString(R.string.ai_model_sheet_manage)),
                ListSuffixSpec.arrow(), listener::onManage);
    }

    private String subtitle(@NonNull AiModelSheetUi.Row row) {
        String window = AiTexts.contextWindow(theme.context, row.contextWindow());
        if (row.firstTokenMillis() < 0) {
            return window;
        }
        return window.isEmpty()
                ? AiFormat.duration(row.firstTokenMillis())
                : window + " · " + AiFormat.duration(row.firstTokenMillis());
    }
}

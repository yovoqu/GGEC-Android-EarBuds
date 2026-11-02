package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.Locale;

/** 文本模式面板：身份卡和回答风格。控件随页面创建一次。 */
final class AiTextModePanel {

    interface Listener {
        void onOpenPlatform();
        void onEditPrompt();
    }

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final TextView badge;
    private final YoList identityList;
    private final YoList promptList;
    private View anchor;

    @Nullable
    private String identitySignature;
    @Nullable
    private String promptSignature;

    AiTextModePanel(@NonNull Context context, @NonNull AiTheme theme,
                    @NonNull YoComponents components, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        this.view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);

        badge = newBadge();
        identityList = YoList.create(context)
                .setOnItemClickListener(position -> listener.onOpenPlatform())
                .build();
        components.own(identityList::destroy);
        identityList.addItem(ListPrefixSpec.custom(badge),
                ListTextSpec.of("", ""), ListSuffixSpec.arrow());
        anchor = identityList.getView();
        addCard(identityList, AiTheme.SPACE_L);

        TextView title = theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_settings_section_style);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        titleParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
        titleParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        titleParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        view.addView(title, titleParams);

        promptList = YoList.create(context)
                .setOnItemClickListener(position -> listener.onEditPrompt())
                .build();
        components.own(promptList::destroy);
        promptList.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_text, theme.text2),
                ListTextSpec.of(context.getString(R.string.ai_settings_param_prompt), ""),
                ListSuffixSpec.arrow());
        addCard(promptList, 0);
    }

    @NonNull
    View view() {
        return view;
    }

    @NonNull
    View anchor() {
        return anchor;
    }

    void bind(@NonNull AiSettingsUiState.Identity identity, @NonNull AiSettingsUiState.Style style) {
        bindIdentity(identity);
        bindPrompt(style);
    }

    void setVisible(boolean visible) {
        // GONE 才不参与测量：两个面板高度不同，非当前面板若只 INVISIBLE，共用槽会被撑到
        // 较高那块的高度，切换与内容变化时就表现为整页上下跳。
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void bindIdentity(@NonNull AiSettingsUiState.Identity identity) {
        String signature = identity.platformName() + "\u0000" + identity.modelName()
                + "\u0000" + identity.emptyTitle() + "\u0000" + identity.emptyHint();
        if (signature.equals(identitySignature)) {
            return;
        }
        identitySignature = signature;
        detach(badge);
        boolean empty = identity.empty();
        badge.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) {
            badge.setText(firstLetter(identity.platformName()));
        }
        ListTextSpec text = empty
                ? ListTextSpec.of(context.getString(identity.emptyTitle()),
                context.getString(identity.emptyHint()))
                : ListTextSpec.of(identity.platformName(), identity.modelName());
        ListSuffixSpec suffix = empty
                ? ListSuffixSpec.arrow()
                : ListSuffixSpec.textAndArrow(context.getString(R.string.ai_settings_platform_change));
        identityList.clear();
        identityList.addItem(empty ? ListPrefixSpec.none() : ListPrefixSpec.custom(badge),
                text, suffix);
        anchor = identityList.getView();
    }

    private void bindPrompt(@NonNull AiSettingsUiState.Style style) {
        String preview = style.promptPreview().resolve(context);
        if (preview.equals(promptSignature)) {
            return;
        }
        promptSignature = preview;
        promptList.clear();
        promptList.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_text, theme.text2),
                ListTextSpec.of(context.getString(R.string.ai_settings_param_prompt), preview),
                ListSuffixSpec.arrow());
    }

    private TextView newBadge() {
        TextView created = theme.text(AiTheme.TEXT_HEADLINE - 4, theme.brand, AiTheme.Weight.MEDIUM);
        created.setGravity(Gravity.CENTER);
        created.setBackground(theme.rounded(theme.brandSoft, AiTheme.RADIUS_ROW));
        created.setMinWidth(theme.dp(AiTheme.TOUCH_TARGET));
        created.setMinHeight(theme.dp(AiTheme.TOUCH_TARGET));
        return created;
    }

    private void addCard(@NonNull YoList list, float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(topMarginDp);
        view.addView(list.getView(), params);
    }

    private static void detach(@NonNull View view) {
        if (view.getParent() instanceof ViewGroup parent) {
            parent.removeView(view);
        }
    }

    @NonNull
    private static String firstLetter(@Nullable String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        return trimmed.substring(0, end).toUpperCase(Locale.ROOT);
    }
}

package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.yohu.youi.api.controls.YoTextInput;

/** 平台模型区：搜索、测速动作和模型行。控件只创建一次。 */
final class PlatformModelsSection {

    interface Listener {
        void onRunOrStopSpeed();
        void onFetchModels();
        void onAddManualModel();
        void onSearch(@NonNull String query);
        void onSelectModel(@NonNull String modelId);
        void onModelMenu(@NonNull View anchor, @NonNull String modelId);
    }

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final TextView title;
    private final TextView action;
    private final TextView summary;
    private final YoTextInput search;
    private final YoList list;
    @Nullable
    private AiPlatformUiState.Models current;
    @Nullable
    private String signature;
    @Nullable
    private String shownQuery;

    PlatformModelsSection(@NonNull Context context, @NonNull AiTheme theme,
                          @NonNull YoComponents components, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        title = AiSectionHeader.title(theme);
        action = AiSectionHeader.action(theme);
        view.addView(AiSectionHeader.withAction(theme, context, title, action,
                () -> {
                    if (current != null && current.count() == 0) {
                        listener.onFetchModels();
                    } else {
                        listener.onRunOrStopSpeed();
                    }
                }), matchWrap());
        summary = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        summary.setVisibility(View.GONE);
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        summaryParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        summaryParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
        view.addView(summary, summaryParams);
        search = YoTextInput.create(context)
                .setPlaceholder(context.getString(R.string.ai_settings_models_search_hint))
                .setSize(YoTextInput.Size.SMALL)
                .setOnValueChangeListener(value -> listener.onSearch(value.toString()))
                .build();
        components.own(search::destroy);
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        searchParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        searchParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
        view.addView(search.getView(), searchParams);
        list = YoList.create(context).setOnItemClickListener(this::onRow).build();
        components.own(list::destroy);
        view.addView(list.getView(), matchWrap());
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull AiPlatformUiState.Models next) {
        current = next;
        title.setText(context.getString(R.string.ai_settings_models_section) + " · " + next.count());
        action.setText(next.count() == 0
                ? context.getString(R.string.ai_settings_models_fetch) : next.actionLabel());
        summary.setVisibility(next.summary() == null ? View.GONE : View.VISIBLE);
        if (next.summary() != null) {
            summary.setText(next.summary());
        }
        if (shownQuery == null || !next.query().equals(shownQuery)) {
            shownQuery = next.query();
            search.setText(next.query());
        }
        search.getView().setVisibility(next.searchable() ? View.VISIBLE : View.GONE);
        String nextSignature = signature(next);
        if (nextSignature.equals(signature)) {
            return;
        }
        signature = nextSignature;
        list.clear();
        for (AiPlatformUiState.Row row : next.rows()) {
            String facts = row.state() == AiPlatformUiState.RowState.TESTING
                    ? context.getString(R.string.ai_settings_speed_testing)
                    : String.join(" · ", row.facts());
            ListTextSpec text = facts.isEmpty()
                    ? ListTextSpec.of(row.name()) : ListTextSpec.of(row.name(), facts);
            list.addItem(ListPrefixSpec.iconTinted(
                            row.selected() ? R.drawable.ic_ai_check : R.drawable.ic_ai_chat,
                            row.selected() ? theme.brand : theme.text2),
                    text, ListSuffixSpec.custom(moreButton(row.id())));
        }
        if (next.count() == 0) {
            list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_plus, theme.text2),
                    ListTextSpec.of(context.getString(R.string.ai_settings_models_empty),
                            context.getString(R.string.ai_settings_models_empty_hint)),
                    ListSuffixSpec.arrow());
        } else if (next.rows().isEmpty()) {
            list.addItem(ListTextSpec.of(context.getString(R.string.ai_settings_models_no_match)));
            list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_plus, theme.text2),
                    ListTextSpec.of(context.getString(R.string.ai_settings_add_model_entry)),
                    ListSuffixSpec.arrow());
        } else {
            list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_plus, theme.text2),
                    ListTextSpec.of(context.getString(R.string.ai_settings_add_model_entry)),
                    ListSuffixSpec.arrow());
        }
    }

    private void onRow(int position) {
        AiPlatformUiState.Models state = current;
        if (state == null) {
            return;
        }
        if (position >= 0 && position < state.rows().size()) {
            listener.onSelectModel(state.rows().get(position).id());
        } else if (state.count() == 0 || state.rows().isEmpty()
                || position == state.rows().size()) {
            listener.onAddManualModel();
        }
    }

    @NonNull
    private View moreButton(@NonNull String modelId) {
        ImageView button = theme.icon(R.drawable.ic_ai_more, theme.text3);
        int padding = theme.dp(AiTheme.SPACE_M);
        button.setPadding(padding, padding, padding, padding);
        button.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        button.setOnClickListener(anchor -> listener.onModelMenu(anchor, modelId));
        return button;
    }

    @NonNull
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @NonNull
    private static String signature(@NonNull AiPlatformUiState.Models models) {
        StringBuilder result = new StringBuilder()
                .append(models.count()).append('\u0000')
                .append(models.actionLabel()).append('\u0000')
                .append(models.summary()).append('\u0000')
                .append(models.query()).append('\u0000')
                .append(models.searchable()).append('\u0000');
        for (AiPlatformUiState.Row row : models.rows()) {
            result.append(row.id()).append(':').append(row.name()).append(':')
                    .append(row.selected()).append(':').append(row.state()).append(':')
                    .append(String.join("|", row.facts())).append('\u0000');
        }
        return result.toString();
    }
}

package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

/** 平台高级区：收起时一行摘要，展开时展示名称、地址和协议。 */
final class PlatformAdvancedSection {

    private final Context context;
    private final AiTheme theme;
    private final Runnable onToggle;
    private final LinearLayout view;
    private final YoList list;
    @Nullable
    private String signature;

    PlatformAdvancedSection(@NonNull Context context, @NonNull AiTheme theme,
                            @NonNull YoComponents components, @NonNull Runnable onToggle) {
        this.context = context;
        this.theme = theme;
        this.onToggle = onToggle;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(AiSectionHeader.titleOnly(theme, context,
                context.getString(R.string.ai_settings_advanced_section)), matchWrap());
        list = YoList.create(context).setOnItemClickListener(this::onRow).build();
        components.own(list::destroy);
        view.addView(list.getView(), matchWrap());
    }

    private void onRow(int position) {
        if (position == 0 && list.getItemCount() == 1) {
            onToggle.run();
        }
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull AiPlatformUiState.Advanced next, boolean expanded) {
        String nextSignature = expanded + "\u0000" + next.name() + "\u0000" + next.baseUrl()
                + "\u0000" + next.protocolRes();
        if (nextSignature.equals(signature)) {
            return;
        }
        signature = nextSignature;
        list.clear();
        if (!expanded) {
            list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_swap, theme.text2),
                    ListTextSpec.of(context.getString(R.string.ai_settings_advanced_section),
                            context.getString(next.protocolRes()) + " · " + next.baseUrl()),
                    ListSuffixSpec.icon(R.drawable.ic_ai_chevron_down, theme.text3));
            return;
        }
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_text, theme.text2),
                ListTextSpec.of(context.getString(R.string.ai_settings_field_name), next.name()),
                ListSuffixSpec.none());
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_globe, theme.text2),
                ListTextSpec.of(context.getString(R.string.ai_settings_field_base_url), next.baseUrl()),
                ListSuffixSpec.none());
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_swap, theme.text2),
                ListTextSpec.of(context.getString(R.string.ai_settings_field_protocol),
                        context.getString(next.protocolRes())),
                ListSuffixSpec.none());
    }

    @NonNull
    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

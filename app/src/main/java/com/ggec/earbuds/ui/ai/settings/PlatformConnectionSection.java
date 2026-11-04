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

/** 平台连接区：测试连接与 Key 的显示状态。控件只创建一次。 */
final class PlatformConnectionSection {

    interface Listener {
        void onTestConnection();
    }

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final YoList list;
    @Nullable
    private AiPlatformUiState.Connection current;
    @Nullable
    private String signature;

    PlatformConnectionSection(@NonNull Context context, @NonNull AiTheme theme,
                              @NonNull YoComponents components, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(AiSectionHeader.titleOnly(theme, context,
                context.getString(R.string.ai_settings_connection_section)), matchWrap());
        list = YoList.create(context).setOnItemClickListener(this::onRow).build();
        components.own(list::destroy);
        view.addView(list.getView(), matchWrap());
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull AiPlatformUiState.Connection next) {
        current = next;
        String value = next.blockedReason() != null
                ? next.blockedReason().resolve(context)
                : next.result() == null
                ? context.getString(R.string.ai_settings_connection_start)
                : next.result().resolve(context);
        String key = next.key().display().resolve(context);
        String nextSignature = value + "\u0000" + key + "\u0000" + next.testing();
        if (nextSignature.equals(signature)) {
            return;
        }
        signature = nextSignature;
        list.clear();
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_bolt, theme.text2),
                ListTextSpec.of(context.getString(R.string.ai_settings_connection_test), value),
                ListSuffixSpec.none());
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_key, theme.text2),
                ListTextSpec.of(context.getString(R.string.ai_settings_field_key), key),
                ListSuffixSpec.icon(R.drawable.ic_ai_settings_reveal, theme.text3));
    }

    private void onRow(int position) {
        if (position == 0 && current != null && current.blockedReason() == null) {
            listener.onTestConnection();
        }
    }

    @NonNull
    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

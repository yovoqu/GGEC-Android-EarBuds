package com.ggec.earbuds.ui.device.home;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.ui.device.DeviceTheme;

/** 单个能力入口卡片：图标 + 标题 + 项数 + 右箭头。 */
public final class DeviceEntryCardView {

    public interface Listener {
        void onOpen(@NonNull DeviceCapabilityGroup group);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceCapabilityGroup group;
    private final LinearLayout root;

    public DeviceEntryCardView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                               @NonNull DeviceCapabilityGroup group,
                               @NonNull Listener listener) {
        this.activity = activity;
        this.theme = theme;
        this.group = group;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_L),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_L));
        root.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_ROW,
                theme.cardStroke, 1f));
        root.setClickable(true);
        root.setOnClickListener(v -> listener.onOpen(group));
    }

    @NonNull
    public View view() {
        return root;
    }

    public void render(@NonNull DeviceHomeUiState.Entry entry) {
        root.removeAllViews();

        ImageView icon = theme.icon(entry.iconRes(), theme.brand);
        root.addView(icon, new LinearLayout.LayoutParams(theme.dp(24f), theme.dp(24f)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = theme.dp(DeviceTheme.SPACE_M);
        root.addView(texts, textParams);

        TextView name = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        name.setText(entry.titleRes());
        texts.addView(name, matchWrap());

        TextView count = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        count.setText(countText(entry.count()));
        texts.addView(count, matchWrap());

        ImageView arrow = theme.icon(R.drawable.ic_ai_chevron_down, theme.hint);
        arrow.setRotation(-90f);
        root.addView(arrow, new LinearLayout.LayoutParams(theme.dp(16f), theme.dp(16f)));
    }

    @NonNull
    private String countText(int count) {
        return activity.getString(R.string.device_entry_count, count);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }


}

package com.ggec.earbuds.ui.device.find;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.DeviceCardView;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 查找设备页视图：参考音频管家「查找设备」——居中耳机主图 + 设备名 + 说明 + 左右耳响铃两张半宽卡 +
 * 停止响铃。纯渲染，所有意图经 {@link Listener} 上送。
 */
public final class FindDeviceScreen {

    public interface Listener {
        void onBack();

        void onRingLeft();

        void onRingRight();

        void onStop();
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;

    private final LinearLayout root;
    private final TextView nameView;
    private final LinearLayout leftCard;
    private final LinearLayout rightCard;
    private final LinearLayout stopCard;

    public FindDeviceScreen(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = DeviceTheme.of(activity);
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        root.addView(buildHeader(), matchWrap());

        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int side = theme.dp(DeviceTheme.PAGE_INSET);
        content.setPadding(side, theme.dp(DeviceTheme.SPACE_S), side,
                theme.dp(DeviceTheme.SPACE_XL));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildHero(), matchWrap());
        nameView = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        nameView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams nameParams = matchWrap();
        nameParams.topMargin = theme.dp(DeviceTheme.SPACE_L);
        content.addView(nameView, nameParams);

        TextView subtitle = theme.text(DeviceTheme.TEXT_CALLOUT, theme.text3);
        subtitle.setText(R.string.find_device_subtitle);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        content.addView(subtitle, subtitleParams);

        TextView hint = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text3);
        hint.setText(R.string.find_device_hint);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = theme.dp(DeviceTheme.SPACE_XL);
        content.addView(hint, hintParams);

        LinearLayout cardsRow = new LinearLayout(activity);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cardsParams = matchWrap();
        cardsParams.topMargin = theme.dp(DeviceTheme.SPACE_M);
        content.addView(cardsRow, cardsParams);

        leftCard = ringCard(R.string.find_device_left, "L", listener::onRingLeft);
        rightCard = ringCard(R.string.find_device_right, "R", listener::onRingRight);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftParams.rightMargin = theme.dp(DeviceTheme.CARD_GAP);
        cardsRow.addView(leftCard, leftParams);
        cardsRow.addView(rightCard, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        stopCard = stopCard();
        LinearLayout.LayoutParams stopParams = matchWrap();
        stopParams.topMargin = theme.dp(DeviceTheme.CARD_GAP);
        content.addView(stopCard, stopParams);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @NonNull
    public View view() {
        return root;
    }

    public void render(@NonNull FindDeviceUiState state) {
        nameView.setText(state.name().isEmpty()
                ? activity.getString(R.string.find_device_name_unknown)
                : state.name());
        setEnabled(leftCard, state.interactive());
        setEnabled(rightCard, state.interactive());
        setEnabled(stopCard, state.interactive());
    }

    private void setEnabled(@NonNull LinearLayout card, boolean enabled) {
        card.setEnabled(enabled);
        card.setAlpha(enabled ? 1f : 0.4f);
        card.setClickable(enabled);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(theme.dp(DeviceTheme.HEADER_HEIGHT));
        int side = theme.dp(DeviceTheme.PAGE_INSET);
        header.setPadding(side, 0, side, 0);

        AiHeaderActionButton back = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.device_back)
                .onClick(v -> listener.onBack());
        header.addView(back, back.createLinearLayoutParams());

        TextView title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1,
                DeviceTheme.Weight.MEDIUM);
        title.setText(R.string.find_device_title);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        header.addView(title, titleParams);
        return header;
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(activity);
        hero.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(200f)));

        View glow = new View(activity);
        glow.setBackground(theme.radialGlow(theme.brandSoft, 200f));
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(
                theme.dp(200f), theme.dp(200f), Gravity.CENTER);
        hero.addView(glow, glowParams);

        ImageView image = new ImageView(activity);
        image.setImageResource(R.drawable.ic_device_earbuds_placeholder);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(image, new FrameLayout.LayoutParams(
                theme.dp(140f), theme.dp(140f), Gravity.CENTER));
        return hero;
    }

    private LinearLayout ringCard(int labelRes, String badge, Runnable onClick) {
        LinearLayout card = DeviceCardView.pressable(activity, theme);

        TextView label = theme.text(DeviceTheme.TEXT_TITLE, theme.text1,
                DeviceTheme.Weight.MEDIUM);
        label.setText(labelRes);
        card.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badgeView = theme.text(DeviceTheme.TEXT_CALLOUT, theme.onBrand,
                DeviceTheme.Weight.MEDIUM);
        badgeView.setText(badge);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setBackground(theme.circle(theme.brand));
        int size = theme.dp(28f);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(size, size);
        card.addView(badgeView, badgeParams);

        card.setOnClickListener(v -> onClick.run());
        return card;
    }

    private LinearLayout stopCard() {
        LinearLayout card = DeviceCardView.pressable(activity, theme);
        card.setGravity(Gravity.CENTER);

        TextView label = theme.text(DeviceTheme.TEXT_TITLE, theme.alert,
                DeviceTheme.Weight.MEDIUM);
        label.setText(R.string.find_device_stop);
        card.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(v -> listener.onStop());
        return card;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

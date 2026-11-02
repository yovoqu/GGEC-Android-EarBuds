package com.ggec.earbuds.ui.ai.history;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
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
import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.ArrayList;
import java.util.List;

/** 历史面板的会话列表：分组卡、行、行菜单。只展示并转发动作。 */
final class AiHistoryListContent {

    interface Listener {
        void onOpen(@NonNull AiHistoryItemUi item);
        void onRename(@NonNull AiHistoryItemUi item);
        void onDelete(@NonNull AiHistoryItemUi item);
    }

    private static final int ACTION_RENAME = 1;
    private static final int ACTION_DELETE = 2;
    private static final float GROUP_TITLE_SIZE = AiTheme.TEXT_CAPTION;
    private static final float ICON_SIZE = 24f;
    private static final float ROW_INSET = AiTheme.SPACE_L;
    private static final float TEXT_GAP = AiTheme.SPACE_M;
    private static final float DUAL_LINE_HEIGHT = 72f;

    private final AppCompatActivity activity;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout column;
    private List<String> signature = List.of();

    AiHistoryListContent(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                         @NonNull Listener listener) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;
        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
    }

    @NonNull
    View view() {
        return column;
    }

    void render(@NonNull List<AiHistoryItemUi> items) {
        List<String> next = signature(items);
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        column.removeAllViews();
        AiHistoryItemUi.Group group = null;
        LinearLayout card = null;
        for (AiHistoryItemUi item : items) {
            if (item.group() != group) {
                boolean first = group == null;
                group = item.group();
                column.addView(groupTitle(group), groupParams(first));
                card = newCard();
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);
                column.addView(card, cardParams);
            }
            if (card.getChildCount() > 0) {
                card.addView(divider(), dividerParams());
            }
            card.addView(row(item), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    void release() {
        column.removeAllViews();
    }

    @NonNull
    private List<String> signature(@NonNull List<AiHistoryItemUi> items) {
        List<String> keys = new ArrayList<>(items.size());
        for (AiHistoryItemUi item : items) {
            keys.add(item.id() + '\u0000' + item.title() + '\u0000' + item.preview()
                    + '\u0000' + item.time() + '\u0000' + item.current() + '\u0000' + item.group());
        }
        return List.copyOf(keys);
    }

    @NonNull
    private TextView groupTitle(@NonNull AiHistoryItemUi.Group group) {
        TextView title = theme.text(GROUP_TITLE_SIZE, theme.text3, AiTheme.Weight.MEDIUM);
        title.setText(groupLabel(group));
        title.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);
        return title;
    }

    @StringRes
    private static int groupLabel(@NonNull AiHistoryItemUi.Group group) {
        return switch (group) {
            case TODAY -> R.string.ai_history_group_today;
            case YESTERDAY -> R.string.ai_history_group_yesterday;
            case EARLIER -> R.string.ai_history_group_earlier;
        };
    }

    @NonNull
    private LinearLayout.LayoutParams groupParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(first ? AiTheme.SPACE_XS : AiTheme.SPACE_L);
        params.bottomMargin = theme.dp(AiTheme.SPACE_S);
        return params;
    }

    @NonNull
    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.roundedStroke(theme.card, AiTheme.RADIUS_SHEET,
                theme.cardStroke, 1));
        card.setClipToOutline(true);
        return card;
    }

    @NonNull
    private View divider() {
        View line = new View(activity);
        line.setBackgroundColor(theme.divider);
        return line;
    }

    @NonNull
    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1)));
        params.setMarginStart(theme.dp(ROW_INSET + ICON_SIZE + TEXT_GAP));
        return params;
    }

    @NonNull
    private View row(@NonNull AiHistoryItemUi item) {
        boolean hasPreview = item.preview() != null;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(hasPreview ? DUAL_LINE_HEIGHT : AiTheme.ROW_HEIGHT));
        row.setPadding(theme.dp(ROW_INSET), 0, theme.dp(AiTheme.SPACE_XS), 0);
        row.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
        row.setOnClickListener(view -> listener.onOpen(item));

        ImageView mark = theme.icon(item.current() ? R.drawable.ic_ai_check : R.drawable.ic_ai_chat,
                item.current() ? theme.brand : theme.text3);
        row.addView(mark, new LinearLayout.LayoutParams(
                theme.dp(ICON_SIZE), theme.dp(ICON_SIZE)));

        LinearLayout lines = new LinearLayout(activity);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        linesParams.setMarginStart(theme.dp(TEXT_GAP));
        row.addView(lines, linesParams);
        lines.addView(titleLine(item), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (hasPreview) {
            TextView preview = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
            preview.setText(item.preview().resolve(activity));
            singleLine(preview);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            previewParams.topMargin = theme.dp(2);
            lines.addView(preview, previewParams);
        }
        row.addView(moreButton(item));
        return row;
    }

    @NonNull
    private View titleLine(@NonNull AiHistoryItemUi item) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        CharSequence title = item.title().resolve(activity);
        if (item.current()) {
            SpannableString bold = new SpannableString(title);
            bold.setSpan(new StyleSpan(Typeface.BOLD), 0, bold.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            title = bold;
        }
        TextView titleView = theme.text(AiTheme.TEXT_BODY, theme.text1);
        titleView.setText(title);
        singleLine(titleView);
        line.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView time = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        time.setText(item.time().resolve(activity));
        singleLine(time);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMarginStart(theme.dp(AiTheme.SPACE_S));
        line.addView(time, timeParams);
        return line;
    }

    private static void singleLine(@NonNull TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    @NonNull
    private ImageView moreButton(@NonNull AiHistoryItemUi item) {
        ImageView more = theme.icon(R.drawable.ic_ai_more, theme.text3);
        int pad = theme.dp(8);
        more.setPadding(pad, pad, pad, pad);
        more.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        more.setContentDescription(activity.getString(R.string.ai_history_row_more));
        more.setOnClickListener(view -> showRowMenu(view, item));
        more.setLayoutParams(new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));
        return more;
    }

    private void showRowMenu(@NonNull View anchor, @NonNull AiHistoryItemUi item) {
        YoMenu menu = YoMenu.create(activity)
                .addItem(ACTION_RENAME, activity.getString(R.string.ai_history_rename),
                        R.drawable.ic_ai_edit)
                .setAnchorView(anchor)
                .setPlacement(YoMenu.Placement.BOTTOM_RIGHT)
                .setOnItemClickListener(id -> {
                    if (id == ACTION_RENAME) {
                        listener.onRename(item);
                    } else if (id == ACTION_DELETE) {
                        listener.onDelete(item);
                    }
                })
                .build();
        menu.addDestructiveItem(ACTION_DELETE, activity.getString(R.string.ai_delete));
        menu.setOnDismissListener(menu::destroy);
        menu.show();
    }
}

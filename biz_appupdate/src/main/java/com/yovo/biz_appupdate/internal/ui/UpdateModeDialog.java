package com.yovo.biz_appupdate.internal.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.R;
import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.yotheme.color.YoTheme;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.YoDialog;
import com.yovo.yotheme.dialog.api.content.CustomContentBinder;
import com.yovo.yotheme.dialog.preset.CustomContentPreset;
import com.yovo.yotheme.dialog.widget.DialogMetrics;
import com.yovo.yotheme.dialog.widget.DialogRippleDrawable;
import com.yovo.yotheme.small_widget.YoRadio;

import java.util.ArrayList;
import java.util.List;

public final class UpdateModeDialog {

    public interface OnConfirmListener {
        void onConfirm(@NonNull DownloadMode mode);
    }

    public interface OnLaterListener {
        void onLater();
    }

    private final Context context;

    public UpdateModeDialog(@NonNull Context context) {
        this.context = context;
    }

    public void show(@NonNull RemoteUpdate update,
                     @NonNull OnConfirmListener listener,
                     @Nullable OnLaterListener onLater) {
        String description = update.description();
        if (description.isEmpty()) {
            description = context.getString(R.string.update_dialog_no_description);
        }

        ModeSelectionBinder binder = new ModeSelectionBinder(context, description);
        YoDialog.show(context,
                CustomContentPreset.create()
                        .title(context.getString(R.string.update_dialog_title, update.version()))
                        .content(binder)
                        .positive(context.getString(R.string.update_dialog_download),
                                result -> listener.onConfirm(binder.getSelectedMode()))
                        .negative(update.forceUpdate()
                                        ? null
                                        : context.getString(R.string.update_dialog_later),
                                result -> {
                                    if (onLater != null) {
                                        onLater.onLater();
                                    }
                                })
                        .build());
    }

    private static final class ModeSelectionBinder implements CustomContentBinder {

        private final Context context;
        private final String description;
        private DownloadMode selectedMode = DownloadMode.INTERNAL;
        private final List<YoRadio> radios = new ArrayList<>();

        ModeSelectionBinder(@NonNull Context context, @NonNull String description) {
            this.context = context;
            this.description = description;
        }

        @NonNull
        DownloadMode getSelectedMode() {
            return selectedMode;
        }

        @Override
        @NonNull
        public View createView(@NonNull Context ctx) {
            LinearLayout content = new LinearLayout(ctx);
            content.setOrientation(LinearLayout.VERTICAL);
            radios.clear();
            selectedMode = DownloadMode.INTERNAL;

            TextView descView = new TextView(ctx);
            descView.setText(description);
            descView.setTextSize(DialogConstants.MESSAGE_TEXT_SIZE.getValue());
            descView.setTextColor(YoTheme.resolve(YoThemeColor.FONT_SECONDARY));
            descView.setPadding(0, 0, 0, dp(ctx, DialogConstants.CONTENT_BOTTOM_MARGIN.getValue()));
            content.addView(descView, matchParentWrap());

            TextView modeTitle = new TextView(ctx);
            modeTitle.setText(R.string.update_download_mode_title);
            modeTitle.setTextSize(14);
            modeTitle.setPadding(0, dp(ctx, 6), 0, dp(ctx, 4));
            content.addView(modeTitle, matchParentWrap());

            addModeRow(content, DownloadMode.INTERNAL,
                    ctx.getString(R.string.update_download_mode_app_internal));
            addModeRow(content, DownloadMode.SYSTEM,
                    ctx.getString(R.string.update_download_mode_system));
            addModeRow(content, DownloadMode.BROWSER,
                    ctx.getString(R.string.update_download_mode_browser));
            return content;
        }

        private void addModeRow(@NonNull LinearLayout content,
                                @NonNull DownloadMode mode,
                                @NonNull String title) {
            int verticalPadding = dp(context, DialogConstants.OPTION_ITEM_VERTICAL_PADDING.getValue());
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(
                    dp(context, DialogConstants.OPTION_PADDING_START.getValue()),
                    verticalPadding,
                    dp(context, DialogConstants.OPTION_PADDING_END.getValue()),
                    verticalPadding);
            row.setBackground(DialogRippleDrawable.listItem(
                    context, DialogConstants.CORNER_RADIUS.getValue(), true));

            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextSize(DialogConstants.OPTION_TEXT_SIZE.getValue());
            titleView.setTextColor(YoTheme.resolve(YoThemeColor.FONT_PRIMARY));
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(titleView, titleLp);

            YoRadio radio = new YoRadio(context);
            radio.setUserInteractive(false);
            int size = dp(context, DialogConstants.OPTION_RADIO_SIZE.getValue());
            radio.setChecked(mode == selectedMode);
            row.addView(radio, new LinearLayout.LayoutParams(size, size));

            row.setOnClickListener(v -> selectMode(mode));
            radios.add(radio);
            content.addView(row, matchParentWrap());

            if (mode != DownloadMode.BROWSER) {
                View divider = new View(context);
                divider.setBackgroundColor(YoTheme.resolve(YoThemeColor.COMP_DIVIDER));
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                content.addView(divider, dividerLp);
            }
        }

        private void selectMode(@NonNull DownloadMode mode) {
            selectedMode = mode;
            DownloadMode[] modes = DownloadMode.values();
            for (int i = 0; i < radios.size() && i < modes.length; i++) {
                radios.get(i).setChecked(modes[i] == selectedMode);
            }
        }

        private static int dp(@NonNull Context ctx, float value) {
            return DialogMetrics.dp(ctx, value);
        }

        @NonNull
        private static LinearLayout.LayoutParams matchParentWrap() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}

package com.yovo.biz_appupdate.internal.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.R;
import com.yovo.yotheme.color.YoTheme;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.YoDialog;
import com.yovo.yotheme.dialog.api.content.CustomContentBinder;
import com.yovo.yotheme.dialog.preset.CustomContentPreset;
import com.yovo.yotheme.dialog.widget.DialogMetrics;
import com.yovo.yotheme.dialog.widget.DialogRippleDrawable;
import com.yovo.yotheme.small_widget.YoRadio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 多版本本地安装包选择弹窗。
 */
public final class LocalApkPickerDialog {

    public interface OnInstallListener {
        void onInstall(@NonNull File apkFile);
    }

    private final Context context;

    public LocalApkPickerDialog(@NonNull Context context) {
        this.context = context;
    }

    public void show(@NonNull List<File> apkFiles, @NonNull OnInstallListener listener) {
        if (apkFiles.isEmpty()) {
            return;
        }
        PickerBinder binder = new PickerBinder(context, apkFiles);
        YoDialog.show(context,
                CustomContentPreset.create()
                        .title(context.getString(R.string.local_apk_picker_title))
                        .content(binder)
                        .positive(context.getString(R.string.local_apk_picker_install),
                                result -> listener.onInstall(binder.getSelectedFile()))
                        .negative(context.getString(R.string.local_apk_picker_cancel), null)
                        .build());
    }

    private static final class PickerBinder implements CustomContentBinder {

        private final Context context;
        private final List<File> apkFiles;
        private final List<YoRadio> radios = new ArrayList<>();
        private File selectedFile;

        PickerBinder(@NonNull Context context, @NonNull List<File> apkFiles) {
            this.context = context;
            this.apkFiles = apkFiles;
            this.selectedFile = apkFiles.get(0);
        }

        @NonNull
        File getSelectedFile() {
            return selectedFile;
        }

        @Override
        @NonNull
        public View createView(@NonNull Context ctx) {
            LinearLayout content = new LinearLayout(ctx);
            content.setOrientation(LinearLayout.VERTICAL);
            radios.clear();
            selectedFile = apkFiles.get(0);
            for (int i = 0; i < apkFiles.size(); i++) {
                File file = apkFiles.get(i);
                boolean checked = file.equals(selectedFile);
                addRow(content, file, checked);
                if (i < apkFiles.size() - 1) {
                    View divider = new View(ctx);
                    divider.setBackgroundColor(YoTheme.resolve(YoThemeColor.COMP_DIVIDER));
                    content.addView(divider, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1));
                }
            }
            return content;
        }

        private void addRow(@NonNull LinearLayout content,
                            @NonNull File file,
                            boolean checked) {
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
            titleView.setText(file.getName());
            titleView.setTextSize(DialogConstants.OPTION_TEXT_SIZE.getValue());
            titleView.setTextColor(YoTheme.resolve(YoThemeColor.FONT_PRIMARY));
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(titleView, titleLp);

            YoRadio radio = new YoRadio(context);
            radio.setUserInteractive(false);
            int size = dp(context, DialogConstants.OPTION_RADIO_SIZE.getValue());
            radio.setChecked(checked);
            row.addView(radio, new LinearLayout.LayoutParams(size, size));

            row.setOnClickListener(v -> select(file));
            radios.add(radio);
            content.addView(row, matchParentWrap());
        }

        private void select(@NonNull File file) {
            selectedFile = file;
            for (int i = 0; i < radios.size() && i < apkFiles.size(); i++) {
                radios.get(i).setChecked(apkFiles.get(i).equals(selectedFile));
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

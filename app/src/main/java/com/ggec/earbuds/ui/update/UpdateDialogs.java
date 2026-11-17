package com.ggec.earbuds.ui.update;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.List;

/**
 * 更新弹层的视图实现：弹窗壳与内容布局都在这里，控件用 YoUI（{@link YoDialog}），不用框架对话框。
 *
 * <p>只做「怎么摆」：文案取自 {@code strings_update.xml}，选项、段数、进度值都由调用方给出。</p>
 */
public final class UpdateDialogs {

    /** 下载方式选择回调。 */
    public interface DownloadModeSelection {
        void onMode(@NonNull UpdateUiPort.DownloadChoice choice);
    }

    /** 本地安装包选择回调（下标对应传入顺序）。 */
    public interface LocalApkSelection {
        void onPick(int index);
    }

    private static final float DIALOG_DP_MESSAGE = 15f;
    private static final float DIALOG_DP_OPTION = 15f;

    private UpdateDialogs() {
    }

    /**
     * 更新弹窗：说明 + 下载方式单选 + 「立即更新 / 稍后」。强制更新时不给「稍后」。
     */
    @NonNull
    public static YoDialog showUpdatePrompt(@NonNull Context context,
                                            @NonNull UpdateUiPort.Prompt prompt,
                                            @NonNull DownloadModeSelection selection,
                                            @NonNull Runnable onLater) {
        String description = prompt.description().isEmpty()
                ? context.getString(R.string.update_no_description)
                : prompt.description();
        ModeRows rows = new ModeRows(context);
        YoDialog.Builder builder = YoDialog.create(context)
                .setTitle(context.getString(R.string.update_title_new_version, prompt.version()))
                .setContentView(rows.body(description))
                .setPositiveButton(context.getString(R.string.update_action_download),
                        () -> selection.onMode(rows.selectedMode()));
        if (!prompt.forceUpdate()) {
            builder.setNegativeButton(context.getString(R.string.update_action_later), onLater);
        }
        YoDialog dialog = builder.build();
        dialog.show();
        return dialog;
    }

    /** 安装确认：只有一句话与两个按钮。 */
    @NonNull
    public static YoDialog showInstallConfirm(@NonNull Context context,
                                              @NonNull String apkFileName,
                                              @NonNull Runnable onInstall) {
        YoDialog dialog = YoDialog.create(context)
                .setTitle(context.getString(R.string.update_install_title))
                .setMessage(context.getString(R.string.update_install_message, apkFileName))
                .setPositiveButton(context.getString(R.string.update_install_positive), onInstall)
                .setNegativeButton(context.getString(R.string.update_install_negative), () -> { })
                .build();
        dialog.show();
        return dialog;
    }

    /** 本地安装包选择：给多份包时让用户选一份；只有一份也走同一套界面，避免分支。 */
    @NonNull
    public static YoDialog showLocalApkPicker(@NonNull Context context,
                                              @NonNull List<String> fileNames,
                                              @NonNull LocalApkSelection selection) {
        FileRows rows = new FileRows(context, fileNames);
        YoDialog dialog = YoDialog.create(context)
                .setTitle(context.getString(R.string.update_local_picker_title))
                .setContentView(rows.body())
                .setPositiveButton(context.getString(R.string.update_local_picker_positive),
                        () -> selection.onPick(rows.selectedIndex()))
                .setNegativeButton(context.getString(R.string.update_local_picker_negative), () -> { })
                .build();
        dialog.show();
        return dialog;
    }

    /** 下载进度：百分比与进度条都归它管，状态变化只改这里。 */
    @NonNull
    public static ProgressHandle showProgress(@NonNull Context context) {
        ProgressHandle handle = new ProgressHandle(context);
        handle.show();
        return handle;
    }

    /** 检查更新的占位弹窗：不可取消，检查结束由调用方关掉。 */
    @NonNull
    public static YoDialog showChecking(@NonNull Context context) {
        YoDialog dialog = YoDialog.create(context)
                .setTitle(context.getString(R.string.update_checking_title))
                .setCancelable(false)
                .build();
        dialog.show();
        return dialog;
    }

    private static int dp(@NonNull Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static LinearLayout column(@NonNull Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static TextView messageText(@NonNull Context context, @NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(DIALOG_DP_MESSAGE);
        return view;
    }

    private static TextView optionRow(@NonNull Context context, @NonNull String label,
                                      boolean selected) {
        TextView row = new TextView(context);
        row.setText(selected
                ? label + "  " + context.getString(R.string.update_selected_mark)
                : label);
        row.setTextSize(DIALOG_DP_OPTION);
        row.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setTypeface(null, selected ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        return row;
    }

    private static View divider(@NonNull Context context) {
        View divider = new View(context);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return divider;
    }

    /** 下载方式三选一。 */
    private static final class ModeRows {

        private final Context context;
        private final List<UpdateUiPort.DownloadChoice> modes = List.of(
                UpdateUiPort.DownloadChoice.INTERNAL,
                UpdateUiPort.DownloadChoice.SYSTEM,
                UpdateUiPort.DownloadChoice.BROWSER);
        private UpdateUiPort.DownloadChoice selected =
                UpdateUiPort.DownloadChoice.INTERNAL;
        private LinearLayout rowsContainer;

        ModeRows(@NonNull Context context) {
            this.context = context;
        }

        @NonNull
        View body(@NonNull String description) {
            LinearLayout column = column(context);
            TextView descriptionView = messageText(context, description);
            descriptionView.setPadding(0, 0, 0, dp(context, 10));
            column.addView(descriptionView, matchWrap());

            TextView title = new TextView(context);
            title.setText(R.string.update_mode_title);
            title.setTextSize(DIALOG_DP_MESSAGE);
            column.addView(title, matchWrap());

            rowsContainer = column(context);
            renderRows();
            column.addView(rowsContainer, matchWrap());
            return column;
        }

        @NonNull
        UpdateUiPort.DownloadChoice selectedMode() {
            return selected;
        }

        private void renderRows() {
            rowsContainer.removeAllViews();
            for (int index = 0; index < modes.size(); index++) {
                UpdateUiPort.DownloadChoice mode = modes.get(index);
                TextView row = optionRow(context, labelOf(mode), mode == selected);
                row.setOnClickListener(v -> select(mode));
                rowsContainer.addView(row, matchWrap());
                if (index < modes.size() - 1) {
                    rowsContainer.addView(divider(context));
                }
            }
        }

        private void select(@NonNull UpdateUiPort.DownloadChoice mode) {
            selected = mode;
            renderRows();
        }

        @NonNull
        private String labelOf(@NonNull UpdateUiPort.DownloadChoice mode) {
            return switch (mode) {
                case INTERNAL -> context.getString(R.string.update_mode_internal);
                case SYSTEM -> context.getString(R.string.update_mode_system);
                case BROWSER -> context.getString(R.string.update_mode_browser);
            };
        }
    }

    /** 本地安装包列表：文件名即选项文案。 */
    private static final class FileRows {

        private final Context context;
        private final List<String> fileNames;
        private final LinearLayout container;
        private int selectedIndex;

        FileRows(@NonNull Context context, @NonNull List<String> fileNames) {
            this.context = context;
            this.fileNames = fileNames;
            this.container = column(context);
        }

        @NonNull
        View body() {
            renderRows();
            return container;
        }

        int selectedIndex() {
            return selectedIndex;
        }

        private void renderRows() {
            container.removeAllViews();
            for (int index = 0; index < fileNames.size(); index++) {
                int rowIndex = index;
                TextView row = optionRow(context, fileNames.get(index), index == selectedIndex);
                row.setOnClickListener(v -> {
                    selectedIndex = rowIndex;
                    renderRows();
                });
                container.addView(row, matchWrap());
                if (index < fileNames.size() - 1) {
                    container.addView(divider(context));
                }
            }
        }
    }

    /** 下载进度弹窗句柄：状态变化直接改这里，不需要重建弹窗。 */
    public static final class ProgressHandle {

        private final Context context;
        private final LinearLayout content;
        private final ProgressBar progressBar;
        private final TextView progressText;
        private YoDialog dialog;

        ProgressHandle(@NonNull Context context) {
            this.context = context;
            LinearLayout column = column(context);

            this.progressText = new TextView(context);
            this.progressText.setTextSize(DIALOG_DP_MESSAGE);
            this.progressText.setText(context.getString(R.string.update_progress_text, 0));
            this.progressText.setPadding(0, 0, 0, dp(context, 10));
            column.addView(this.progressText, matchWrap());

            this.progressBar = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
            this.progressBar.setMax(100);
            this.progressBar.setProgress(0);
            column.addView(this.progressBar, matchWrap());
            this.content = column;
        }

        void show() {
            dialog = YoDialog.create(context)
                    .setTitle(context.getString(R.string.update_progress_title))
                    .setContentView(content)
                    // 「后台下载」只关闭这个进度弹窗，下载本身继续；动作交给弹窗自身的关闭。
                    .setNegativeButton(context.getString(R.string.update_progress_background),
                            () -> { })
                    .setCancelableOnTouchOutside(false)
                    .build();
            dialog.show();
        }

        public void setPercent(int percent) {
            progressBar.setProgress(percent);
            progressText.setText(context.getString(R.string.update_progress_text, percent));
        }

        public void dismiss() {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        }
    }
}

package com.yovo.biz_appupdate.internal.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.R;
import com.yovo.yotheme.dialog.YoDialog;
import com.yovo.yotheme.dialog.api.content.CustomContentBinder;
import com.yovo.yotheme.dialog.api.lifecycle.DialogHandle;
import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.preset.CustomContentPreset;

/**
 * 下载进度弹窗。
 */
public final class DownloadProgressDialog {

    private final Context context;
    private DialogHandle instance;
    private ProgressBar progressBar;
    private TextView progressText;

    public DownloadProgressDialog(@NonNull Context context) {
        this.context = context;
    }

    public void show() {
        CustomContentBinder binder = new CustomContentBinder() {
            @Override
            @NonNull
            public View createView(@NonNull Context ctx) {
                LinearLayout content = new LinearLayout(ctx);
                content.setOrientation(LinearLayout.VERTICAL);

                progressText = new TextView(ctx);
                progressText.setText(ctx.getString(R.string.download_progress_text, 0));
                progressText.setTextSize(15);
                progressText.setPadding(0, 0, 0, dp(10));

                progressBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setProgress(0);

                content.addView(progressText, matchParentWrap());
                content.addView(progressBar, matchParentWrap());
                return content;
            }
        };

        instance = YoDialog.show(context,
                CustomContentPreset.create()
                        .title(context.getString(R.string.download_progress_title))
                        .content(binder)
                        .negative(context.getString(R.string.download_progress_background), null)
                        .build());
    }

    public void updatePercent(int percent) {
        if (progressBar == null || progressText == null) {
            return;
        }
        progressBar.setProgress(percent);
        progressText.setText(context.getString(R.string.download_progress_text, percent));
    }

    public void dismiss() {
        if (instance != null && instance.isShowing()) {
            instance.dismiss(DialogResult.Reason.PROGRAMMATIC);
        }
        instance = null;
        progressBar = null;
        progressText = null;
    }

    private int dp(float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    @NonNull
    private static LinearLayout.LayoutParams matchParentWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

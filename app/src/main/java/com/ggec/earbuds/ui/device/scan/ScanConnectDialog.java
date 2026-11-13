package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.feedback.YoLoadingProgress;

/**
 * 连接过程的加载弹窗。只跟随 {@link ScanConnectPhase} 显示或关闭，不决定连接成败。
 *
 * <p>弹窗与加载器的释放统一走自己的 {@link YoComponents}：显示只登记、关闭只隐藏，
 * 真正的 destroy 发生在页面销毁。这样不会出现「视图自己 destroy、页面再 destroy 一次」的双通道。</p>
 */
public final class ScanConnectDialog {

    private final AppCompatActivity activity;
    private final YoComponents components = new YoComponents();
    @Nullable
    private YoDialog dialog;
    @Nullable
    private YoLoadingProgress progress;

    public ScanConnectDialog(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    public void render(boolean connecting) {
        if (connecting) {
            show();
            return;
        }
        dismiss();
    }

    public void destroy() {
        dismiss();
        components.close();
        dialog = null;
        progress = null;
    }

    private void show() {
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        progress = YoLoadingProgress.create(activity)
                .setStyle(YoLoadingProgress.Style.SPINNER)
                .setSize(YoLoadingProgress.Size.NORMAL)
                .setAnimating(true)
                .build();
        dialog = YoDialog.create(activity)
                .kind(YoDialog.Kind.LOADING)
                .setMessage(activity.getString(R.string.device_connect_in_progress))
                .hosted(progress)
                .setCancelable(false)
                .setCancelableOnTouchOutside(false)
                .build();
        components.own(progress::destroy);
        components.own(dialog::destroy);
        dialog.show();
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}

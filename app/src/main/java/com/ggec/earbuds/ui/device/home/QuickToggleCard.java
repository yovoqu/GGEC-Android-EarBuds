package com.ggec.earbuds.ui.device.home;

import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoSwitch;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备首页的快捷开关卡：YoList 默认样式，只为当前能力支持的开关生成行。
 * 当协议控制通道不可用时（{@code !controlsAvailable}），开关置灰且不可交互。
 */
public final class QuickToggleCard {

    public interface ToggleListener {
        void onToggle(@NonNull HomeToggle key, boolean checked);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final ToggleListener listener;
    private final FrameLayout host;
    private final List<Runnable> releases = new ArrayList<>();
    private String signature = "";

    public QuickToggleCard(@NonNull AppCompatActivity activity,
                           @NonNull DeviceTheme theme,
                           @NonNull YoComponents components,
                           @NonNull ToggleListener listener) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;
        this.host = new FrameLayout(activity);
        components.own(this::releaseAll);
    }

    @NonNull
    public View view() {
        return host;
    }

    public void render(@NonNull DeviceHomeUiState state) {
        String next = state.controlsAvailable() + "|"
                + state.showGame() + ":" + state.gameMode() + "|"
                + state.showWear() + ":" + state.wearDetect() + "|"
                + state.showVoice() + ":" + state.voiceWakeup();
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        host.removeAllViews();
        releaseAll();

        boolean any = state.showGame() || state.showWear() || state.showVoice();
        host.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) {
            return;
        }

        YoList.Builder builder = YoList.create(activity);
        boolean available = state.controlsAvailable();
        if (state.showGame()) {
            addItem(builder, R.string.device_toggle_game, state.gameMode(), available, HomeToggle.GAME_MODE);
        }
        if (state.showWear()) {
            addItem(builder, R.string.device_toggle_wear, state.wearDetect(), available, HomeToggle.WEAR_DETECT);
        }
        if (state.showVoice()) {
            addItem(builder, R.string.device_toggle_voice, state.voiceWakeup(), available, HomeToggle.VOICE_WAKEUP);
        }
        YoList list = builder.build();
        releases.add(list::destroy);
        host.addView(list.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addItem(YoList.Builder builder, int titleRes, boolean checked,
                         boolean available, HomeToggle key) {
        String title = activity.getString(titleRes);
        if (available) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(title),
                    ListSuffixSpec.switch_(checked, next -> listener.onToggle(key, next)));
            return;
        }
        YoSwitch sw = YoSwitch.create(activity)
                .setChecked(checked)
                .setEnabled(false)
                .build();
        releases.add(sw::destroy);
        builder.addItem(ListPrefixSpec.none(),
                ListTextSpec.colored(title, theme.text3),
                ListSuffixSpec.custom(sw.getView()));
    }

    private void releaseAll() {
        for (Runnable release : releases) {
            release.run();
        }
        releases.clear();
    }
}

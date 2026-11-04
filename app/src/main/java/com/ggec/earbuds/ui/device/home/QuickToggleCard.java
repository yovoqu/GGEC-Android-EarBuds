package com.ggec.earbuds.ui.device.home;

import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

/**
 * 设备首页的快捷开关卡：YoList 默认样式，只为当前能力支持的开关生成行。
 */
public final class QuickToggleCard {

    public interface ToggleListener {
        void onToggle(@NonNull HomeToggle key, boolean checked);
    }

    private final AppCompatActivity activity;
    private final YoComponents components;
    private final ToggleListener listener;
    private final FrameLayout host;
    private String signature = "";

    public QuickToggleCard(@NonNull AppCompatActivity activity,
                           @NonNull YoComponents components, @NonNull ToggleListener listener) {
        this.activity = activity;
        this.components = components;
        this.listener = listener;
        host = new FrameLayout(activity);
    }

    @NonNull
    public View view() {
        return host;
    }

    public void render(@NonNull DeviceHomeUiState state) {
        String next = state.showGame() + ":" + state.gameMode() + "|"
                + state.showWear() + ":" + state.wearDetect() + "|"
                + state.showVoice() + ":" + state.voiceWakeup();
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        host.removeAllViews();

        boolean any = state.showGame() || state.showWear() || state.showVoice();
        host.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) {
            return;
        }

        YoList.Builder builder = YoList.create(activity);
        if (state.showGame()) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(R.string.device_toggle_game)),
                    ListSuffixSpec.switch_(state.gameMode(),
                            checked -> listener.onToggle(HomeToggle.GAME_MODE, checked)));
        }
        if (state.showWear()) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(R.string.device_toggle_wear)),
                    ListSuffixSpec.switch_(state.wearDetect(),
                            checked -> listener.onToggle(HomeToggle.WEAR_DETECT, checked)));
        }
        if (state.showVoice()) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(R.string.device_toggle_voice)),
                    ListSuffixSpec.switch_(state.voiceWakeup(),
                            checked -> listener.onToggle(HomeToggle.VOICE_WAKEUP, checked)));
        }
        YoList list = builder.build();
        components.own(list::destroy);
        host.addView(list.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }
}

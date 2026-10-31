package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.List;
import java.util.function.Consumer;

/**
 * 「添加平台」的候选列表：选中一个模板，再由平台页去填具体信息。
 *
 * <p>这里只做选择，不在这里收 API Key：内置平台的地址、协议都由模板决定，
 * 收到 Key 之后真正的落盘在 {@code SavePlatform} 里，界面不该重复一遍校验。</p>
 *
 * <p>对话框内容是一张托管给 {@link YoDialog} 的 {@link YoList}：由对话框负责释放，
 * 页面不用另外登记。</p>
 */
final class AiAddPlatformDialog {

    private AiAddPlatformDialog() {
    }

    static void show(@NonNull AppCompatActivity activity, @NonNull List<AiPlatformTemplate> templates,
                     @NonNull Consumer<AiPlatformTemplate> onPick) {
        int iconTint = AiTheme.of(activity).text2;
        YoList list = YoList.create(activity)
                .setOnItemClickListener(position -> {
                    if (position < 0 || position >= templates.size()) {
                        return;
                    }
                    AiPlatformTemplate picked = templates.get(position);
                    // 托管列表的释放挂在对话框的关闭路径上，所以先关再走下一步。
                    YoDialog.dismissAll(activity);
                    onPick.accept(picked);
                })
                .build();
        for (AiPlatformTemplate template : templates) {
            ListTextSpec text = template.custom()
                    ? ListTextSpec.of(template.name(), activity.getString(R.string.ai_settings_add_platform_custom))
                    : ListTextSpec.of(template.name(), template.defaultBaseUrl());
            list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_globe, iconTint), text,
                    ListSuffixSpec.arrow());
        }
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.ai_settings_add_platform_pick))
                .hosted(list)
                .setActions(YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null))
                .show();
    }
}

package com.ggec.earbuds.ui.device.scan;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoSelect;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 标题栏设备类型下拉的控制器：持有下拉的渲染状态（已渲染选项、上次签名、上次选中项），
 * 把用户选择翻成机型 id 回调出去。
 *
 * <p>下拉只会因为 {@link ScanUiState#filterOptions()} 变化而重建选项；选中项按机型 id 对齐，
 * 型号目录换序不会让选中项跳到别的机型上。用户回调期间用 {@code updating} 屏蔽自触发，
 * 否则程序化 setValue 会被当成一次用户选择再打回 ViewModel。</p>
 */
public final class ScanFilterController {

    private final AppCompatActivity activity;
    private final YoSelect select;
    private final Consumer<String> onSelectModelId;

    private List<ScanFilterOption> renderedOptions = List.of();
    private String lastOptionsSignature = "";
    private String lastSelectedModelId = "";
    private boolean updating;

    public ScanFilterController(@NonNull AppCompatActivity activity,
                                @NonNull YoComponents components,
                                @NonNull Consumer<String> onSelectModelId) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.onSelectModelId = Objects.requireNonNull(onSelectModelId, "onSelectModelId");
        this.select = YoSelect.create(activity)
                .setControlSize(YoSelect.ControlSize.SMALL)
                .setArrowPosition(YoSelect.ArrowPosition.END)
                .setMenuAlign(YoSelect.MenuAlignType.END)
                .setOptionWidth(YoSelect.OptionWidthMode.FIT_CONTENT)
                .setShowDefaultSelectedIcon(true)
                .setOnSelectListener((index, text, itemId) -> {
                    if (updating) {
                        return;
                    }
                    // YoSelect.onSelect 的第一个参数是列表序号，addOption 传入的 id 在第三个参数。
                    if (itemId >= 0 && itemId < renderedOptions.size()) {
                        onSelectModelId.accept(renderedOptions.get(itemId).modelId());
                    }
                })
                .build();
        components.own(select::destroy);
    }

    @NonNull
    public View view() {
        return select.getView();
    }

    /** 按当前界面状态刷新选项与选中项；无变化时不触碰下拉。 */
    public void render(@NonNull ScanUiState state) {
        List<ScanFilterOption> options = state.filterOptions();
        String signature = optionsSignature(options);
        boolean optionsChanged = !signature.equals(lastOptionsSignature);

        if (optionsChanged) {
            updating = true;
            try {
                renderedOptions = options;
                lastOptionsSignature = signature;
                select.clearOptions();
                for (int i = 0; i < options.size(); i++) {
                    select.addOption(i, optionLabel(options.get(i)));
                }
            } finally {
                updating = false;
            }
        }

        int selectedIndex = findOptionIndex(renderedOptions, state.selectedFilterModelId());
        String selectedModelId = selectedIndex >= 0
                ? renderedOptions.get(selectedIndex).modelId()
                : (renderedOptions.isEmpty() ? "" : renderedOptions.get(0).modelId());
        if (!optionsChanged && selectedModelId.equals(lastSelectedModelId)) {
            return;
        }
        updating = true;
        try {
            lastSelectedModelId = selectedModelId;
            String selectedLabel = selectedIndex >= 0
                    ? optionLabel(renderedOptions.get(selectedIndex))
                    : activity.getString(R.string.device_scan_filter_all);
            select.setValue(selectedLabel);
            if (selectedIndex >= 0) {
                select.setSelectedItemId(selectedIndex);
            }
            select.getView().setContentDescription(
                    activity.getString(R.string.device_scan_filter_desc) + " " + selectedLabel);
        } finally {
            updating = false;
        }
    }

    @NonNull
    private String optionLabel(@NonNull ScanFilterOption option) {
        return option.label().isEmpty() ? option.modelId() : option.label();
    }

    @NonNull
    private String optionsSignature(@NonNull List<ScanFilterOption> options) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                builder.append('|');
            }
            ScanFilterOption option = options.get(i);
            builder.append(option.modelId()).append('=').append(optionLabel(option));
        }
        return builder.toString();
    }

    private static int findOptionIndex(@NonNull List<ScanFilterOption> options,
                                       @NonNull String modelId) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).modelId().equals(modelId)) {
                return i;
            }
        }
        return options.isEmpty() ? -1 : 0;
    }
}

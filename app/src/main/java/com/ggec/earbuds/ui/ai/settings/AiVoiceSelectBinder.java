package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yohu.youi.api.controls.YoSelect;

import java.util.List;

/** 长寿命 YoSelect 的选项与选中值同步。只有选项集合真的变化才重建菜单内容。 */
final class AiVoiceSelectBinder {

    private AiVoiceSelectBinder() {
    }

    static void bind(@NonNull YoSelect select, @Nullable String previousSignature,
                     @NonNull List<String> labels, @NonNull String selectedText,
                     @NonNull String nextSignature) {
        if (!nextSignature.equals(previousSignature)) {
            select.clearOptions();
            for (int i = 0; i < labels.size(); i++) {
                select.addOption(i, labels.get(i));
            }
        }
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).contentEquals(selectedText)) {
                select.setSelectedItemId(i);
                break;
            }
        }
        select.setValue(selectedText);
    }

    @NonNull
    static String signature(int size, @NonNull Part part) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < size; i++) {
            result.append(part.at(i)).append('\u0000');
        }
        return result.toString();
    }

    interface Part {
        @NonNull String at(int index);
    }
}

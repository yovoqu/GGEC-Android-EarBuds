package com.yovo.yotheme.dialog.api.callback;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import androidx.annotation.NonNull;


import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 确认选择回调：点击确认按钮后提交已选项
 */
public interface SelectionConfirmCallback {

    void onConfirm(@NonNull List<String> selectedIds, @NonNull List<OptionItemSpec> selectedItems);
}





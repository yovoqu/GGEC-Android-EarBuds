package com.yovo.yotheme.dialog.api.callback;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import androidx.annotation.NonNull;


/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 立即单选回调：点选后触发并关闭对话框
 */
public interface ImmediateSelectionCallback {

    void onSelect(int index, @NonNull OptionItemSpec item);
}





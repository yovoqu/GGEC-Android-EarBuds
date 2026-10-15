package com.yovo.yotheme.components.yo_input;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description 输入框组件接口定义
 * @note 定义 YoInput 对外暴露的核心文本操作能力
 */
public interface YoInputInterface {

    void setText(CharSequence text);

    CharSequence getText();

    Editable getEditableText();

    void setHint(CharSequence hint);

    CharSequence getHint();

    void setInputType(int inputType);

    void setImeOptions(int imeOptions);

    void setSelection(int index);

    void addTextChangedListener(TextWatcher watcher);

    void removeTextChangedListener(TextWatcher watcher);
}

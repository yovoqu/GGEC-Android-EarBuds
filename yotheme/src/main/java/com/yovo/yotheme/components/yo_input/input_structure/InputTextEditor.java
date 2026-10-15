package com.yovo.yotheme.components.yo_input.input_structure;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.widget.AppCompatEditText;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.InputConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description 内部文本编辑器：隐藏系统光标与背景，由 YoInput 统一绘制
 */
public class InputTextEditor extends AppCompatEditText {

    public interface Listener {
        void onFocusChanged(boolean focused);

        void onSelectionChanged();

        void onTextChanged();
    }

    private Listener listener;

    public InputTextEditor(Context context) {
        super(context);
        init(context);
    }

    public InputTextEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public InputTextEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackground(null);
        setCursorVisible(false);
        setIncludeFontPadding(false);
        setSingleLine(true);
        setMaxLines(1);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setTextColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));
        setHintTextColor(ColorMapper.map(YoThemeColor.FONT_TERTIARY));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, InputConstants.TEXT_SIZE.getIntValue());
        setTypeface(getTypeface(), Typeface.NORMAL);
        setImeOptions(EditorInfo.IME_ACTION_DONE);

        setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO) {
                hideImeAndClearFocus();
                return true;
            }
            return false;
        });

        int paddingH = dpToPx(context, InputConstants.PADDING_HORIZONTAL.getIntValue());
        int paddingV = dpToPx(context, InputConstants.PADDING_VERTICAL.getIntValue());
        setPadding(paddingH, paddingV, paddingH, paddingV);

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (listener != null) {
                    listener.onTextChanged();
                }
            }
        });
    }

    public void hideImeAndClearFocus() {
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getWindowToken() != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
        clearFocus();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) {
            listener.onSelectionChanged();
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (listener != null) {
            listener.onFocusChanged(focused);
        }
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density + InputConstants.ROUNDING_FLOAT.getFloatValue());
    }
}

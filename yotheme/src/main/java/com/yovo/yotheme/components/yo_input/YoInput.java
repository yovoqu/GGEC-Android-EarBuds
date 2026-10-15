package com.yovo.yotheme.components.yo_input;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.R;
import com.yovo.yotheme.components.yo_input.input_structure.InputCursorRenderer;
import com.yovo.yotheme.components.yo_input.input_structure.InputFieldRenderer;
import com.yovo.yotheme.components.yo_input.input_structure.InputFocusController;
import com.yovo.yotheme.components.yo_input.input_structure.InputStateManager;
import com.yovo.yotheme.components.yo_input.input_structure.InputTextEditor;
import com.yovo.yotheme.constants.InputConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description Yo 主题通用输入框
 * @note 圆角边框、文本编辑与自定义光标均由组件内部绘制，不使用系统光标
 */
public class YoInput extends FrameLayout implements YoInputInterface, InputFocusController.Host {

    private final InputFieldRenderer fieldRenderer;
    private final InputCursorRenderer cursorRenderer;
    private final InputStateManager stateManager;
    private final InputTextEditor editor;
    private View attachedRoot;

    public static YoInput create(@NonNull Context context) {
        return new YoInput(context);
    }

    public YoInput(@NonNull Context context) {
        this(context, null);
    }

    public YoInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoInput(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);

        fieldRenderer = new InputFieldRenderer(context);
        cursorRenderer = new InputCursorRenderer(context);
        stateManager = new InputStateManager();
        editor = new InputTextEditor(context);

        int minHeight = dpToPx(context, InputConstants.MIN_HEIGHT.getIntValue());
        editor.setMinHeight(minHeight);
        addView(editor, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        bindStructure();
        applyAttributes(context, attrs);
    }

    private void bindStructure() {
        editor.setListener(new InputTextEditor.Listener() {
            @Override
            public void onFocusChanged(boolean focused) {
                stateManager.syncEditorFocus(YoInput.this, focused);
            }

            @Override
            public void onSelectionChanged() {
                if (editor.isFocused()) {
                    stateManager.onSelectionChanged(YoInput.this);
                }
            }

            @Override
            public void onTextChanged() {
                if (editor.isFocused()) {
                    stateManager.onTextChanged(YoInput.this);
                }
            }
        });

        stateManager.setCallback(new InputStateManager.Callback() {
            @Override
            public void onFocusChanged(boolean focused) {
                invalidate();
            }

            @Override
            public void onCursorBlinkTick(boolean visible) {
                invalidate();
            }
        });
    }

    private void applyAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.YoInput);
        try {
            CharSequence hint = typedArray.getText(R.styleable.YoInput_android_hint);
            if (hint != null) {
                editor.setHint(hint);
            }
            CharSequence text = typedArray.getText(R.styleable.YoInput_android_text);
            if (text != null) {
                editor.setText(text);
            }
            if (typedArray.hasValue(R.styleable.YoInput_android_inputType)) {
                editor.setInputType(typedArray.getInt(
                        R.styleable.YoInput_android_inputType,
                        editor.getInputType()));
            }
            if (typedArray.hasValue(R.styleable.YoInput_android_imeOptions)) {
                editor.setImeOptions(typedArray.getInt(
                        R.styleable.YoInput_android_imeOptions,
                        editor.getImeOptions()));
            }
            editor.setEnabled(typedArray.getBoolean(R.styleable.YoInput_android_enabled, true));
            if (typedArray.hasValue(R.styleable.YoInput_android_singleLine)) {
                editor.setSingleLine(typedArray.getBoolean(R.styleable.YoInput_android_singleLine, true));
            }
            if (typedArray.hasValue(R.styleable.YoInput_android_maxLines)) {
                editor.setMaxLines(typedArray.getInt(R.styleable.YoInput_android_maxLines, 1));
            }
        } finally {
            typedArray.recycle();
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        boolean editorFocused = editor.isFocused();
        fieldRenderer.draw(canvas, getWidth(), getHeight(), stateManager.isFieldFocused(editorFocused));
        super.dispatchDraw(canvas);
        cursorRenderer.draw(canvas, editor, stateManager.isCursorVisible(editorFocused));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedRoot = getRootView();
        InputFocusController.register(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        InputFocusController.onWindowFocusChanged(this, hasWindowFocus);
        stateManager.onWindowFocusChanged(this, hasWindowFocus);
    }

    @Override
    protected void onDetachedFromWindow() {
        InputFocusController.unregister(this, attachedRoot);
        attachedRoot = null;
        stateManager.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    @Override
    public View asView() {
        return this;
    }

    @Override
    public InputTextEditor getEditor() {
        return editor;
    }

    @Override
    public void onFocusStateChanged() {
        stateManager.syncEditorFocus(this, editor.isFocused());
    }

    public void clearEditorFocus() {
        InputFocusController.dismissEditor(this);
    }

    @Override
    public void setText(CharSequence text) {
        editor.setText(text);
    }

    @Override
    public CharSequence getText() {
        return editor.getText();
    }

    @Override
    public Editable getEditableText() {
        return editor.getEditableText();
    }

    @Override
    public void setHint(CharSequence hint) {
        editor.setHint(hint);
    }

    @Override
    public CharSequence getHint() {
        return editor.getHint();
    }

    @Override
    public void setInputType(int inputType) {
        editor.setInputType(inputType);
    }

    @Override
    public void setImeOptions(int imeOptions) {
        editor.setImeOptions(imeOptions);
    }

    @Override
    public void setSelection(int index) {
        editor.setSelection(index);
    }

    @Override
    public void addTextChangedListener(TextWatcher watcher) {
        editor.addTextChangedListener(watcher);
    }

    @Override
    public void removeTextChangedListener(TextWatcher watcher) {
        editor.removeTextChangedListener(watcher);
    }

    public void setFillColor(int color) {
        fieldRenderer.setFillColor(color);
        invalidate();
    }

    public void setBorderColorNormal(int color) {
        fieldRenderer.setBorderColorNormal(color);
        invalidate();
    }

    public void setBorderColorFocused(int color) {
        fieldRenderer.setBorderColorFocused(color);
        invalidate();
    }

    public void setCursorColor(int color) {
        cursorRenderer.setCursorColor(color);
        invalidate();
    }

    private static int dpToPx(@NonNull Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density + InputConstants.ROUNDING_FLOAT.getFloatValue());
    }
}

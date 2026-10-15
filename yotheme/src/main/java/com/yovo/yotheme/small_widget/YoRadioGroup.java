package com.yovo.yotheme.small_widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-07-22
 * @description YoRadio 互斥组，对齐系统 {@link android.widget.RadioGroup} 语义：组内至多一项选中。
 */
public class YoRadioGroup extends LinearLayout {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(YoRadioGroup group, @IdRes int checkedId);
    }

    @Nullable
    private OnCheckedChangeListener listener;
    private boolean protecting;
    @IdRes
    private int checkedId = View.NO_ID;

    public YoRadioGroup(Context context) {
        this(context, null);
    }

    public YoRadioGroup(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoRadioGroup(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        bindDescendantRadios(child);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        bindDescendantRadios(this);
        // 还原 XML 里已 checked 的项
        YoRadio preChecked = findCheckedRadio(this);
        if (preChecked != null) {
            check(preChecked);
        }
    }

    private void bindDescendantRadios(View root) {
        if (root instanceof YoRadio) {
            ((YoRadio) root).setRadioGroup(this);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                bindDescendantRadios(group.getChildAt(i));
            }
        }
    }

    /**
     * 选中指定 radio，并取消同组其它项。
     */
    public void check(@Nullable YoRadio radio) {
        if (protecting) {
            return;
        }
        protecting = true;
        try {
            applyExclusive(radio);
        } finally {
            protecting = false;
        }
        int newId = radio != null ? radio.getId() : View.NO_ID;
        if (checkedId != newId) {
            checkedId = newId;
            if (listener != null) {
                listener.onCheckedChanged(this, checkedId);
            }
        }
    }

    public void check(@IdRes int id) {
        if (id == View.NO_ID) {
            clearCheck();
            return;
        }
        View target = findViewById(id);
        if (target instanceof YoRadio) {
            check((YoRadio) target);
        }
    }

    public void clearCheck() {
        check((YoRadio) null);
    }

    @IdRes
    public int getCheckedRadioId() {
        return checkedId;
    }

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    private void applyExclusive(@Nullable YoRadio selected) {
        applyExclusiveRecursive(this, selected);
    }

    private void applyExclusiveRecursive(View root, @Nullable YoRadio selected) {
        if (root instanceof YoRadio) {
            YoRadio radio = (YoRadio) root;
            radio.setRadioGroup(this);
            boolean shouldCheck = selected != null && radio == selected;
            radio.setChecked(shouldCheck, false);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyExclusiveRecursive(group.getChildAt(i), selected);
            }
        }
    }

    @Nullable
    private static YoRadio findCheckedRadio(View root) {
        if (root instanceof YoRadio) {
            YoRadio radio = (YoRadio) root;
            return radio.isChecked() ? radio : null;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                YoRadio found = findCheckedRadio(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

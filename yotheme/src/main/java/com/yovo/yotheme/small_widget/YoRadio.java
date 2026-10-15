package com.yovo.yotheme.small_widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description Yo 主题单选按钮。
 * <p>
 * 组件级语义（对齐系统 RadioButton）：
 * <ul>
 *   <li>可交互时：点击只会<strong>选中</strong>，不会自行取消选中</li>
 *   <li>属于 {@link YoRadioGroup} 时：选中一项会自动取消同组其它项</li>
 *   <li>{@code android:clickable=false} 或 {@link #setUserInteractive(boolean)} 为 false 时：
 *       仅作指示器，不响应点击（也不会被 setOnClickListener 重新打开）</li>
 * </ul>
 */
public class YoRadio extends View {

    private Paint circlePaint;
    private Paint checkmarkPaint;
    private Path checkmarkPath;

    private boolean checked;
    private boolean userInteractive = true;
    private boolean notifying;

    private float radius;
    private int size;

    private int uncheckedColor;
    private int checkedColor;
    private int checkmarkColor;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(YoRadio radio, boolean isChecked);
    }

    @Nullable
    private OnCheckedChangeListener listener;
    @Nullable
    private YoRadioGroup radioGroup;

    public YoRadio(Context context) {
        this(context, null);
    }

    public YoRadio(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoRadio(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);

        checkmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkmarkPaint.setStyle(Paint.Style.STROKE);
        checkmarkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkmarkPaint.setStrokeCap(Paint.Cap.ROUND);

        checkmarkPath = new Path();

        uncheckedColor = ColorMapper.map(YoThemeColor.COMP_DIVIDER);
        checkedColor = ColorMapper.map(YoThemeColor.BRAND);
        checkmarkColor = ColorMapper.map(YoThemeColor.WHITE);

        boolean clickableFromXml = true;
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(
                    attrs, new int[]{android.R.attr.clickable});
            clickableFromXml = a.getBoolean(0, true);
            a.recycle();
        }
        setUserInteractive(clickableFromXml);
    }

    /**
     * 是否允许用户点击切换选中。
     * <p>
     * false：纯指示器（列表行、由外层整行点击驱动选中时使用）。
     * 不会像旧实现那样通过 {@code setOnClickListener} 把 clickable 重新打开。
     */
    public void setUserInteractive(boolean interactive) {
        userInteractive = interactive;
        super.setOnClickListener(interactive ? v -> performUserSelect() : null);
        setClickable(interactive);
        setFocusable(interactive);
    }

    public boolean isUserInteractive() {
        return userInteractive;
    }

    /**
     * 禁止外部再挂会强制可点的 listener；可交互时统一走 {@link #performUserSelect()}。
     */
    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        if (!userInteractive) {
            super.setOnClickListener(null);
            setClickable(false);
            return;
        }
        if (l == null) {
            super.setOnClickListener(v -> performUserSelect());
            setClickable(true);
            return;
        }
        super.setOnClickListener(v -> {
            performUserSelect();
            l.onClick(v);
        });
    }

    private void performUserSelect() {
        if (!userInteractive || !isEnabled()) {
            return;
        }
        YoRadioGroup group = resolveRadioGroup();
        if (group != null) {
            group.check(this);
            return;
        }
        // 无分组时：只允许选中，不允许点击取消（避免「伪多选 / 点掉唯一选中」）
        if (!checked) {
            setChecked(true);
        }
    }

    void setRadioGroup(@Nullable YoRadioGroup group) {
        radioGroup = group;
    }

    @Nullable
    private YoRadioGroup resolveRadioGroup() {
        if (radioGroup != null) {
            return radioGroup;
        }
        ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof YoRadioGroup) {
                return (YoRadioGroup) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        size = dpToPx(24);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(size, widthSize);
        } else {
            width = size;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(size, heightSize);
        } else {
            height = size;
        }

        int finalSize = Math.min(width, height);
        setMeasuredDimension(finalSize, finalSize);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        radius = Math.min(w, h) / 2f - dpToPx(1);
        float strokeWidth = dpToPx(1.0f);
        circlePaint.setStrokeWidth(strokeWidth);
        checkmarkPaint.setStrokeWidth(strokeWidth + dpToPx(1.5f));
        createCheckmarkPath(w, h);
    }

    private void createCheckmarkPath(int width, int height) {
        checkmarkPath.reset();
        float centerX = width / 2f;
        float centerY = height / 2f;
        float startX = centerX - radius * 0.4f;
        float startY = centerY + radius * 0.1f;
        float middleX = centerX - radius * 0.1f;
        float middleY = centerY + radius * 0.35f;
        float endX = centerX + radius * 0.5f;
        float endY = centerY - radius * 0.3f;
        checkmarkPath.moveTo(startX, startY);
        checkmarkPath.lineTo(middleX, middleY);
        checkmarkPath.lineTo(endX, endY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        if (checked) {
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(checkedColor);
            canvas.drawCircle(centerX, centerY, radius, circlePaint);
            checkmarkPaint.setColor(checkmarkColor);
            canvas.drawPath(checkmarkPath, checkmarkPaint);
        } else {
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setColor(uncheckedColor);
            canvas.drawCircle(centerX, centerY, radius, circlePaint);
        }
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    /**
     * @param notify 是否回调 {@link OnCheckedChangeListener}；组内批量改状态时可传 false。
     */
    public void setChecked(boolean checked, boolean notify) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        invalidate();
        if (!notify || notifying) {
            return;
        }
        OnCheckedChangeListener l = listener;
        if (l != null) {
            notifying = true;
            try {
                l.onCheckedChanged(this, this.checked);
            } finally {
                notifying = false;
            }
        }
    }

    public boolean isChecked() {
        return checked;
    }

    /**
     * @deprecated 单选语义不应「点一下取消」。请使用 {@link #setChecked(boolean)} 或点击选中。
     */
    @Deprecated
    public void toggle() {
        setChecked(!checked);
    }

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    private int dpToPx(float dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

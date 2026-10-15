package com.yovo.yotheme.small_widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description Yo主题多选框（圆角矩形，8dp 圆角）
 */
public class YoCheckbox extends View {

	private Paint boxPaint;
	private Paint checkmarkPaint;
	private Path checkmarkPath;

	private boolean checked = false;
	private float cornerRadius;
	private float halfStrokeInset;
	private int size;

	private int uncheckedColor;
	private int checkedColor;
	private int checkmarkColor;

	public interface OnCheckedChangeListener {
		void onCheckedChanged(YoCheckbox checkbox, boolean isChecked);
	}

	private OnCheckedChangeListener listener;

	public YoCheckbox(Context context) {
		this(context, null);
	}

	public YoCheckbox(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public YoCheckbox(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		boxPaint.setStyle(Paint.Style.STROKE);

		checkmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		checkmarkPaint.setStyle(Paint.Style.STROKE);
		checkmarkPaint.setStrokeJoin(Paint.Join.ROUND);
		checkmarkPaint.setStrokeCap(Paint.Cap.ROUND);

		checkmarkPath = new Path();

		uncheckedColor = ColorMapper.map(YoThemeColor.COMP_DIVIDER);
		checkedColor = ColorMapper.map(YoThemeColor.BRAND);
		checkmarkColor = ColorMapper.map(YoThemeColor.WHITE);

		setOnClickListener(v -> toggle());
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

		float strokeWidth = dpToPx(1.0f);
		boxPaint.setStrokeWidth(strokeWidth);
		checkmarkPaint.setStrokeWidth(strokeWidth + dpToPx(1.5f));

		cornerRadius = dpToPx(8.0f);
		halfStrokeInset = strokeWidth / 2f;

		createCheckmarkPath(w, h);
	}

	private void createCheckmarkPath(int width, int height) {
		checkmarkPath.reset();

		float left = halfStrokeInset + dpToPx(3);
		float top = halfStrokeInset + dpToPx(3);
		float right = width - halfStrokeInset - dpToPx(3);
		float bottom = height - halfStrokeInset - dpToPx(3);

		float boxWidth = right - left;
		float boxHeight = bottom - top;

		float startX = left + boxWidth * 0.22f;
		float startY = top + boxHeight * 0.55f;
		float middleX = left + boxWidth * 0.45f;
		float middleY = top + boxHeight * 0.75f;
		float endX = left + boxWidth * 0.80f;
		float endY = top + boxHeight * 0.30f;

		checkmarkPath.moveTo(startX, startY);
		checkmarkPath.lineTo(middleX, middleY);
		checkmarkPath.lineTo(endX, endY);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		float left = halfStrokeInset;
		float top = halfStrokeInset;
		float right = getWidth() - halfStrokeInset;
		float bottom = getHeight() - halfStrokeInset;

		RectF rect = new RectF(left, top, right, bottom);

		if (checked) {
			boxPaint.setStyle(Paint.Style.FILL);
			boxPaint.setColor(checkedColor);
			canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint);

			checkmarkPaint.setColor(checkmarkColor);
			canvas.drawPath(checkmarkPath, checkmarkPaint);
		} else {
			boxPaint.setStyle(Paint.Style.STROKE);
			boxPaint.setColor(uncheckedColor);
			canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint);
		}
	}

	public void setChecked(boolean checked) {
		if (this.checked != checked) {
			this.checked = checked;
			invalidate();
			if (listener != null) {
				listener.onCheckedChanged(this, checked);
			}
		}
	}

	public boolean isChecked() {
		return checked;
	}

	public void toggle() {
		setChecked(!checked);
	}

	public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
		this.listener = listener;
	}

	private int dpToPx(float dp) {
		float density = getContext().getResources().getDisplayMetrics().density;
		return Math.round(dp * density);
	}
}



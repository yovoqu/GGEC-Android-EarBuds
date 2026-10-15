package com.yovo.yotheme.components.yo_list.list_structure;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.appcompat.widget.AppCompatTextView;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.ListConstants;

/**
 * 主文本组件
 */
public class ListTitle extends AppCompatTextView {

    public ListTitle(Context context) {
        this(context, null);
    }

    public ListTitle(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListTitle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTextColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ListConstants.TITLE_TEXT_SIZE_SP.getFloatValue());
        setMaxLines(ListConstants.TEXT_MAX_LINES_TITLE.getIntValue());
        setEllipsize(TextUtils.TruncateAt.END);
    }
}



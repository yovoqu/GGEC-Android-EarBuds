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
 * 辅助文本组件
 */
public class ListSubtitle extends AppCompatTextView {

    public ListSubtitle(Context context) {
        this(context, null);
    }

    public ListSubtitle(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListSubtitle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTextColor(ColorMapper.map(YoThemeColor.FONT_SECONDARY));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ListConstants.SUBTITLE_TEXT_SIZE_SP.getFloatValue());
        setMaxLines(ListConstants.TEXT_MAX_LINES_SUBTITLE.getIntValue());
        setEllipsize(TextUtils.TruncateAt.END);
    }
}



package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置页的一块：标题/说明在列表外面，中间是一张独立 {@link YoList}。
 *
 * <p>独立列表由 YoUI 自己画板材（圆角、边界线、阴影）并计算行 Ripple；
 * 不要换成带 groupHeader 的分组列表，否则行会改用分组卡片的 Ripple 几何。</p>
 */
final class AiSectionList {

    /** 分组标题字号（与原设置页一致）。 */
    private static final float TITLE_SP = 14f;

    private final TextView titleView;
    private final YoList list;
    private final TextView noteView;
    private final List<Runnable> actions = new ArrayList<>();

    private AiSectionList(@NonNull Context context, @NonNull CharSequence title) {
        AiTheme theme = AiTheme.of(context);
        int padH = theme.dp(AiTheme.SPACE_XS);
        titleView = theme.text(TITLE_SP, theme.text2, AiTheme.Weight.MEDIUM);
        titleView.setText(title);
        titleView.setPadding(padH, theme.dp(AiTheme.SPACE_M), padH, padH);
        list = YoList.create(context).setOnItemClickListener(this::onClick).build();
        list.getView().setVisibility(View.GONE);
        noteView = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        noteView.setPadding(padH, padH, padH, theme.dp(AiTheme.SPACE_S));
        noteView.setVisibility(View.GONE);
    }

    @NonNull
    static AiSectionList create(@NonNull Context context, @NonNull CharSequence title) {
        return new AiSectionList(context, title);
    }

    void attach(@NonNull LinearLayout parent) {
        parent.addView(titleView, match());
        parent.addView(list.getView(), match());
        parent.addView(noteView, match());
    }

    void clear() {
        list.clear();
        actions.clear();
        list.getView().setVisibility(View.GONE);
        noteView.setText("");
        noteView.setVisibility(View.GONE);
    }

    void setTitle(@NonNull CharSequence title) {
        titleView.setText(title);
    }

    void setShown(boolean shown) {
        titleView.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (!shown) {
            list.getView().setVisibility(View.GONE);
            noteView.setVisibility(View.GONE);
        }
    }

    void add(@NonNull ListPrefixSpec prefix, @NonNull ListTextSpec text, @NonNull ListSuffixSpec suffix,
             @Nullable Runnable action) {
        list.addItem(prefix, text, suffix);
        actions.add(action);
        list.getView().setVisibility(View.VISIBLE);
    }

    void setNote(@Nullable CharSequence note) {
        if (note == null || note.length() == 0) {
            noteView.setText("");
            noteView.setVisibility(View.GONE);
            return;
        }
        noteView.setText(note);
        noteView.setVisibility(View.VISIBLE);
    }

    @NonNull
    View listView() {
        return list.getView();
    }

    void destroy() {
        list.destroy();
    }

    private void onClick(int position) {
        if (position < 0 || position >= actions.size()) {
            return;
        }
        Runnable action = actions.get(position);
        if (action != null) {
            action.run();
        }
    }

    @NonNull
    private static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

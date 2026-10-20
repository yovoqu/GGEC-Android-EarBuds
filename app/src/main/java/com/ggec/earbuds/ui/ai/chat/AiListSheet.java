package com.ggec.earbuds.ui.ai.chat;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.YoSheet;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史/模型面板共用的骨架：一个 {@link YoList} + 一个 {@link YoSheet}。
 *
 * <p>面板只构建一次、反复 show；列表内容每次刷新前先回收行内组件，再清空行。
 * 这样面板、列表和行内按钮（它们是 YoUI 组件，必须 destroy）都有唯一释放路径。</p>
 */
final class AiListSheet {

    private final AiTheme theme;
    private final YoList list;
    private final CharSequence title;
    private final YoSheet.Height height;
    private final @Nullable View trailingAction;
    private final Runnable onClosed;
    private final List<Runnable> actions = new ArrayList<>();

    private YoComponents rowComponents = new YoComponents();
    @Nullable
    private YoSheet sheet;

    AiListSheet(@NonNull AiTheme theme, @NonNull CharSequence title, @NonNull YoSheet.Height height,
                @Nullable View trailingAction, @NonNull Runnable onClosed) {
        this.theme = theme;
        this.title = title;
        this.height = height;
        this.trailingAction = trailingAction;
        this.onClosed = onClosed;
        this.list = YoList.create(theme.context).setOnItemClickListener(this::onItemClick).build();
    }

    @NonNull
    YoList list() {
        return list;
    }

    /** 清空行与行内组件；下一次 add 重新开始。 */
    void resetRows() {
        rowComponents.close();
        rowComponents = new YoComponents();
        list.clear();
        actions.clear();
    }

    /** 登记一个随下一次 {@link #resetRows()} 一起释放的行内组件。 */
    void ownRowComponent(@NonNull Runnable release) {
        rowComponents.own(release);
    }

    void addItem(@NonNull ListPrefixSpec prefix, @NonNull ListTextSpec text,
                 @NonNull ListSuffixSpec suffix, @Nullable Runnable action) {
        list.addItem(prefix, text, suffix);
        actions.add(action);
    }

    void addGroupHeader(@NonNull CharSequence header) {
        list.addGroupHeader(header);
        actions.add(null);
    }

    void addGroupFooter(@NonNull CharSequence footer) {
        list.addGroupFooter(footer);
        actions.add(null);
    }

    void show() {
        if (sheet == null) {
            YoSheet.Builder builder = YoSheet.create(theme.context)
                    .setTitle(title)
                    .setContent(list.getView())
                    .setHeight(height)
                    .setBackdropMode(YoSheet.BackdropMode.SOLID)
                    .setListener(new YoSheet.Listener() {
                        @Override
                        public void onDismissed(@NonNull YoSheet.DismissReason reason) {
                            onClosed.run();
                        }
                    });
            if (trailingAction != null) {
                builder.setTrailingAction(trailingAction);
            }
            sheet = builder.build();
        }
        sheet.show();
    }

    void dismiss() {
        if (sheet != null) {
            sheet.dismiss();
        }
    }

    boolean isShowing() {
        return sheet != null && sheet.isShown();
    }

    void destroy() {
        if (sheet != null) {
            sheet.destroy();
            sheet = null;
        }
        list.destroy();
        rowComponents.close();
    }

    private void onItemClick(int position) {
        if (position < 0 || position >= actions.size()) {
            return;
        }
        Runnable action = actions.get(position);
        if (action != null) {
            action.run();
        }
    }
}

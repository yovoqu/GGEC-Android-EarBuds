package com.ggec.earbuds.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yohu.youi.api.containers.YoPage;

/**
 * 以 {@link YoPage} 作为窗口根的 Activity 基类。
 *
 * <p>边缘到边缘、页面创建、页面销毁三件事只在这里做一次，子类只负责内容与交互。</p>
 */
public abstract class YoPageActivity extends AppCompatActivity {

    @Nullable
    private YoPage page;

    /**
     * 创建页面并设为 Activity 的窗口根。
     *
     * @param builder 已经配置好的 YoPage Builder（不要调用 build()）
     */
    @NonNull
    protected final YoPage installPage(@NonNull YoPage.Builder builder) {
        YoUi.edgeToEdge(this);
        YoPage created = builder.build();
        page = created;
        setContentView(created.getView());
        return created;
    }

    @NonNull
    protected final YoPage page() {
        if (page == null) {
            throw new IllegalStateException("installPage() has not been called");
        }
        return page;
    }

    @Override
    protected void onDestroy() {
        YoPage current = page;
        page = null;
        if (current != null) {
            current.destroy();
        }
        super.onDestroy();
    }
}

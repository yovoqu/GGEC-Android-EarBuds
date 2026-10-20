package com.ggec.earbuds.ui.common;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 页面级 YoUI 组件的释放集合。
 *
 * <p>YoUI 的主题订阅是静态的，唯一解绑入口是组件自己的 {@code destroy()}；
 * 因此菜单、面板、按钮、输入框等由页面创建的组件都必须登记到这里，在页面销毁时统一释放。
 * 释放顺序与登记顺序相反，且重复 {@link #close()} 不会重复执行。</p>
 */
public final class YoComponents {

    private final Deque<Runnable> releases = new ArrayDeque<>();
    private boolean closed;

    /** 登记一个释放动作；集合已关闭时立即执行，避免登记即泄漏。 */
    public void own(@NonNull Runnable release) {
        if (closed) {
            release.run();
        } else {
            releases.push(release);
        }
    }

    /** 释放全部已登记组件。 */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        while (!releases.isEmpty()) {
            try {
                releases.pop().run();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}

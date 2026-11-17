package com.ggec.earbuds.ui.shell;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.ggec.earbuds.ui.ai.history.AiHistorySheet;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.ui.update.UpdateOverlay;
import com.ggec.earbuds.wiring.AppContainer;

/**
 * 应用外壳：设备 / AI / 我的三个页签，底部是悬浮标签栏。
 *
 * <p>窗口级 edge-to-edge 由 {@link YoUi#edgeToEdge} 处理。AI 页自己避开状态栏，
 * 占位页由 {@link ShellPageView} 补顶部。</p>
 *
 * <p>APP 更新也挂在这个壳上：本页是唯一常驻入口，启动即检查、下载与安装的弹层都由
 * {@link UpdateOverlay} 渲染，流程状态在适配层侧（页面上只留 {@link UpdateUiPort} 接缝）。</p>
 */
public class MainActivity extends AppCompatActivity {

    private final YoComponents components = new YoComponents();

    private ShellViewModel shellViewModel;
    private UpdateUiPort updateHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);

        shellViewModel = new ViewModelProvider(this, container().shell().factory())
                .get(ShellViewModel.class);
        ShellScreen screen = new ShellScreen(this, shellViewModel, container().ai().factory(),
                container().device().homeFactory(),
                new ShellScreen.Listener() {
                    @Override
                    public void onSelect(@NonNull String key) {
                        shellViewModel.select(key);
                    }

                    @Override
                    public void onAIHistory() {
                        AppContainer container = container();
                        AiHistorySheet.show(
                                MainActivity.this, container.ai().historyViewModel());
                    }
                });
        components.own(screen::destroy);
        setContentView(screen.view());
        shellViewModel.uiState().observe(this, screen::render);

        updateHost = container().openUpdateUi(this);
        updateHost.attach(new UpdateOverlay(this, updateHost.actions()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateHost != null) {
            updateHost.onHostResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (updateHost != null) {
            updateHost.detach();
            updateHost = null;
        }
        components.close();
        super.onDestroy();
    }

    @NonNull
    private AppContainer container() {
        AppContainer container = AppContainer.from(this);
        if (container == null) {
            throw new IllegalStateException("AppContainer is not installed");
        }
        return container;
    }

}

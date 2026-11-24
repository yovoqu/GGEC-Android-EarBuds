package com.ggec.earbuds.ui.ai.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.history.AiHistorySheet;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;

/**
 * AI 对话首页宿主：承载文本对话与实时语音通话入口。
 */
public final class AiChatActivity extends AppCompatActivity {

    private AiChatPage page;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, AiChatActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AiTheme theme = AiTheme.of(this);
        YoUi.edgeToEdge(this);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            boolean dark = theme.isDark();
            controller.setAppearanceLightStatusBars(!dark);
            controller.setAppearanceLightNavigationBars(!dark);
        }

        AppContainer container = container();
        AiChatViewModel viewModel = new ViewModelProvider(this, container.ai().factory())
                .get(AiChatViewModel.class);
        page = new AiChatPage(this, viewModel, this::finish,
                () -> AiHistorySheet.show(this, container.ai().historyViewModel()));
        setContentView(page.view());
    }

    @Override
    protected void onDestroy() {
        if (page != null) {
            page.destroy();
            page = null;
        }
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

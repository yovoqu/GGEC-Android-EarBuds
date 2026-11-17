package com.ggec.earbuds.ui.device.record;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.controls.YoSegmentButton;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 录音页面总屏幕视图：集成导航头、YoSegmentButton 分段控制器、随录随传面板与本地录音面板。
 */
public final class RecordScreen {

    public interface Listener {
        void onBack();
        void onModeChanged(@NonNull RecordMode mode);
        void onStartStreamRecord();
        void onStopStreamRecord();
        void onTogglePlay(@NonNull SavedRecordItem item);
        void onDeleteLocalFile(@NonNull SavedRecordItem item);
        void onStartDeviceRecord();
        void onStopDeviceRecord();
        void onRefreshDeviceRecords();
        void onSyncDeviceRecord(@NonNull String fileId);
        void onCancelSync();
        void onDeleteDeviceRecord(@NonNull String fileId);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;
    private final YoComponents components = new YoComponents();

    private final LinearLayout root;
    private final TextView connectionStatusText;
    private final View connectionDot;
    private final FrameLayout contentHost;
    private final NestedScrollView scroll;
    private final LinearLayout column;
    private final LinearLayout segmentContainer;

    private YoSegmentButton segmentButton;
    private final StreamRecordPanel streamPanel;
    private final LocalRecordPanel localPanel;

    private RecordMode renderedMode = null;

    public RecordScreen(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = DeviceTheme.of(activity);
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        // 1. 顶部导航栏
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(theme.dp(DeviceTheme.HEADER_HEIGHT));
        header.setPadding(theme.dp(DeviceTheme.PAGE_INSET), 0, theme.dp(DeviceTheme.PAGE_INSET), 0);

        AiHeaderActionButton back = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.device_back)
                .onClick(v -> listener.onBack());
        header.addView(back, back.createLinearLayoutParams());

        TextView title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        title.setText(R.string.earbuds_record_title);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        header.addView(title, titleParams);

        // 右侧连接状态胶囊
        LinearLayout statusBadge = new LinearLayout(activity);
        statusBadge.setOrientation(LinearLayout.HORIZONTAL);
        statusBadge.setGravity(Gravity.CENTER_VERTICAL);
        statusBadge.setPadding(theme.dp(DeviceTheme.SPACE_S), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.SPACE_S), theme.dp(DeviceTheme.SPACE_XS));
        statusBadge.setBackground(theme.rounded(theme.fill, theme.dp(DeviceTheme.RADIUS_FIELD)));

        connectionDot = new View(activity);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.STATUS_DOT), theme.dp(DeviceTheme.STATUS_DOT));
        dotParams.setMarginEnd(theme.dp(DeviceTheme.SPACE_XS));
        statusBadge.addView(connectionDot, dotParams);

        connectionStatusText = theme.text(DeviceTheme.TEXT_CAPTION, theme.text2);
        statusBadge.addView(connectionStatusText, wrapContent());
        header.addView(statusBadge, wrapContent());

        root.addView(header, matchWrap());

        // 2. 分段控制器容器
        segmentContainer = new LinearLayout(activity);
        segmentContainer.setOrientation(LinearLayout.VERTICAL);
        segmentContainer.setPadding(theme.dp(DeviceTheme.LIST_EDGE), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.LIST_EDGE), theme.dp(DeviceTheme.SPACE_S));
        ensureSegmentButton(segmentContainer);
        root.addView(segmentContainer, matchWrap());

        // 3. 内容滚动容器
        contentHost = new FrameLayout(activity);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, theme.dp(DeviceTheme.SPACE_XL));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentHost.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 4. 初始化两大业务面板
        streamPanel = new StreamRecordPanel(activity, theme, new StreamRecordPanel.Listener() {
            @Override
            public void onStartStreamRecord() {
                listener.onStartStreamRecord();
            }

            @Override
            public void onStopStreamRecord() {
                listener.onStopStreamRecord();
            }

            @Override
            public void onTogglePlay(@NonNull SavedRecordItem item) {
                listener.onTogglePlay(item);
            }

            @Override
            public void onDeleteLocalFile(@NonNull SavedRecordItem item) {
                listener.onDeleteLocalFile(item);
            }
        });

        localPanel = new LocalRecordPanel(activity, theme, new LocalRecordPanel.Listener() {
            @Override
            public void onStartDeviceRecord() {
                listener.onStartDeviceRecord();
            }

            @Override
            public void onStopDeviceRecord() {
                listener.onStopDeviceRecord();
            }

            @Override
            public void onRefreshDeviceRecords() {
                listener.onRefreshDeviceRecords();
            }

            @Override
            public void onSyncDeviceRecord(@NonNull String fileId) {
                listener.onSyncDeviceRecord(fileId);
            }

            @Override
            public void onCancelSync() {
                listener.onCancelSync();
            }

            @Override
            public void onDeleteDeviceRecord(@NonNull String fileId) {
                listener.onDeleteDeviceRecord(fileId);
            }
        });

        column.addView(streamPanel.view(), matchWrap());
        column.addView(localPanel.view(), matchWrap());

        // Insets 沉浸式与刘海适配
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void ensureSegmentButton(LinearLayout container) {
        if (segmentButton == null) {
            segmentButton = YoSegmentButton.create(activity)
                    .addSegment(0, activity.getString(R.string.earbuds_record_tab_stream))
                    .addSegment(1, activity.getString(R.string.earbuds_record_tab_local))
                    .setType(YoSegmentButton.SegmentType.SINGLE_CHOICE)
                    .setSize(YoSegmentButton.Size.MEDIUM)
                    .setLayoutMode(YoSegmentButton.LayoutMode.DISTRIBUTE)
                    .setSelectedIndex(0)
                    .setOnSelectionChangeListener(index ->
                            listener.onModeChanged(index == 0 ? RecordMode.STREAM : RecordMode.LOCAL))
                    .build();
            components.own(segmentButton::destroy);
            container.addView(segmentButton.getView(), matchWrap());
        }
    }

    @NonNull
    public View view() {
        return root;
    }

    public void render(@NonNull RecordUiState state) {
        // 更新连接状态
        boolean connected = state.isConnected();
        connectionDot.setBackground(theme.rounded(connected ? DeviceTheme.CONNECTED_GREEN : theme.hint,
                theme.dp(DeviceTheme.STATUS_DOT / 2f)));
        connectionStatusText.setText(connected ? R.string.earbuds_record_status_connected : R.string.earbuds_record_status_disconnected);

        // 动态支持单模式与多模式展示：WY01 仅支持随录随传时隐藏分段控制器
        boolean singleMode = state.supportedModes().size() <= 1;
        segmentContainer.setVisibility(singleMode ? View.GONE : View.VISIBLE);

        // 切换分段面板可见性
        RecordMode mode = state.mode();
        if (renderedMode != mode || (singleMode && streamPanel.view().getVisibility() != View.VISIBLE)) {
            renderedMode = mode;
            if (!singleMode && segmentButton != null) {
                int targetIndex = mode == RecordMode.STREAM ? 0 : 1;
                if (segmentButton.getSelectedIndex() != targetIndex) {
                    segmentButton.setSelectedIndex(targetIndex);
                }
            }
            streamPanel.view().setVisibility(mode == RecordMode.STREAM ? View.VISIBLE : View.GONE);
            localPanel.view().setVisibility(mode == RecordMode.LOCAL ? View.VISIBLE : View.GONE);
        }

        // 分发渲染给对应面板
        if (mode == RecordMode.STREAM) {
            streamPanel.render(state);
        } else {
            localPanel.render(state);
        }
    }

    public void destroy() {
        components.close();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}

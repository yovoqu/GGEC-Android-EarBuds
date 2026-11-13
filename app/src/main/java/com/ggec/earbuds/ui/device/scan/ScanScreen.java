package com.ggec.earbuds.ui.device.scan;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.controls.YoSelect;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.List;

/** 扫描连接页视图。标题栏右侧提供设备类型下拉筛选，列表使用 RecyclerView + DiffUtil 增量复用。 */
public final class ScanScreen {

    public interface Listener {
        void onBack();

        void onScan();

        void onSelectFilter(@NonNull String modelId);

        void onConnect(@NonNull ScanItem item);
    }

    /** RSSI-only 局部刷新载荷：仅信号强度变化时走轻量 bindRssi，避免整卡重绑。 */
    private static final Object PAYLOAD_RSSI = new Object();

    private static final DiffUtil.ItemCallback<ScanItem> ITEM_DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull ScanItem oldItem, @NonNull ScanItem newItem) {
            return oldItem.modelId().equals(newItem.modelId())
                    && oldItem.bleAddress().equalsIgnoreCase(newItem.bleAddress());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ScanItem oldItem, @NonNull ScanItem newItem) {
            return oldItem.equals(newItem);
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull ScanItem oldItem, @NonNull ScanItem newItem) {
            // BLE 名称或 BLE 地址变化需要完整重绑；只有 RSSI 变化时局部刷新副标题。
            if (!oldItem.bleName().equals(newItem.bleName())
                    || !oldItem.bleAddress().equalsIgnoreCase(newItem.bleAddress())) {
                return null;
            }
            return PAYLOAD_RSSI;
        }
    };

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;
    private final YoComponents components = new YoComponents();

    private final LinearLayout root;
    private final TextView status;
    private final RecyclerView recyclerView;
    private final ScanDeviceAdapter adapter;
    private final LinearLayout emptyState;
    private final TextView emptyHeading;
    private final TextView emptyBody;
    private final YoButton action;
    private final YoSelect filterSelect;
    private final ScanConnectDialog connectDialog;

    private List<ScanFilterOption> renderedOptions = List.of();
    private String lastOptionsSignature = "";
    private String lastSelectedModelId = "";
    private boolean updatingFilter;
    @Nullable
    private Boolean lastScanning;
    @StringRes
    private int lastEmptyTitleRes;
    @StringRes
    private int lastEmptyBodyRes;

    public ScanScreen(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = DeviceTheme.of(activity);
        this.listener = listener;
        this.connectDialog = new ScanConnectDialog(activity);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        filterSelect = buildFilterSelect();
        root.addView(buildHeader(), matchWrap());

        status = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text3);
        status.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S));
        status.setVisibility(View.GONE);
        root.addView(status, matchWrap());

        FrameLayout contentContainer = new FrameLayout(activity);
        recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setClipToPadding(false);
        // RV 尺寸固定为 MATCH_PARENT，内容变化不改变自身大小：开启后避免每次 submitList 触发 requestLayout。
        recyclerView.setHasFixedSize(true);
        recyclerView.setPadding(0, 0, 0, theme.dp(DeviceTheme.SPACE_S));
        recyclerView.setItemAnimator(new ScanCardAnimator(theme.dp(ScanListMotion.APPEAR_OFFSET_DP)));
        adapter = new ScanDeviceAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setVisibility(View.GONE);
        contentContainer.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyState = new LinearLayout(activity);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.EMPTY_TOP),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XL));
        emptyHeading = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        emptyHeading.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        emptyState.addView(emptyHeading, matchWrap());
        emptyBody = theme.text(DeviceTheme.TEXT_CALLOUT, theme.text2);
        emptyBody.setLineSpacing(theme.dpf(DeviceTheme.LINE_SPACING_EXTRA), 1f);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        emptyState.addView(emptyBody, bodyParams);
        contentContainer.addView(emptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(contentContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        action = YoButton.create(activity).build();
        components.own(action::destroy);
        action.setText(activity.getString(R.string.device_scan_start));
        action.setOnClickListener(v -> listener.onScan());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(DeviceTheme.PRIMARY_ACTION_HEIGHT));
        actionParams.setMargins(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_M));
        root.addView(action.getView(), actionParams);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        bindFilter(ScanUiState.idle());
        renderEmptyState(false, false, 0);
    }

    @NonNull
    public View view() {
        return root;
    }

    public void destroy() {
        connectDialog.destroy();
        recyclerView.setAdapter(null);
        components.close();
    }

    public void render(@NonNull ScanUiState state) {
        connectDialog.render(state.connectPhase() == ScanConnectPhase.CONNECTING);
        bindFilter(state);
        boolean scanning = state.scanning();
        if (lastScanning == null || lastScanning != scanning) {
            lastScanning = scanning;
            action.setText(activity.getString(scanning
                    ? R.string.device_scan_stop : R.string.device_scan_start));
        }
        List<ScanItem> results = state.results();
        if (results.isEmpty()) {
            if (status.getVisibility() != View.GONE) {
                status.setVisibility(View.GONE);
            }
            if (recyclerView.getVisibility() != View.GONE) {
                recyclerView.setVisibility(View.GONE);
            }
            if (!adapter.getCurrentList().isEmpty()) {
                adapter.submitList(List.of());
            }
            if (emptyState.getVisibility() != View.VISIBLE) {
                emptyState.setVisibility(View.VISIBLE);
            }
            renderEmptyState(scanning, state.hasScanned(), state.totalDiscoveredCount());
            return;
        }

        if (status.getVisibility() != View.VISIBLE) {
            status.setVisibility(View.VISIBLE);
        }
        String nextStatus = scanning
                ? activity.getString(R.string.device_scan_searching)
                : activity.getString(R.string.device_scan_found_count, results.size());
        if (!nextStatus.contentEquals(status.getText())) {
            status.setText(nextStatus);
        }

        if (emptyState.getVisibility() != View.GONE) {
            emptyState.setVisibility(View.GONE);
        }
        if (recyclerView.getVisibility() != View.VISIBLE) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        adapter.submitList(results);
    }

    @NonNull
    private YoSelect buildFilterSelect() {
        YoSelect select = YoSelect.create(activity)
                .setControlSize(YoSelect.ControlSize.SMALL)
                .setArrowPosition(YoSelect.ArrowPosition.END)
                .setMenuAlign(YoSelect.MenuAlignType.END)
                .setOptionWidth(YoSelect.OptionWidthMode.FIT_CONTENT)
                .setShowDefaultSelectedIcon(true)
                .setOnSelectListener((index, text, itemId) -> {
                    if (updatingFilter) {
                        return;
                    }
                    // YoSelect.onSelect 的第一个参数是列表序号，addOption 传入的 id 在第三个参数。
                    if (itemId >= 0 && itemId < renderedOptions.size()) {
                        listener.onSelectFilter(renderedOptions.get(itemId).modelId());
                    }
                })
                .build();
        components.own(select::destroy);
        return select;
    }

    private void bindFilter(@NonNull ScanUiState state) {
        List<ScanFilterOption> options = state.filterOptions().isEmpty()
                ? ScanUiState.defaultFilterOptions() : state.filterOptions();
        String signature = optionsSignature(options);
        boolean optionsChanged = !signature.equals(lastOptionsSignature);

        if (optionsChanged) {
            updatingFilter = true;
            try {
                renderedOptions = options;
                lastOptionsSignature = signature;
                filterSelect.clearOptions();
                for (int i = 0; i < options.size(); i++) {
                    filterSelect.addOption(i, optionLabel(options.get(i)));
                }
            } finally {
                updatingFilter = false;
            }
        }

        int selectedIndex = findOptionIndex(renderedOptions, state.selectedFilterModelId());
        String selectedModelId = selectedIndex >= 0
                ? renderedOptions.get(selectedIndex).modelId()
                : ScanFilterOption.ALL_MODELS;
        if (optionsChanged || !selectedModelId.equals(lastSelectedModelId)) {
            updatingFilter = true;
            try {
                lastSelectedModelId = selectedModelId;
                String selectedLabel = selectedIndex >= 0
                        ? optionLabel(renderedOptions.get(selectedIndex))
                        : activity.getString(R.string.device_scan_filter_all);
                filterSelect.setValue(selectedLabel);
                if (selectedIndex >= 0) {
                    filterSelect.setSelectedItemId(selectedIndex);
                }
                filterSelect.getView().setContentDescription(
                        activity.getString(R.string.device_scan_filter_desc) + " " + selectedLabel);
            } finally {
                updatingFilter = false;
            }
        }
    }

    @NonNull
    private String optionLabel(@NonNull ScanFilterOption option) {
        return option.isAll() || option.label().isEmpty()
                ? activity.getString(R.string.device_scan_filter_all)
                : option.label();
    }

    @NonNull
    private String optionsSignature(@NonNull List<ScanFilterOption> options) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                builder.append('|');
            }
            ScanFilterOption option = options.get(i);
            builder.append(option.modelId()).append('=').append(optionLabel(option));
        }
        return builder.toString();
    }

    private static int findOptionIndex(@NonNull List<ScanFilterOption> options,
                                       @NonNull String modelId) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).modelId().equals(modelId)) {
                return i;
            }
        }
        return options.isEmpty() ? -1 : 0;
    }

    private void renderEmptyState(boolean scanning, boolean hasScanned, int totalDiscoveredCount) {
        int titleRes;
        int bodyRes;
        if (totalDiscoveredCount > 0) {
            titleRes = R.string.device_scan_filter_empty_title;
            bodyRes = R.string.device_scan_filter_empty_body;
        } else if (scanning) {
            titleRes = R.string.device_scan_searching;
            bodyRes = R.string.device_scan_searching_hint;
        } else if (!hasScanned) {
            titleRes = R.string.device_scan_idle_title;
            bodyRes = R.string.device_scan_idle_body;
        } else {
            titleRes = R.string.device_scan_empty_title;
            bodyRes = R.string.device_scan_empty_body;
        }
        if (titleRes != lastEmptyTitleRes) {
            lastEmptyTitleRes = titleRes;
            emptyHeading.setText(titleRes);
        }
        if (bodyRes != lastEmptyBodyRes) {
            lastEmptyBodyRes = bodyRes;
            emptyBody.setText(bodyRes);
        }
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(DeviceTheme.HEADER_HEIGHT));
        row.setPadding(theme.dp(DeviceTheme.PAGE_INSET), 0, theme.dp(DeviceTheme.PAGE_INSET), 0);

        AiHeaderActionButton back = new AiHeaderActionButton(activity)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.device_back)
                .onClick(v -> listener.onBack());
        row.addView(back, back.createLinearLayoutParams());

        TextView title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        title.setText(R.string.device_scan_title);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        row.addView(title, titleParams);

        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filterParams.setMarginStart(theme.dp(DeviceTheme.SPACE_S));
        row.addView(filterSelect.getView(), filterParams);
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private final class ScanDeviceAdapter extends ListAdapter<ScanItem, ScanDeviceViewHolder> {

        private ScanDeviceAdapter() {
            super(ITEM_DIFF);
        }

        @NonNull
        @Override
        public ScanDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ScanDeviceCard card = new ScanDeviceCard(activity, theme, listener::onConnect);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
            params.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
            params.topMargin = theme.dp(DeviceTheme.SPACE_S);
            card.view().setLayoutParams(params);
            return new ScanDeviceViewHolder(card);
        }

        @Override
        public void onBindViewHolder(@NonNull ScanDeviceViewHolder holder, int position) {
            holder.card.bind(getItem(position));
        }

        @Override
        public void onBindViewHolder(@NonNull ScanDeviceViewHolder holder, int position,
                                     @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && payloads.contains(PAYLOAD_RSSI)) {
                holder.card.bindRssi(getItem(position));
                return;
            }
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private static final class ScanDeviceViewHolder extends RecyclerView.ViewHolder {

        private final ScanDeviceCard card;

        private ScanDeviceViewHolder(@NonNull ScanDeviceCard card) {
            super(card.view());
            this.card = card;
        }
    }
}

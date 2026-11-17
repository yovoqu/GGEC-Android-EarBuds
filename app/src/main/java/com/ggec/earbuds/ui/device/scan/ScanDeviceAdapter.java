package com.ggec.earbuds.ui.device.scan;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 扫描结果列表适配器：只做「行绑定 + 点击转发」。
 *
 * <p>三种刷新载荷分开：设备名/地址变了整卡重绑，仅连接中状态或仅 RSSI 变化走轻量局部绑定，
 * 避免每次扫描上报都把整张卡片重建一遍。</p>
 */
public final class ScanDeviceAdapter extends ListAdapter<ScanItem, ScanDeviceAdapter.Holder> {

    /** RSSI-only 局部刷新载荷：仅信号强度变化时走轻量 bindRssi，避免整卡重绑。 */
    private static final Object PAYLOAD_RSSI = new Object();
    private static final Object PAYLOAD_CONNECTING = new Object();

    private static final DiffUtil.ItemCallback<ScanItem> ITEM_DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull ScanItem oldItem, @NonNull ScanItem newItem) {
            return oldItem.modelId().equals(newItem.modelId())
                    && oldItem.address().equalsIgnoreCase(newItem.address());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ScanItem oldItem, @NonNull ScanItem newItem) {
            return oldItem.equals(newItem);
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull ScanItem oldItem, @NonNull ScanItem newItem) {
            // 设备名称或 MAC 地址变化需要完整重绑；只有连接状态或 RSSI 变化时局部刷新。
            if (!oldItem.name().equals(newItem.name())
                    || !oldItem.address().equalsIgnoreCase(newItem.address())) {
                return null;
            }
            if (oldItem.connecting() != newItem.connecting()) {
                return PAYLOAD_CONNECTING;
            }
            if (oldItem.rssi() != newItem.rssi()) {
                return PAYLOAD_RSSI;
            }
            return null;
        }
    };

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final YoComponents components;
    private final Consumer<ScanItem> onItemClick;

    private boolean connecting;

    public ScanDeviceAdapter(@NonNull AppCompatActivity activity,
                             @NonNull DeviceTheme theme,
                             @NonNull YoComponents components,
                             @NonNull Consumer<ScanItem> onItemClick) {
        super(ITEM_DIFF);
        this.activity = Objects.requireNonNull(activity, "activity");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.components = Objects.requireNonNull(components, "components");
        this.onItemClick = Objects.requireNonNull(onItemClick, "onItemClick");
    }

    /** 全局「有设备正在连接」标志：变化时只重绑连接中一列，不重建列表。 */
    void setConnecting(boolean connecting) {
        if (this.connecting == connecting) {
            return;
        }
        this.connecting = connecting;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_CONNECTING);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ScanDeviceCard card = new ScanDeviceCard(activity, theme, onItemClick);
        components.own(card::destroy);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.topMargin = theme.dp(DeviceTheme.SPACE_S);
        card.view().setLayoutParams(params);
        return new Holder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.card.bind(getItem(position), connecting);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            if (payloads.contains(PAYLOAD_CONNECTING)) {
                holder.card.bindConnecting(getItem(position), connecting);
                return;
            }
            if (payloads.contains(PAYLOAD_RSSI)) {
                holder.card.bindRssi(getItem(position));
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    /** 行持有者：只暴露卡片，绑定规则在 {@link ScanDeviceCard} 内。 */
    static final class Holder extends RecyclerView.ViewHolder {

        private final ScanDeviceCard card;

        private Holder(@NonNull ScanDeviceCard card) {
            super(card.view());
            this.card = card;
        }
    }
}

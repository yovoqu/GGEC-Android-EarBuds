package com.yovo.yotheme.dialog.option;

import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.yovo.yotheme.dialog.api.style.OptionRowStyle;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项列表 Adapter：stable id + payload 局部刷新选中态
 */
public final class OptionListAdapter extends RecyclerView.Adapter<OptionListAdapter.OptionViewHolder> {

    public interface RowInteractionListener {
        void onRowClick(int index, @NonNull OptionItemSpec item);
    }

    private final Context context;
    @NonNull
    private List<OptionItemSpec> items;
    @NonNull
    private final OptionSelectionMode selectionMode;
    @NonNull
    private final OptionRowStyle rowStyle;
    private final int dialogCornerRadiusDp;
    @NonNull
    private final OptionSelectionStore selectionStore;
    @Nullable
    private final RowInteractionListener rowInteractionListener;

    public OptionListAdapter(@NonNull Context context,
                             @NonNull List<OptionItemSpec> items,
                             @NonNull OptionSelectionMode selectionMode,
                             @NonNull OptionRowStyle rowStyle,
                             int dialogCornerRadiusDp,
                             @NonNull OptionSelectionStore selectionStore,
                             @Nullable RowInteractionListener rowInteractionListener) {
        this.context = context;
        this.items = items;
        this.selectionMode = selectionMode;
        this.rowStyle = rowStyle;
        this.dialogCornerRadiusDp = dialogCornerRadiusDp;
        this.selectionStore = selectionStore;
        this.rowInteractionListener = rowInteractionListener;
        setHasStableIds(true);
    }

    public void submitItems(@NonNull List<OptionItemSpec> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId().hashCode();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public OptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        OptionRowViewHolder rowHolder = OptionRowViewHolder.create(
                context, selectionMode, rowStyle, dialogCornerRadiusDp, true);
        return new OptionViewHolder(rowHolder);
    }

    @Override
    public void onBindViewHolder(@NonNull OptionViewHolder holder, int position) {
        bindHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull OptionViewHolder holder,
                                 int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        OptionItemSpec item = items.get(position);
        holder.rowHolder.bindChecked(selectionStore.isSelected(item.getId()));
    }

    private void bindHolder(@NonNull OptionViewHolder holder, int position) {
        OptionItemSpec item = items.get(position);
        holder.rowHolder.bindFull(
                item,
                position,
                selectionStore.isSelected(item.getId()),
                rowStyle,
                v -> {
                    if (!item.isEnabled()) {
                        return;
                    }
                    if (rowInteractionListener != null) {
                        rowInteractionListener.onRowClick(position, item);
                    }
                });
        holder.rowHolder.setDividerVisible(position < getItemCount() - 1);
    }

    public void refreshSelectionState() {
        for (int i = 0; i < getItemCount(); i++) {
            notifyItemChanged(i, OptionRowViewHolder.PAYLOAD_CHECKED);
        }
    }

    static final class OptionViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        final OptionRowViewHolder rowHolder;

        OptionViewHolder(@NonNull OptionRowViewHolder rowHolder) {
            super(rowHolder.getItemView());
            this.rowHolder = rowHolder;
        }
    }
}


package com.ggec.earbuds.ui.device.scan;

import android.view.View;
import android.view.ViewPropertyAnimator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描卡片的出现、换位和离开。
 *
 * <p>RSSI 文本变化不走这里：{@link SimpleItemAnimator#setSupportsChangeAnimations(boolean)}
 * 关闭后，DiffUtil 的内容载荷只重绑副标题。时长和曲线全部来自 {@link ScanListMotion}。</p>
 */
public final class ScanCardAnimator extends SimpleItemAnimator {

    private final float appearOffsetPx;
    private final List<RecyclerView.ViewHolder> pendingRemoves = new ArrayList<>();
    private final List<Move> pendingMoves = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> pendingAdds = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> runningRemoves = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> runningMoves = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> runningAdds = new ArrayList<>();

    public ScanCardAnimator(float appearOffsetPx) {
        this.appearOffsetPx = appearOffsetPx;
        setSupportsChangeAnimations(false);
        setAddDuration(ScanListMotion.APPEAR_MS);
        setMoveDuration(ScanListMotion.MOVE_MS);
        setRemoveDuration(ScanListMotion.REMOVE_MS);
    }

    @Override
    public boolean animateRemove(@NonNull RecyclerView.ViewHolder holder) {
        cancel(holder.itemView);
        pendingRemoves.add(holder);
        return true;
    }

    @Override
    public boolean animateAdd(@NonNull RecyclerView.ViewHolder holder) {
        cancel(holder.itemView);
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(appearOffsetPx);
        pendingAdds.add(holder);
        return true;
    }

    @Override
    public boolean animateMove(@NonNull RecyclerView.ViewHolder holder,
                                int fromX, int fromY, int toX, int toY) {
        View view = holder.itemView;
        int startX = fromX + Math.round(view.getTranslationX());
        int startY = fromY + Math.round(view.getTranslationY());
        cancel(view);
        int deltaX = toX - startX;
        int deltaY = toY - startY;
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder);
            return false;
        }
        if (deltaX != 0) {
            view.setTranslationX(-deltaX);
        }
        if (deltaY != 0) {
            view.setTranslationY(-deltaY);
        }
        pendingMoves.add(new Move(holder, deltaX, deltaY));
        return true;
    }

    @Override
    public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder,
                                  @NonNull RecyclerView.ViewHolder newHolder,
                                  int fromLeft, int fromTop, int toLeft, int toTop) {
        return false;
    }

    @Override
    public void runPendingAnimations() {
        List<RecyclerView.ViewHolder> removes = new ArrayList<>(pendingRemoves);
        List<Move> moves = new ArrayList<>(pendingMoves);
        List<RecyclerView.ViewHolder> adds = new ArrayList<>(pendingAdds);
        pendingRemoves.clear();
        pendingMoves.clear();
        pendingAdds.clear();
        for (RecyclerView.ViewHolder holder : removes) {
            runRemove(holder);
        }
        for (Move move : moves) {
            runMove(move);
        }
        for (RecyclerView.ViewHolder holder : adds) {
            runAdd(holder);
        }
    }

    @Override
    public void endAnimation(@NonNull RecyclerView.ViewHolder holder) {
        cancel(holder.itemView);
        if (pendingRemoves.remove(holder)) {
            reset(holder.itemView);
            dispatchRemoveFinished(holder);
        }
        if (pendingAdds.remove(holder)) {
            reset(holder.itemView);
            dispatchAddFinished(holder);
        }
        pendingMoves.removeIf(move -> {
            if (move.holder != holder) {
                return false;
            }
            reset(holder.itemView);
            dispatchMoveFinished(holder);
            return true;
        });
        finishRunning(runningRemoves, holder, true);
        finishRunning(runningMoves, holder, false);
        if (runningAdds.remove(holder)) {
            reset(holder.itemView);
            dispatchAddFinished(holder);
        }
        dispatchFinishedWhenDone();
    }

    @Override
    public void endAnimations() {
        for (int index = pendingRemoves.size() - 1; index >= 0; index--) {
            endAnimation(pendingRemoves.get(index));
        }
        for (int index = pendingAdds.size() - 1; index >= 0; index--) {
            endAnimation(pendingAdds.get(index));
        }
        for (int index = pendingMoves.size() - 1; index >= 0; index--) {
            endAnimation(pendingMoves.get(index).holder);
        }
        cancelRunning(runningRemoves);
        cancelRunning(runningMoves);
        cancelRunning(runningAdds);
        dispatchFinishedWhenDone();
    }

    @Override
    public boolean isRunning() {
        return !pendingRemoves.isEmpty()
                || !pendingMoves.isEmpty()
                || !pendingAdds.isEmpty()
                || !runningRemoves.isEmpty()
                || !runningMoves.isEmpty()
                || !runningAdds.isEmpty();
    }

    private void runRemove(RecyclerView.ViewHolder holder) {
        runningRemoves.add(holder);
        dispatchRemoveStarting(holder);
        holder.itemView.animate()
                .alpha(0f)
                .setDuration(getRemoveDuration())
                .setInterpolator(ScanListMotion.EXIT)
                .withEndAction(() -> {
                    if (!runningRemoves.remove(holder)) {
                        return;
                    }
                    reset(holder.itemView);
                    dispatchRemoveFinished(holder);
                    dispatchFinishedWhenDone();
                })
                .start();
    }

    private void runAdd(RecyclerView.ViewHolder holder) {
        runningAdds.add(holder);
        dispatchAddStarting(holder);
        holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(getAddDuration())
                .setInterpolator(ScanListMotion.APPEAR)
                .withEndAction(() -> {
                    if (!runningAdds.remove(holder)) {
                        return;
                    }
                    reset(holder.itemView);
                    dispatchAddFinished(holder);
                    dispatchFinishedWhenDone();
                })
                .start();
    }

    private void runMove(Move move) {
        RecyclerView.ViewHolder holder = move.holder;
        runningMoves.add(holder);
        dispatchMoveStarting(holder);
        holder.itemView.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(getMoveDuration())
                .setInterpolator(ScanListMotion.MOVE)
                .withEndAction(() -> {
                    if (!runningMoves.remove(holder)) {
                        return;
                    }
                    reset(holder.itemView);
                    dispatchMoveFinished(holder);
                    dispatchFinishedWhenDone();
                })
                .start();
    }

    private void finishRunning(List<RecyclerView.ViewHolder> running,
                                RecyclerView.ViewHolder holder,
                                boolean remove) {
        if (!running.remove(holder)) {
            return;
        }
        reset(holder.itemView);
        if (remove) {
            dispatchRemoveFinished(holder);
        } else {
            dispatchMoveFinished(holder);
        }
    }

    private void cancelRunning(List<RecyclerView.ViewHolder> running) {
        for (int index = running.size() - 1; index >= 0; index--) {
            endAnimation(running.get(index));
        }
    }

    private void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
        }
    }

    private static void cancel(View view) {
        ViewPropertyAnimator animator = view.animate();
        animator.cancel();
    }

    private static void reset(View view) {
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
    }

    private record Move(RecyclerView.ViewHolder holder, int deltaX, int deltaY) {
    }
}

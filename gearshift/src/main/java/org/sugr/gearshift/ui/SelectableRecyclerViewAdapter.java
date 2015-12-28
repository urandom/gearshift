package org.sugr.gearshift.ui;

import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;

import java.util.ArrayList;
import java.util.List;

public abstract class SelectableRecyclerViewAdapter<VH extends RecyclerView.ViewHolder, I> extends RecyclerView.Adapter<VH> {
    public List<I> itemData = new ArrayList<>();

    protected SparseBooleanArray selectedItems = new SparseBooleanArray();

    protected SelectableRecyclerViewAdapter() {
        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            private static final int INSERT = 1;
            private static final int REMOVE = 2;
            private static final int MOVE = 3;

            @Override public void onItemRangeInserted(int positionStart, int itemCount) {
                shiftSelection(positionStart, -1, itemCount, INSERT);
                super.onItemRangeInserted(positionStart, itemCount);
            }

            @Override public void onItemRangeRemoved(int positionStart, int itemCount) {
                shiftSelection(positionStart, -1, itemCount, REMOVE);
                super.onItemRangeRemoved(positionStart, itemCount);
            }

            @Override public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                shiftSelection(Math.min(fromPosition, toPosition),
                    Math.max(fromPosition, toPosition), itemCount, MOVE);
                super.onItemRangeMoved(fromPosition, toPosition, itemCount);
            }

            private void shiftSelection(int start, int end, int count, int op) {
                List<Integer> deleted = new ArrayList<>();
                List<Integer> added = new ArrayList<>();
                for (int i = 0; i < selectedItems.size(); ++i) {
                    int index = selectedItems.keyAt(i);
                    if (index < start) {
                        continue;
                    }

                    if (end != - 1 && index > end) {
                        continue;
                    }

                    deleted.add(index);
                    if (op == INSERT) {
                        added.add(index + count);
                    } else if (op == REMOVE) {
                        if (index - count > 0) {
                            added.add(index - count);
                        }
                    } else if (op == MOVE) {
                    }
                }

                for (int index : deleted) {
                    selectedItems.delete(index);
                }

                for (int index : added) {
                    selectedItems.put(index, true);
                }


            }

        });
    }

    public SparseBooleanArray getSelectedItemPositions() {
        return selectedItems.clone();
    }

    public void setItemSelected(int position, boolean selected) {
        if (position == -1 || selected && !isItemSelectable(position)) {
            return;
        }

        if (selected) {
            selectedItems.put(position, true);
        } else {
            selectedItems.delete(position);
        }
        notifyItemChanged(position);
    }

    public void clearSelections() {
        SparseBooleanArray selected = selectedItems.clone();

        for (int i = 0; i < selected.size(); ++i) {
            int index = selected.keyAt(i);
            setItemSelected(index, false);
        }
    }

    public int getSelectedItemCount() {
        return selectedItems.size();
    }

    public List<I> getSelectedItems() {
        List<I> items =
            new ArrayList<>(selectedItems.size());

        for (int i = 0; i < selectedItems.size(); i++) {
            int index = selectedItems.keyAt(i);
            if (itemData.size() > index) {
                items.add(itemData.get(index));
            }
        }
        return items;
    }

    public abstract boolean isItemSelectable(int position);


    @Override public void onBindViewHolder(VH holder, int position) {
        holder.itemView.setActivated(
            isItemSelectable(position) && selectedItems.get(position, false));
    }

    @Override public int getItemCount() {
        return this.itemData.size();
    }

}

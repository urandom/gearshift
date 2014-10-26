package org.sugr.gearshift.ui;

import android.support.v7.widget.RecyclerView;
import android.util.SparseBooleanArray;

import java.util.ArrayList;
import java.util.List;

public abstract class SelectableRecyclerViewAdapter<VH extends RecyclerView.ViewHolder, I> extends RecyclerView.Adapter<VH> {
    public List<I> itemData = new ArrayList<>();

    protected SparseBooleanArray selectedItems = new SparseBooleanArray();

    public SparseBooleanArray getSelectedItemPositions() {
        return selectedItems.clone();
    }

    public void setItemSelected(int position, boolean selected) {
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
            new ArrayList<I>(selectedItems.size());

        for (int i = 0; i < selectedItems.size(); i++) {
            int index = selectedItems.keyAt(i);
            if (itemData.size() > index) {
                items.add(itemData.get(index));
            }
        }
        return items;
    }

    @Override public void onBindViewHolder(VH holder, int position) {
        holder.itemView.setActivated(selectedItems.get(position, false));
    }
}

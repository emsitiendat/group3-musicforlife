package com.example.musicforlife;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

public abstract class EndlessRecyclerViewScrollListener extends RecyclerView.OnScrollListener {
    private int visibleThreshold = 5;
    private RecyclerView.LayoutManager mLayoutManager;

    public EndlessRecyclerViewScrollListener(LinearLayoutManager layoutManager) {
        this.mLayoutManager = layoutManager;
    }

    public EndlessRecyclerViewScrollListener(GridLayoutManager layoutManager) {
        this.mLayoutManager = layoutManager;
        visibleThreshold = visibleThreshold * layoutManager.getSpanCount();
    }

    @Override
    public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
        if (dy <= 0) return;

        int totalItemCount = mLayoutManager.getItemCount();
        int lastVisibleItemPosition = 0;


        if (mLayoutManager instanceof StaggeredGridLayoutManager) {
            int[] lastVisibleItemPositions = ((StaggeredGridLayoutManager) mLayoutManager).findLastVisibleItemPositions(null);
            lastVisibleItemPosition = getLastVisibleItem(lastVisibleItemPositions);
        } else if (mLayoutManager instanceof GridLayoutManager) {
            lastVisibleItemPosition = ((GridLayoutManager) mLayoutManager).findLastVisibleItemPosition();
        } else if (mLayoutManager instanceof LinearLayoutManager) {
            lastVisibleItemPosition = ((LinearLayoutManager) mLayoutManager).findLastVisibleItemPosition();
        }

        if (!isLoading() && !isLastPage()) {
            if (lastVisibleItemPosition >= totalItemCount - visibleThreshold) {
                onLoadMore();
            }
        }
    }

    private int getLastVisibleItem(int[] lastVisibleItemPositions) {
        int maxSize = 0;
        for (int i = 0; i < lastVisibleItemPositions.length; i++) {
            if (i == 0) maxSize = lastVisibleItemPositions[i];
            else if (lastVisibleItemPositions[i] > maxSize) maxSize = lastVisibleItemPositions[i];
        }
        return maxSize;
    }

    public abstract void onLoadMore();
    public abstract boolean isLoading();
    public abstract boolean isLastPage();
}
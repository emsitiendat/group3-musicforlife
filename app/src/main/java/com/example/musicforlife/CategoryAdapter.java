package com.example.musicforlife;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private List<Category> categoryList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Category category);
    }

    public CategoryAdapter(List<Category> categoryList, OnItemClickListener listener) {
        this.categoryList = categoryList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_card, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categoryList.get(position);

        holder.tvName.setText(category.getName());
        holder.cardContainer.setCardBackgroundColor(Color.parseColor(category.getHexColor()));

        holder.imgCover.setImageResource(category.getImageResId());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList != null ? categoryList.size() : 0;
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        CardView cardContainer;
        TextView tvName;
        ImageView imgCover;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            cardContainer = itemView.findViewById(R.id.card_category_container);
            tvName = itemView.findViewById(R.id.tv_category_name);
            imgCover = itemView.findViewById(R.id.img_category_cover);
        }
    }


}
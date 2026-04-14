package com.example.musicforlife;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class QuickAddAdapter extends RecyclerView.Adapter<QuickAddAdapter.ViewHolder> {

    private Context context;
    private List<Song> suggestionList;
    private OnAddClickListener addListener;
    private OnPlayClickListener playListener;

    private OnArtistClickListener artistListener;

    private int currentPlayingSongId = -1;

    public interface OnAddClickListener {
        void onAddClick(Song song, int position);
    }

    public interface OnPlayClickListener {
        void onPlayClick(List<Song> songs, int position);
    }

    public interface OnArtistClickListener {
        void onArtistClick(String artistName);
    }

    public QuickAddAdapter(Context context, List<Song> suggestionList, OnAddClickListener addListener, OnPlayClickListener playListener, OnArtistClickListener artistListener) {
        this.context = context;
        this.suggestionList = suggestionList;
        this.addListener = addListener;
        this.playListener = playListener;
        this.artistListener = artistListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song_quick_add, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
        holder.itemView.setAlpha(1f);

        Song song = suggestionList.get(position);
        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());

        GlideHelper.loadCenterCropForAdapter(context, song.getCoverArtPath(), holder.imgCover);

        if (currentPlayingSongId == song.getId()) {
            holder.tvTitle.setTextColor(Color.parseColor("#FF5252"));
        } else {
            holder.tvTitle.setTextColor(Color.parseColor("#FFFFFF"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (playListener != null) {
                playListener.onPlayClick(suggestionList, holder.getAdapterPosition());
            }
        });

        holder.btnAdd.setOnClickListener(v -> {
            holder.itemView.animate().scaleX(0.8f).scaleY(0.8f).alpha(0f).setDuration(200).withEndAction(() -> {
                if (addListener != null) {
                    addListener.onAddClick(song, holder.getAdapterPosition());
                }
            }).start();
        });

        holder.tvArtist.setOnClickListener(v -> {
            if (artistListener != null && song.getArtist() != null) {
                artistListener.onArtistClick(song.getArtist());
            }
        });
    }

    @Override
    public int getItemCount() {
        return suggestionList != null ? suggestionList.size() : 0;
    }

    public void updateData(List<Song> newSongs) {
        this.suggestionList.clear();
        this.suggestionList.addAll(newSongs);
        notifyDataSetChanged();
    }

    public void appendData(List<Song> newSongs) {
        if (newSongs == null || newSongs.isEmpty()) return;
        int startPosition = this.suggestionList.size();
        this.suggestionList.addAll(newSongs);
        notifyItemRangeInserted(startPosition, newSongs.size());
    }

    public void removeItem(int position) {
        if (position >= 0 && position < suggestionList.size()) {
            suggestionList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, suggestionList.size());
        }
    }

    public void updatePlayingState(int songId) {
        this.currentPlayingSongId = songId;
        notifyDataSetChanged();
    }

    public List<Song> getSuggestionList() {
        if (this.suggestionList == null) {
            return new ArrayList<>();
        }
        return this.suggestionList;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTitle, tvArtist, btnAdd;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_quick_add_cover);
            tvTitle = itemView.findViewById(R.id.tv_quick_add_title);
            tvArtist = itemView.findViewById(R.id.tv_quick_add_artist);
            btnAdd = itemView.findViewById(R.id.btn_quick_add);
        }
    }
}
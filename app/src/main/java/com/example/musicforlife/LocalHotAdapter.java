package com.example.musicforlife;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.List;

public class LocalHotAdapter extends RecyclerView.Adapter<LocalHotAdapter.LocalViewHolder> {

    private Context context;
    private List<Song> songList;
    private SongAdapter.OnItemClickListener clickListener;
    private String cityName;

    public LocalHotAdapter(Context context, List<Song> songList, String cityName, SongAdapter.OnItemClickListener clickListener) {
        this.context = context;
        this.songList = songList;
        this.cityName = cityName;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public LocalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_local_hot, parent, false);
        return new LocalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocalViewHolder holder, int position) {
        Song song = songList.get(position);

        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());
        holder.tvBadge.setText("📍 " + cityName);

        if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == song.getId()) {
            holder.tvTitle.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            holder.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.tvTitle.getPaint().setFakeBoldText(true);
        } else {
            holder.tvTitle.setTextColor(android.graphics.Color.WHITE);
            holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.tvTitle.getPaint().setFakeBoldText(false);
        }

        holder.tvArtist.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).navigateToDetailFragment(ArtistFragment.newInstance(song.getArtist()), "ArtistFragment");
            }
        });

        GlideHelper.loadRoundedForAdapter(context, song.getCoverArtPath(), holder.imgCover, 16);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(songList, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return songList != null ? songList.size() : 0;
    }

    public static class LocalViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTitle, tvArtist, tvBadge;

        public LocalViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_local_cover);
            tvTitle = itemView.findViewById(R.id.tv_local_title);
            tvArtist = itemView.findViewById(R.id.tv_local_artist);
            tvBadge = itemView.findViewById(R.id.tv_local_badge);
        }
    }
}
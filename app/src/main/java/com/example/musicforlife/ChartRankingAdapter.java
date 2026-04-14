package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
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
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;

public class ChartRankingAdapter extends RecyclerView.Adapter<ChartRankingAdapter.ViewHolder> {
    private Context context;
    private List<Song> topSongs;

    public ChartRankingAdapter(Context context, List<Song> topSongs) {
        this.context = context;
        this.topSongs = topSongs;
    }

    public void updateData(List<Song> newTopSongs) {
        this.topSongs = newTopSongs;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chart_ranking, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = topSongs.get(position);

        holder.tvRank.setText(String.valueOf(position + 1));

        if (position == 0) holder.tvRank.setTextColor(Color.parseColor("#FFD700"));
        else if (position == 1) holder.tvRank.setTextColor(Color.parseColor("#C0C0C0"));
        else if (position == 2) holder.tvRank.setTextColor(Color.parseColor("#CD7F32"));
        else holder.tvRank.setTextColor(Color.parseColor("#FFFFFF"));

        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());

        if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == song.getId()) {
            holder.tvTitle.setTextColor(Color.parseColor("#FF5252"));
            holder.tvTitle.setTypeface(null, Typeface.BOLD);
            holder.tvTitle.getPaint().setFakeBoldText(true);
        } else {
            holder.tvTitle.setTextColor(Color.WHITE);
            holder.tvTitle.setTypeface(null, Typeface.NORMAL);
            holder.tvTitle.getPaint().setFakeBoldText(false);
        }

        holder.tvArtist.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).navigateToDetailFragment(
                        ArtistFragment.newInstance(song.getArtist()), "ArtistFragment"
                );
            }
        });

        GlideHelper.loadRoundedForAdapter(context, song.getCoverArtPath(), holder.imgCover, 8);

        holder.itemView.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(context, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_PLAY);
            serviceIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(topSongs));
            serviceIntent.putExtra("EXTRA_SONG_POSITION", position);
            context.startService(serviceIntent);
        });
    }
    @Override
    public int getItemCount() {
        return topSongs == null ? 0 : topSongs.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvTitle, tvArtist;
        ImageView imgCover;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tv_rank_number);
            tvTitle = itemView.findViewById(R.id.tv_chart_title);
            tvArtist = itemView.findViewById(R.id.tv_chart_artist);
            imgCover = itemView.findViewById(R.id.img_chart_cover);
        }
    }
}
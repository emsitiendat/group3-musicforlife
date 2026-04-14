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

import java.util.List;

public class StoryViralAdapter extends RecyclerView.Adapter<StoryViralAdapter.StoryViewHolder> {

    private Context context;
    private List<Song> songList;
    private SongAdapter.OnItemClickListener clickListener;

    public StoryViralAdapter(Context context, List<Song> songList, SongAdapter.OnItemClickListener clickListener) {
        this.context = context;
        this.songList = songList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_story_viral, parent, false);
        return new StoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        Song song = songList.get(position);

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

        GlideHelper.loadCenterCropForAdapter(context, song.getCoverArtPath(), holder.imgBg);

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

    public static class StoryViewHolder extends RecyclerView.ViewHolder {
        ImageView imgBg;
        TextView tvTitle, tvArtist;

        public StoryViewHolder(@NonNull View itemView) {
            super(itemView);
            imgBg = itemView.findViewById(R.id.img_story_bg);
            tvTitle = itemView.findViewById(R.id.tv_story_title);
            tvArtist = itemView.findViewById(R.id.tv_story_artist);
        }
    }
}
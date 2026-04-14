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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

public class SearchMomentCinematicAdapter extends RecyclerView.Adapter<SearchMomentCinematicAdapter.CinematicViewHolder> {

    private Context context;
    private List<Song> momentSongs;

    public SearchMomentCinematicAdapter(Context context, List<Song> momentSongs) {
        this.context = context;
        this.momentSongs = momentSongs;
    }

    public void updateData(List<Song> newSongs) {
        if (this.momentSongs != null && newSongs != null && this.momentSongs.size() == newSongs.size()) {
            boolean isSame = true;
            for (int i = 0; i < newSongs.size(); i++) {
                if (this.momentSongs.get(i).getId() != newSongs.get(i).getId()) {
                    isSame = false; break;
                }
            }
            if (isSame) return;
        }
        this.momentSongs.clear();
        this.momentSongs.addAll(newSongs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CinematicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_moment_cinematic, parent, false);
        return new CinematicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CinematicViewHolder holder, int position) {
        Song song = momentSongs.get(position);

        holder.tvTitle.setText(song.getTitle());
        String artistAndSearch = song.getArtist();
        holder.tvArtist.setText(artistAndSearch);

        GlideHelper.loadCenterCropForAdapter(context, song.getCoverArtPath(), holder.imgCover);

        boolean isCurrentSong = MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == song.getId();
        boolean isPlaying = MusicService.globalIsPlaying;

        if (isCurrentSong) {
            holder.tvTitle.setTextColor(Color.parseColor("#FF5252"));
            holder.tvTitle.setTypeface(null, Typeface.BOLD);

            if (isPlaying) {
                holder.iconPlayCenter.setImageResource(R.drawable.ic_pause_white);
            } else {
                holder.iconPlayCenter.setImageResource(R.drawable.ic_play_white);
            }
            holder.iconPlayCenter.setColorFilter(Color.parseColor("#FF5252"));

        } else {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            holder.tvTitle.setTypeface(null, Typeface.NORMAL);

            holder.iconPlayCenter.setImageResource(R.drawable.ic_play_white);
            holder.iconPlayCenter.clearColorFilter();
        }

        View.OnClickListener playClickListener = v -> {
            if (isCurrentSong) {
                Intent intent = new Intent(context, MusicService.class);
                intent.setAction(isPlaying ? MusicService.ACTION_PAUSE : MusicService.ACTION_RESUME);
                context.startService(intent);
            } else {
                Intent playIntent = new Intent(context, MusicService.class);
                playIntent.setAction(MusicService.ACTION_PLAY);
                playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(momentSongs));
                playIntent.putExtra("EXTRA_SONG_POSITION", position);
                context.startService(playIntent);
            }
        };

        holder.iconPlayCenter.setOnClickListener(playClickListener);
        holder.itemView.setOnClickListener(playClickListener);

        holder.tvArtist.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;
                ArtistFragment artistFragment = ArtistFragment.newInstance(song.getArtist());
                activity.navigateToDetailFragment(artistFragment, "ArtistFragment");
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;
                ArtistFragment artistFragment = ArtistFragment.newInstance(song.getArtist());
                activity.navigateToDetailFragment(artistFragment, "ArtistFragment");
            }
            return true;
        });
    }
    @Override
    public int getItemCount() { return momentSongs != null ? momentSongs.size() : 0; }

    public static class CinematicViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover, iconPlayCenter;
        TextView tvTitle, tvArtist;

        public CinematicViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_moment_cover);
            iconPlayCenter = itemView.findViewById(R.id.icon_play_center);
            tvTitle = itemView.findViewById(R.id.tv_moment_title);
            tvArtist = itemView.findViewById(R.id.tv_moment_artist);
        }
    }
}
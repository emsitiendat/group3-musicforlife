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

public class SearchNewReleaseSquareAdapter extends RecyclerView.Adapter<SearchNewReleaseSquareAdapter.SquareViewHolder> {

    private Context context;
    private List<Song> newReleaseSongs;

    public SearchNewReleaseSquareAdapter(Context context, List<Song> newReleaseSongs) {
        this.context = context;
        this.newReleaseSongs = newReleaseSongs;
    }

    public void updateData(List<Song> newSongs) {
        if (this.newReleaseSongs != null && newSongs != null && this.newReleaseSongs.size() == newSongs.size()) {
            boolean isSame = true;
            for (int i = 0; i < newSongs.size(); i++) {
                if (this.newReleaseSongs.get(i).getId() != newSongs.get(i).getId()) {
                    isSame = false; break;
                }
            }
            if (isSame) return;
        }
        this.newReleaseSongs.clear();
        this.newReleaseSongs.addAll(newSongs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SquareViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_new_release_square, parent, false);
        return new SquareViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SquareViewHolder holder, int position) {
        Song song = newReleaseSongs.get(position);

        holder.tvTitle.setText(song.getTitle());
        holder.tvArtistName.setText(song.getArtist());

        if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == song.getId()) {
            holder.tvTitle.setTextColor(Color.parseColor("#FF5252"));
            holder.tvTitle.setTypeface(null, Typeface.BOLD);
        } else {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            holder.tvTitle.setTypeface(null, Typeface.BOLD);
        }

        GlideHelper.loadCenterCropForAdapter(context, song.getCoverArtPath(), holder.imgCover);

        holder.itemView.setOnClickListener(v -> {
            Intent playIntent = new Intent(context, MusicService.class);
            playIntent.setAction(MusicService.ACTION_PLAY);
            playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(newReleaseSongs));
            playIntent.putExtra("EXTRA_SONG_POSITION", position);
            playIntent.putExtra("EXTRA_START_POSITION", 30000);
            context.startService(playIntent);
        });

        holder.tvArtistName.setOnClickListener(v -> {
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
    public int getItemCount() {
        return newReleaseSongs != null ? newReleaseSongs.size() : 0;
    }

    public static class SquareViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTitle;
        TextView tvArtistName;

        public SquareViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_release_cover);
            tvTitle = itemView.findViewById(R.id.tv_release_title);
            tvArtistName = itemView.findViewById(R.id.tv_release_artist);
        }
    }
}
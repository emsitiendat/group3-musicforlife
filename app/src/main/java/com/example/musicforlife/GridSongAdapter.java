package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.List;

public class GridSongAdapter extends RecyclerView.Adapter<GridSongAdapter.GridSongViewHolder> {

    private Context context;
    private List<Song> songList;

    public GridSongAdapter(Context context, List<Song> songList) {
        this.context = context;
        this.songList = songList;
    }

    @NonNull
    @Override
    public GridSongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_grid_song, parent, false);
        return new GridSongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridSongViewHolder holder, int position) {
        Song song = songList.get(position);

        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());

        if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == song.getId()) {
            holder.tvTitle.setTextColor(Color.parseColor("#FF5252"));
        } else {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        }

        holder.tvArtist.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;

                androidx.fragment.app.FragmentManager fm = activity.getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    String topFragmentName = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1).getName();
                    if ("ArtistFragment".equals(topFragmentName)) return;
                }

                ArtistFragment artistFragment = ArtistFragment.newInstance(song.getArtist());
                activity.navigateToDetailFragment(artistFragment, "ArtistFragment");
            }
        });

        GlideHelper.loadRoundedForAdapter(context, song.getCoverArtPath(), holder.imgCover, 16);

        holder.itemView.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(context, MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_PLAY);

            serviceIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songList));
            serviceIntent.putExtra("EXTRA_SONG_POSITION", position);

            context.startService(serviceIntent);

        });
    }
    @Override
    public int getItemCount() {
        return songList == null ? 0 : songList.size();
    }

    public void updateData(List<Song> newSongs) {
        this.songList = newSongs;
        notifyDataSetChanged();
    }

    public static class GridSongViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTitle;
        TextView tvArtist;

        public GridSongViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_song_cover);
            tvTitle = itemView.findViewById(R.id.tv_song_title);
            tvArtist = itemView.findViewById(R.id.tv_song_artist);
        }
    }
}
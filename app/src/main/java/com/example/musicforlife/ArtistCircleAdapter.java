package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.util.List;

public class ArtistCircleAdapter extends RecyclerView.Adapter<ArtistCircleAdapter.ViewHolder> {
    private Context context;
    private List<Song> sampleSongsWithArtists;

    public ArtistCircleAdapter(Context context, List<Song> sampleSongsWithArtists) {
        this.context = context;
        this.sampleSongsWithArtists = sampleSongsWithArtists;
    }

    public void updateData(List<Song> newArtists) {
        this.sampleSongsWithArtists = newArtists;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_artist_circle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = sampleSongsWithArtists.get(position);
        holder.tvName.setText(song.getArtist());

        GlideHelper.loadCircleForAdapter(context, song.getCoverArtPath(), holder.imgAvatar);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, StoryActivity.class);
            intent.putExtra("EXTRA_ARTIST_LIST", new java.util.ArrayList<>(sampleSongsWithArtists));
            intent.putExtra("EXTRA_CURRENT_INDEX", position);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return sampleSongsWithArtists == null ? 0 : sampleSongsWithArtists.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        TextView tvName;


        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.img_artist_avatar);
            tvName = itemView.findViewById(R.id.tv_artist_name);
        }
    }
}
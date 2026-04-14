package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    private Context context;
    private List<Playlist> playlistList;

    public PlaylistAdapter(Context context, List<Playlist> playlistList) {
        this.context = context;
        this.playlistList = playlistList;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return playlistList.get(position).getId();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = playlistList.get(position);
        if (playlist == null) return;

        holder.tvName.setText(playlist.getName());

        holder.layoutDualAvatar.setVisibility(View.GONE);
        holder.layoutCollage.setVisibility(View.GONE);
        holder.imgCover.setVisibility(View.VISIBLE);

        if (playlist.isBlend()) {
            holder.imgCover.setVisibility(View.GONE);
            holder.layoutDualAvatar.setVisibility(View.VISIBLE);

            GlideHelper.loadCircleForAdapter(context, playlist.getOwnerAvatar(), holder.imgOwnerSmall);

            if (playlist.getPartnerAvatar() != null && !playlist.getPartnerAvatar().equals("null")) {
                GlideHelper.loadCircleForAdapter(context, playlist.getPartnerAvatar(), holder.imgPartnerSmall);
            } else {
                holder.imgPartnerSmall.setImageResource(R.drawable.ic_add_friend);
            }

        } else if (playlist.isCustomCover() && playlist.getCoverArtPath() != null) {
            Glide.with(context)
                    .load(Utils.normalizeUrl(playlist.getCoverArtPath()))
                    .apply(new RequestOptions()
                            .transform(new CenterCrop(), new RoundedCorners(24))
                            .signature(new ObjectKey(playlist.getUpdatedAt())))
                    .placeholder(R.drawable.default_cover)
                    .into(holder.imgCover);
        } else if (playlist.getCollageCovers() != null && playlist.getCollageCovers().size() >= 4) {
            holder.imgCover.setVisibility(View.GONE);
            holder.layoutCollage.setVisibility(View.VISIBLE);
            loadCollageImages(holder, playlist.getCollageCovers());
        } else {
            String coverUrl = (playlist.getCoverArtPath() != null) ? playlist.getCoverArtPath() : "";
            Glide.with(context)
                    .load(Utils.normalizeUrl(coverUrl))
                    .apply(new RequestOptions()
                            .transform(new CenterCrop(), new RoundedCorners(24))
                            .signature(new ObjectKey(playlist.getUpdatedAt())))
                    .placeholder(R.drawable.default_cover)
                    .into(holder.imgCover);
        }

        holder.itemView.setOnClickListener(v -> {
            PlaylistDetailFragment fragment = new PlaylistDetailFragment();
            Bundle args = new Bundle();
            args.putInt("EXTRA_PLAYLIST_ID", playlist.getId());
            args.putString("EXTRA_PLAYLIST_NAME", playlist.getName());
            args.putString("EXTRA_PLAYLIST_COVER", playlist.getCoverArtPath());
            args.putBoolean("EXTRA_IS_CUSTOM_COVER", playlist.isCustomCover());

            args.putBoolean("EXTRA_IS_BLEND", playlist.isBlend());
            args.putString("EXTRA_OWNER_AVATAR", playlist.getOwnerAvatar());
            args.putString("EXTRA_PARTNER_AVATAR", playlist.getPartnerAvatar());
            args.putString("EXTRA_PARTNER_USERNAME", playlist.getPartnerUsername());

            fragment.setArguments(args);

            if (context instanceof MainActivity) {
                ((MainActivity) context).navigateToDetailFragment(fragment, "PlaylistDetailFragment");
            }
        });

        if (holder.btnQuickPlay != null) {
            holder.btnQuickPlay.setOnClickListener(v -> {
                fetchSongsAndPlay(playlist.getId());
            });
        }
    }

    private void loadCollageImages(PlaylistViewHolder holder, List<String> urls) {
        ImageView[] imgs = {holder.imgCollage1, holder.imgCollage2, holder.imgCollage3, holder.imgCollage4};
        for (int i = 0; i < 4; i++) {
            Glide.with(context)
                    .load(Utils.normalizeUrl(urls.get(i)))
                    .centerCrop()
                    .placeholder(R.drawable.default_cover)
                    .into(imgs[i]);
        }
    }

    private void fetchSongsAndPlay(int playlistId) {
        RetrofitClient.getApiService().getSongsInPlaylist(playlistId).enqueue(new Callback<List<Song>>() {
            @Override
            public void onResponse(@NonNull Call<List<Song>> call, @NonNull Response<List<Song>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Intent playIntent = new Intent(context, MusicService.class);
                    playIntent.setAction(MusicService.ACTION_PLAY);
                    playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(response.body()));
                    playIntent.putExtra("EXTRA_SONG_POSITION", 0);
                    context.startService(playIntent);
                } else {
                    Toast.makeText(context, "Playlist rỗng!", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {
                Toast.makeText(context, "Lỗi kết nối!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlistList != null ? playlistList.size() : 0;
    }

    public void updateData(List<Playlist> newPlaylists) {
        if (newPlaylists == null) return;
        this.playlistList.clear();
        this.playlistList.addAll(newPlaylists);
        notifyDataSetChanged();
    }

    public static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover, btnQuickPlay;
        TextView tvName;
        GridLayout layoutCollage;
        ImageView imgCollage1, imgCollage2, imgCollage3, imgCollage4;

        View layoutDualAvatar;
        ImageView imgOwnerSmall, imgPartnerSmall;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_playlist_cover);
            tvName = itemView.findViewById(R.id.tv_playlist_name);
            btnQuickPlay = itemView.findViewById(R.id.btn_quick_play);
            layoutCollage = itemView.findViewById(R.id.layout_collage_cover);
            imgCollage1 = itemView.findViewById(R.id.img_collage_1);
            imgCollage2 = itemView.findViewById(R.id.img_collage_2);
            imgCollage3 = itemView.findViewById(R.id.img_collage_3);
            imgCollage4 = itemView.findViewById(R.id.img_collage_4);

            layoutDualAvatar = itemView.findViewById(R.id.layout_dual_avatar_small);
            imgOwnerSmall = itemView.findViewById(R.id.img_owner_small);
            imgPartnerSmall = itemView.findViewById(R.id.img_partner_small);
        }
    }
}
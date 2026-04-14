package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<FeedItem> feedItems;
    private List<Song> playableSongs = new ArrayList<>();

    private boolean isFooterLoading = false;

    public FeedAdapter(Context context, List<FeedItem> feedItems) {
        this.context = context;
        this.feedItems = feedItems;
        extractPlayableSongs();
    }

    public void updateData(List<FeedItem> newItems) {
        this.feedItems = newItems;
        this.isFooterLoading = false;
        extractPlayableSongs();
        notifyDataSetChanged();
    }
    public void refreshPlayableSongs() {
        extractPlayableSongs();
    }

    public void showLoading() {
        if (!isFooterLoading) {
            isFooterLoading = true;
            feedItems.add(new FeedItem(FeedItem.TYPE_LOADING));
            notifyItemInserted(feedItems.size() - 1);
        }
    }

    public void hideLoading() {
        if (isFooterLoading) {
            isFooterLoading = false;
            if (feedItems != null && !feedItems.isEmpty()) {
                int lastIndex = feedItems.size() - 1;
                if (feedItems.get(lastIndex).getType() == FeedItem.TYPE_LOADING) {
                    feedItems.remove(lastIndex);
                    notifyItemRemoved(lastIndex);
                }
            }
        }
    }

    private void extractPlayableSongs() {
        playableSongs.clear();
        for (FeedItem item : feedItems) {
            if (item.getType() == FeedItem.TYPE_SONG) playableSongs.add(item.getSong());
        }
    }

    @Override
    public int getItemViewType(int position) {
        return feedItems.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == FeedItem.TYPE_LOADING) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_loading_bottom, parent, false);
            return new LoadingViewHolder(view);
        } else if (viewType == FeedItem.TYPE_PLAYLIST_CAROUSEL) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_feed_playlist_carousel, parent, false);
            return new CarouselViewHolder(view);
        } else if (viewType == FeedItem.TYPE_ARTIST_SPOTLIGHT) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_feed_artist_spotlight, parent, false);
            return new SpotlightViewHolder(view);
        } else if (viewType == FeedItem.TYPE_CHART_CAROUSEL) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_feed_chart_carousel, parent, false);
            return new ChartViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_song_reel, parent, false);
            return new ReelViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FeedItem item = feedItems.get(position);

        if (holder instanceof ReelViewHolder) {
            bindSong((ReelViewHolder) holder, item.getSong());
        } else if (holder instanceof CarouselViewHolder) {
            bindCarousel((CarouselViewHolder) holder, item.getSuggestedPlaylists());
        } else if (holder instanceof SpotlightViewHolder) {
            bindSpotlight((SpotlightViewHolder) holder, item.getSpotlightArtistSong());
        } else if (holder instanceof ChartViewHolder) {
            bindChart((ChartViewHolder) holder, item.getChartSongs());
        } else if (holder instanceof LoadingViewHolder) {
        }
    }


    private void bindSong(ReelViewHolder holder, Song song) {
        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());


        GlideHelper.loadRoundedForAdapter(context, song.getCoverArtPath(), holder.imgCover, 24);

        if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == song.getId()) {
            holder.tvTitle.setTextColor(Color.parseColor("#FF5252"));
            holder.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            holder.tvTitle.setTextColor(Color.WHITE);
            holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        }

        boolean isLiked = SongAdapter.globalLikedSongsCache.contains(song.getId());
        updateHeartUI(holder.btnFavorite, isLiked);

        View.OnClickListener playMusicListener = v -> {

            int songIndex = -1;
            for (int i = 0; i < playableSongs.size(); i++) {
                if (playableSongs.get(i).getId() == song.getId()) {
                    songIndex = i;
                    break;
                }
            }

            if (songIndex != -1) {
                int totalSize = playableSongs.size();
                int startIndex = Math.max(0, songIndex - 20);
                int endIndex = Math.min(totalSize, songIndex + 30);

                ArrayList<Song> safeList = new ArrayList<>(playableSongs.subList(startIndex, endIndex));
                int safePosition = songIndex - startIndex;

                Intent intent = new Intent(context, MusicService.class);
                intent.setAction(MusicService.ACTION_PLAY);
                intent.putExtra("EXTRA_SONG_LIST", safeList);
                intent.putExtra("EXTRA_SONG_POSITION", safePosition);
                context.startService(intent);
            } else {
                Toast.makeText(context, "Có lỗi xảy ra, không tìm thấy bài hát!", Toast.LENGTH_SHORT).show();
            }
        };
        holder.imgCover.setOnClickListener(playMusicListener);
        holder.layoutInfo.setOnClickListener(playMusicListener);

        holder.btnFavorite.setOnClickListener(v -> toggleFavorite(song, holder.btnFavorite));

        holder.btnShare.setOnClickListener(v -> Toast.makeText(context, "Đã nhấn Chia sẻ", Toast.LENGTH_SHORT).show());

        holder.tvArtist.setOnClickListener(v -> {
            if (context instanceof MainActivity) ((MainActivity) context).navigateToDetailFragment(ArtistFragment.newInstance(song.getArtist()), "ArtistFragment");
        });
    }
    private void bindCarousel(CarouselViewHolder holder, List<Playlist> playlists) {
        holder.rvPlaylists.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        holder.rvPlaylists.setAdapter(new PlaylistAdapter(context, playlists));
    }

    private void bindSpotlight(SpotlightViewHolder holder, Song song) {
        holder.tvArtistName.setText(song.getArtist());

        GlideHelper.loadCenterCropForAdapter(context, song.getCoverArtPath(), holder.imgSpotlightBg);

        holder.itemView.setOnClickListener(v -> {
            if (context instanceof MainActivity) ((MainActivity) context).navigateToDetailFragment(ArtistFragment.newInstance(song.getArtist()), "ArtistFragment");
        });
    }
    private void bindChart(ChartViewHolder holder, List<Song> chartSongs) {
        holder.rvChart.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        holder.rvChart.setAdapter(new ChartRankingAdapter(context, chartSongs));
    }

    @Override
    public int getItemCount() { return feedItems == null ? 0 : feedItems.size(); }

    private void updateHeartUI(ImageView btnHeart, boolean isLiked) {
        if (isLiked) {
            btnHeart.setImageResource(R.drawable.ic_heart_filled);
            btnHeart.setColorFilter(Color.parseColor("#FF5252"));
        } else {
            btnHeart.setImageResource(R.drawable.ic_heart_outline);
            btnHeart.setColorFilter(Color.parseColor("#E6FFFFFF"));
        }
    }

    private void toggleFavorite(Song song, ImageView btnHeart) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("isLoggedIn", false)) { Toast.makeText(context, "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show(); return; }

        boolean isCurrentlyLiked = SongAdapter.globalLikedSongsCache.contains(song.getId());
        boolean newLikedState = !isCurrentlyLiked;

        if (newLikedState) {
            SongAdapter.globalLikedSongsCache.add(song.getId());
            btnHeart.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                    .withEndAction(() -> btnHeart.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()).start();
        } else {
            SongAdapter.globalLikedSongsCache.remove(song.getId());
        }

        updateHeartUI(btnHeart, newLikedState);
        btnHeart.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        context.sendBroadcast(new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED")
                .setPackage(context.getPackageName())
                .putExtra("EXTRA_SONG", song)
                .putExtra("EXTRA_IS_LIKED", newLikedState));

        HashMap<String, Object> body = new HashMap<>();
        body.put("username", prefs.getString("userUsername", ""));
        body.put("song_id", song.getId());

        RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) { if (!response.isSuccessful()) revertFavorite(song, btnHeart, isCurrentlyLiked); }
            @Override public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) { revertFavorite(song, btnHeart, isCurrentlyLiked); }
        });
    }

    private void revertFavorite(Song song, ImageView btnHeart, boolean wasLiked) {
        if (wasLiked) SongAdapter.globalLikedSongsCache.add(song.getId()); else SongAdapter.globalLikedSongsCache.remove(song.getId());
        updateHeartUI(btnHeart, wasLiked);
    }

    public static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class ReelViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover, btnFavorite, btnShare; TextView tvTitle, tvArtist; View layoutInfo;
        public ReelViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_reel_cover); tvTitle = itemView.findViewById(R.id.tv_reel_title);
            tvArtist = itemView.findViewById(R.id.tv_reel_artist); btnFavorite = itemView.findViewById(R.id.btn_reel_favorite);
            btnShare = itemView.findViewById(R.id.btn_reel_share); layoutInfo = itemView.findViewById(R.id.layout_reel_info);
        }
    }
    public static class CarouselViewHolder extends RecyclerView.ViewHolder {
        RecyclerView rvPlaylists;
        public CarouselViewHolder(@NonNull View itemView) { super(itemView); rvPlaylists = itemView.findViewById(R.id.rv_playlists_carousel); }
    }
    public static class SpotlightViewHolder extends RecyclerView.ViewHolder {
        TextView tvArtistName; ImageView imgSpotlightBg;
        public SpotlightViewHolder(@NonNull View itemView) {
            super(itemView);
            tvArtistName = itemView.findViewById(R.id.tv_spotlight_artist_name);
            imgSpotlightBg = itemView.findViewById(R.id.img_spotlight_bg);
        }
    }
    public static class ChartViewHolder extends RecyclerView.ViewHolder {
        RecyclerView rvChart;
        public ChartViewHolder(@NonNull View itemView) { super(itemView); rvChart = itemView.findViewById(R.id.rv_chart_carousel); }
    }
}
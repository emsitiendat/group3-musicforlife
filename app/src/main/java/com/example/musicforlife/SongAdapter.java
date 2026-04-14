package com.example.musicforlife;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SONG = 1;
    private boolean hasHeader = false;

    private Context context;
    private List<Song> songList;
    private OnItemClickListener listener;

    public static final Set<Integer> globalLikedSongsCache = new HashSet<>();
    private static boolean isCacheLoaded = false;
    private boolean isFavoriteView = false;
    private boolean isGridView = false;
    private boolean isHorizontalList = false;

    public interface OnItemClickListener {
        void onItemClick(List<Song> songs, int position);
    }

    public SongAdapter(Context context, List<Song> songList, OnItemClickListener listener) {
        this.context = context;
        this.songList = songList;
        this.listener = listener;

        if (!isCacheLoaded) {
            loadFavoriteCache();
        }
    }
    public List<Song> getSongs() {
        return this.songList;
    }

    public void setHasHeader(boolean hasHeader) { this.hasHeader = hasHeader; }
    public void setFavoriteView(boolean favoriteView) { this.isFavoriteView = favoriteView; }
    public void setGridView(boolean gridView) { this.isGridView = gridView; }
    public void setHorizontalList(boolean horizontalList) {
        this.isHorizontalList = horizontalList;
        this.isGridView = true;
    }

    public void updateData(List<Song> newSongs) {
        if (this.songList != null && newSongs != null && this.songList.size() == newSongs.size()) {
            boolean isSame = true;
            for (int i = 0; i < newSongs.size(); i++) {
                if (this.songList.get(i).getId() != newSongs.get(i).getId()) {
                    isSame = false; break;
                }
            }
            if (isSame) return;
        }
        this.songList.clear();
        this.songList.addAll(newSongs);
        notifyDataSetChanged();
    }

    public void appendData(List<Song> newSongs) {
        if (newSongs == null || newSongs.isEmpty()) return;
        if (songList == null) songList = new ArrayList<>();
        int start = songList.size() + (hasHeader ? 1 : 0);
        songList.addAll(newSongs);
        notifyItemRangeInserted(start, newSongs.size());
    }

    private void loadFavoriteCache() {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) return;

        RetrofitClient.getApiService().getFavoriteSongs(username).enqueue(new Callback<List<Song>>() {
            @Override
            public void onResponse(@NonNull Call<List<Song>> call, @NonNull Response<List<Song>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    globalLikedSongsCache.clear();
                    for (Song s : response.body()) globalLikedSongsCache.add(s.getId());
                    isCacheLoaded = true;
                    notifyDataSetChanged();
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<Song>> call, @NonNull Throwable t) {}
        });
    }

    @Override
    public int getItemViewType(int position) {
        if (hasHeader && position == 0) return TYPE_HEADER;
        return TYPE_SONG;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_header_for_you, parent, false);
            return new HeaderViewHolder(view);
        }

        View view;
        if (isHorizontalList) view = LayoutInflater.from(context).inflate(R.layout.item_song_horizontal, parent, false);
        else if (isGridView) view = LayoutInflater.from(context).inflate(R.layout.item_grid_song, parent, false);
        else view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);

        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == TYPE_HEADER) return;

        int realPosition = hasHeader ? position - 1 : position;
        Song song = songList.get(realPosition);
        if (song == null) return;

        SongViewHolder songHolder = (SongViewHolder) holder;

        File localFile = new File(context.getFilesDir(), "offline_song_" + song.getId() + ".mp3");
        if (localFile.exists()) {
            song.setOffline(true);
            song.setLocalFilePath(localFile.getAbsolutePath());
        } else {
            song.setOffline(false);
            song.setLocalFilePath(null);
        }

        if (songHolder.tvTitle != null) songHolder.tvTitle.setText(song.getTitle());
        if (songHolder.tvArtist != null) songHolder.tvArtist.setText(song.getArtist());

        if (songHolder.tvArtist != null) {
            songHolder.tvArtist.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    int backStackEntryCount = activity.getSupportFragmentManager().getBackStackEntryCount();
                    if (backStackEntryCount > 0) {
                        String topFragmentName = activity.getSupportFragmentManager().getBackStackEntryAt(backStackEntryCount - 1).getName();
                        if ("ArtistFragment".equals(topFragmentName)) return;
                    }

                    ArtistFragment artistFragment = ArtistFragment.newInstance(song.getArtist());
                    activity.navigateToDetailFragment(artistFragment, "ArtistFragment");
                }
            });
        }

        if (songHolder.tvDuration != null) {
            int minutes = song.getDuration() / 60;
            int seconds = song.getDuration() % 60;
            songHolder.tvDuration.setText(String.format("%d:%02d", minutes, seconds));
            songHolder.tvDuration.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        if (songHolder.imgCover != null) {
            GlideHelper.loadRoundedWithSizeForAdapter(context, song.getCoverArtPath(), songHolder.imgCover, 8, 150, 150);
        }

        if (songHolder.btnHeart != null) {
            boolean isLiked = globalLikedSongsCache.contains(song.getId());
            updateHeartUI(songHolder.btnHeart, isLiked);
            songHolder.btnHeart.setOnClickListener(v -> {
                int pos = songHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                toggleFavorite(song, songHolder.btnHeart, pos);
            });
        }

        if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == song.getId()) {
            if (songHolder.tvTitle != null) {
                songHolder.tvTitle.setTextColor(Color.parseColor("#FF5252"));
                songHolder.tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            if (songHolder.viewPlayingIndicator != null) songHolder.viewPlayingIndicator.setVisibility(View.VISIBLE);
        } else {
            if (songHolder.tvTitle != null) {
                songHolder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                songHolder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            if (songHolder.viewPlayingIndicator != null) songHolder.viewPlayingIndicator.setVisibility(View.GONE);
        }

        songHolder.itemView.setOnClickListener(v -> {
            int pos = songHolder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int rPos = hasHeader ? pos - 1 : pos;

            if (listener != null) {
                listener.onItemClick(songList, rPos);
            }
        });

        songHolder.itemView.setOnLongClickListener(v -> {
            int pos = songHolder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            if (song.isOffline()) Toast.makeText(context, "Bài hát này đã có sẵn trong máy!", Toast.LENGTH_SHORT).show();
            else {
                Toast.makeText(context, "Đang tải: " + song.getTitle() + "...", Toast.LENGTH_SHORT).show();
                downloadSongToDevice(song, pos);
            }
            return true;
        });
    }
    private void downloadSongToDevice(Song song, int adapterPosition) {
        if (song.getFilePath() == null) return;
        RetrofitClient.getApiService().downloadFile(song.getFilePath()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        boolean isSaved = writeResponseBodyToDisk(response.body(), "offline_song_" + song.getId() + ".mp3");
                        if (isSaved && context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "Đã tải xong: " + song.getTitle(), Toast.LENGTH_SHORT).show();
                                notifyItemChanged(adapterPosition);
                            });
                        }
                    }).start();
                } else Toast.makeText(context, "Không thể tải file lúc này!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(context, "Lỗi chi tiết: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean writeResponseBodyToDisk(ResponseBody body, String filename) {
        try {
            File savedFile = new File(context.getFilesDir(), filename);
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                byte[] fileReader = new byte[4096];
                inputStream = body.byteStream();
                outputStream = new FileOutputStream(savedFile);
                while (true) {
                    int read = inputStream.read(fileReader);
                    if (read == -1) break;
                    outputStream.write(fileReader, 0, read);
                }
                outputStream.flush();
                return true;
            } catch (Exception e) { return false; }
            finally {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            }
        } catch (Exception e) { return false; }
    }

    @Override
    public int getItemCount() {
        int count = songList != null ? songList.size() : 0;
        return hasHeader ? count + 1 : count;
    }

    private void toggleFavorite(Song song, ImageButton btnHeart, int adapterPosition) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("isLoggedIn", false)) {
            Toast.makeText(context, "Vui lòng đăng nhập để thả tim!", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = prefs.getString("userUsername", "");
        boolean isCurrentlyLiked = globalLikedSongsCache.contains(song.getId());
        boolean newLikedState = !isCurrentlyLiked;

        if (newLikedState) {
            globalLikedSongsCache.add(song.getId());
        } else {
            globalLikedSongsCache.remove(song.getId());
            if (isFavoriteView) {
                int realPos = hasHeader ? adapterPosition - 1 : adapterPosition;
                songList.remove(realPos);
                notifyItemRemoved(adapterPosition);
                notifyItemRangeChanged(adapterPosition, getItemCount());
            }
        }
        updateHeartUI(btnHeart, newLikedState);

        btnHeart.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        btnHeart.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                .withEndAction(() -> btnHeart.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()).start();

        Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        favIntent.setPackage(context.getPackageName());
        favIntent.putExtra("EXTRA_SONG", song);
        favIntent.putExtra("EXTRA_IS_LIKED", newLikedState);
        context.sendBroadcast(favIntent);

        HashMap<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("song_id", song.getId());

        RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    revertFavoriteState(song, btnHeart, adapterPosition, isCurrentlyLiked);
                    Toast.makeText(context, "Lỗi đồng bộ máy chủ", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                revertFavoriteState(song, btnHeart, adapterPosition, isCurrentlyLiked);
                Toast.makeText(context, "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void revertFavoriteState(Song song, ImageButton btnHeart, int adapterPosition, boolean wasLiked) {
        if (wasLiked) globalLikedSongsCache.add(song.getId());
        else globalLikedSongsCache.remove(song.getId());

        if (isFavoriteView && wasLiked) {
            int realPos = hasHeader ? adapterPosition - 1 : adapterPosition;
            songList.add(realPos, song);
            notifyItemInserted(adapterPosition);
            notifyItemRangeChanged(adapterPosition, getItemCount());
        }
        updateHeartUI(btnHeart, wasLiked);

        Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        favIntent.setPackage(context.getPackageName());
        favIntent.putExtra("EXTRA_SONG", song);
        favIntent.putExtra("EXTRA_IS_LIKED", wasLiked);
        context.sendBroadcast(favIntent);
    }

    private void updateHeartUI(ImageButton btnHeart, boolean isLiked) {
        if (isLiked) {
            btnHeart.setImageResource(R.drawable.ic_heart_filled);
            btnHeart.setColorFilter(Color.parseColor("#FF5252"));
        } else {
            btnHeart.setImageResource(R.drawable.ic_heart_outline);
            btnHeart.setColorFilter(Color.parseColor("#B3FFFFFF"));
        }
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTitle, tvArtist, tvDuration;
        ImageButton btnHeart;
        View viewPlayingIndicator;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_song_cover);
            tvTitle = itemView.findViewById(R.id.tv_song_title);
            tvArtist = itemView.findViewById(R.id.tv_song_artist);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            btnHeart = itemView.findViewById(R.id.btn_song_heart);
            viewPlayingIndicator = itemView.findViewById(R.id.view_playing_indicator);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
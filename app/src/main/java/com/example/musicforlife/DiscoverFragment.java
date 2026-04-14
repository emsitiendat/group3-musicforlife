package com.example.musicforlife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DiscoverFragment extends Fragment {

    private RecyclerView rvDiscoverCards;
    private TextView tvEmpty;
    private ImageButton btnClose;
    private ImageButton btnSwipeLeft, btnPlayPause, btnSwipeRight;
    private TextView btnRefresh;
    private ProgressBar progressBar;

    private DiscoverAdapter discoverAdapter;
    private List<Song> discoverSongs = new ArrayList<>();

    private final BroadcastReceiver mainPlayerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicService.ACTION_UPDATE_UI.equals(intent.getAction())) {
                boolean isMainPlaying = intent.getBooleanExtra("EXTRA_IS_PLAYING", false);
                if (btnPlayPause != null) {
                    btnPlayPause.setImageResource(isMainPlaying ? R.drawable.ic_pause_white : R.drawable.ic_play_white);
                }
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter(MusicService.ACTION_UPDATE_UI);
        ContextCompat.registerReceiver(requireContext(), mainPlayerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_discover, container, false);

        rvDiscoverCards = view.findViewById(R.id.rv_discover_cards);
        tvEmpty = view.findViewById(R.id.tv_empty_discover);
        btnClose = view.findViewById(R.id.btn_close_discover);
        progressBar = view.findViewById(R.id.progress_bar_discover);

        btnSwipeLeft = view.findViewById(R.id.btn_swipe_left);
        btnPlayPause = view.findViewById(R.id.btn_discover_play);
        btnSwipeRight = view.findViewById(R.id.btn_swipe_right);

        btnRefresh = view.findViewById(R.id.btn_refresh_discover);

        btnClose.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        btnSwipeLeft.setOnClickListener(v -> handleSwipeAction(false));
        btnSwipeRight.setOnClickListener(v -> handleSwipeAction(true));
        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> fetchDiscoverSongs());
        }

        rvDiscoverCards.setLayoutManager(new LinearLayoutManager(requireContext()) {
            @Override
            public boolean canScrollVertically() { return false; }
        });

        discoverAdapter = new DiscoverAdapter(requireContext(), discoverSongs, this);
        discoverAdapter.setOnItemClickListener((songs, position) -> {
            playDiscoverSong(songs.get(position));
        });

        rvDiscoverCards.setAdapter(discoverAdapter);

        setupTinderSwipe();
        fetchDiscoverSongs();

        return view;
    }

    /**
     * Hàm quan trọng: Cập nhật trạng thái hiển thị của UI dựa trên việc có bài hát hay không.
     */
    private void updateUIVisibility(boolean hasSongs) {
        if (hasSongs) {
            rvDiscoverCards.setVisibility(View.VISIBLE);
            btnSwipeLeft.setVisibility(View.VISIBLE);
            btnPlayPause.setVisibility(View.VISIBLE);
            btnSwipeRight.setVisibility(View.VISIBLE);

            tvEmpty.setVisibility(View.GONE);
            if (btnRefresh != null) btnRefresh.setVisibility(View.GONE);
        } else {
            rvDiscoverCards.setVisibility(View.GONE);

            btnSwipeLeft.setVisibility(View.INVISIBLE);
            btnPlayPause.setVisibility(View.INVISIBLE);
            btnSwipeRight.setVisibility(View.INVISIBLE);

            tvEmpty.setVisibility(View.VISIBLE);
            if (btnRefresh != null) btnRefresh.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            forcePlayDiscoverSong();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            forcePlayDiscoverSong();
        }
    }

    private void forcePlayDiscoverSong() {
        if (!discoverSongs.isEmpty()) {
            Song currentDiscoverSong = discoverSongs.get(0);
            if (MusicService.globalCurrentSong == null || MusicService.globalCurrentSong.getId() != currentDiscoverSong.getId()) {
                playDiscoverSong(currentDiscoverSong);
            } else {
                if (btnPlayPause != null) {
                    btnPlayPause.setImageResource(MusicService.globalIsPlaying ? R.drawable.ic_pause_white : R.drawable.ic_play_white);
                }
            }
        }
    }

    private void fetchDiscoverSongs() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        tvEmpty.setVisibility(View.GONE);
        if (btnRefresh != null) btnRefresh.setVisibility(View.GONE);

        Call<List<Song>> call = RetrofitClient.getApiService().getRecommendations(1, 30);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                if (result != null && !result.isEmpty()) {
                    discoverSongs.clear();
                    for (Song song : result) {
                        if (!SongAdapter.globalLikedSongsCache.contains(song.getId())) {
                            discoverSongs.add(song);
                        }
                    }

                    if (!discoverSongs.isEmpty()) {
                        discoverAdapter.notifyDataSetChanged();
                        updateUIVisibility(true);

                        rvDiscoverCards.setAlpha(0f);
                        rvDiscoverCards.animate().alpha(1f).setDuration(300).start();

                        if (isResumed() && !isHidden()) {
                            forcePlayDiscoverSong();
                        }
                    } else {
                        updateUIVisibility(false);
                    }
                } else {
                    updateUIVisibility(false);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                updateUIVisibility(false);
                Toast.makeText(getContext(), "Lỗi tải nhạc: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupTinderSwipe() {
        ItemTouchHelper.SimpleCallback tinderSwipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Song swipedSong = discoverSongs.get(position);

                if (direction == ItemTouchHelper.LEFT) {
                    likeSong(swipedSong);
                }

                discoverSongs.remove(position);
                discoverAdapter.notifyItemRemoved(position);

                if (discoverSongs.isEmpty()) {
                    updateUIVisibility(false);
                } else if (position == 0) {
                    playDiscoverSong(discoverSongs.get(0));
                }
            }
        };
        new ItemTouchHelper(tinderSwipeCallback).attachToRecyclerView(rvDiscoverCards);
    }

    private void handleSwipeAction(boolean isLike) {
        if (discoverSongs.isEmpty()) return;
        Song currentSong = discoverSongs.get(0);

        if (isLike) {
            likeSong(currentSong);
            Toast.makeText(requireContext(), "Đã thêm vào Yêu thích ❤️", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Đã bỏ qua ❌", Toast.LENGTH_SHORT).show();
        }

        discoverSongs.remove(0);
        discoverAdapter.notifyItemRemoved(0);

        if (!discoverSongs.isEmpty()) {
            playDiscoverSong(discoverSongs.get(0));
        } else {
            updateUIVisibility(false);
        }
    }

    private void playDiscoverSong(Song song) {
        Intent playIntent = new Intent(requireContext(), MusicService.class);
        playIntent.setAction(MusicService.ACTION_PLAY);
        playIntent.putExtra("EXTRA_SONG", song);
        requireContext().startService(playIntent);
    }

    private void togglePlayPause() {
        if (discoverSongs.isEmpty()) return;
        Song currentDiscoverSong = discoverSongs.get(0);

        if (MusicService.globalCurrentSong == null || MusicService.globalCurrentSong.getId() != currentDiscoverSong.getId()) {
            playDiscoverSong(currentDiscoverSong);
        } else {
            Intent intent = new Intent(requireContext(), MusicService.class);
            intent.setAction(MusicService.globalIsPlaying ? MusicService.ACTION_PAUSE : MusicService.ACTION_RESUME);
            requireContext().startService(intent);
        }
    }

    private void likeSong(Song song) {
        if (SongAdapter.globalLikedSongsCache.contains(song.getId())) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) return;

        HashMap<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("song_id", song.getId());

        SongAdapter.globalLikedSongsCache.add(song.getId());
        sendFavoriteBroadcast(song, true);

        RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful()) revertLikeState(song);
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                revertLikeState(song);
            }
        });
    }

    private void sendFavoriteBroadcast(Song song, boolean isLiked) {
        Intent intent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        intent.setPackage(requireContext().getPackageName());
        intent.putExtra("EXTRA_SONG", song);
        intent.putExtra("EXTRA_IS_LIKED", isLiked);
        requireContext().sendBroadcast(intent);
    }

    private void revertLikeState(Song song) {
        if (getContext() == null) return;
        SongAdapter.globalLikedSongsCache.remove(song.getId());
        sendFavoriteBroadcast(song, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            requireContext().unregisterReceiver(mainPlayerReceiver);
        } catch (IllegalArgumentException ignored) {}
    }
}
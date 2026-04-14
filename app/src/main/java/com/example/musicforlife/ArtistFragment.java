package com.example.musicforlife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;

public class ArtistFragment extends Fragment {

    private ImageView imgBanner, imgAvatar;
    private TextView tvArtistName, tvFollowers, tvBio;
    private RecyclerView rvSongs;
    private FloatingActionButton btnPlayAll;
    private Toolbar toolbar;
    private CollapsingToolbarLayout collapsingToolbar;

    private LinearLayout layoutGlobalLoading;

    private android.widget.ImageButton btnArtistComment, btnArtistShare, btnSearchSong, btnCloseSearchArtist;
    private LinearLayout layoutSearchBarArtist;
    private android.widget.EditText etSearchSong;
    private TextView btnPostCommunity;

    private TextView btnReadMoreBio;
    private boolean isBioExpanded = false;

    private SongAdapter songAdapter;
    private List<Song> artistSongs = new ArrayList<>();
    private List<Song> fullSongsList = new ArrayList<>();
    private String artistNameQuery = "Unknown";
    private Button btnFollowArtist;
    private String myUsername;

    private final BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (MusicService.ACTION_UPDATE_UI.equals(action)) {
                Song realSong = (Song) intent.getSerializableExtra("EXTRA_SONG");
                if (realSong != null) {
                    MusicService.globalCurrentSong = realSong;
                    if (songAdapter != null) songAdapter.notifyDataSetChanged();
                }
            } else if ("com.example.musicforlife.ACTION_FAVORITE_CHANGED".equals(action)) {
                if (songAdapter != null) songAdapter.notifyDataSetChanged();
            }
        }
    };

    public static ArtistFragment newInstance(String artistName) {
        ArtistFragment fragment = new ArtistFragment();
        Bundle args = new Bundle();
        args.putString("EXTRA_ARTIST_NAME", artistName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            artistNameQuery = getArguments().getString("EXTRA_ARTIST_NAME", "Unknown");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_artist, container, false);

        imgBanner = view.findViewById(R.id.img_artist_banner);
        imgAvatar = view.findViewById(R.id.img_artist_avatar);
        tvArtistName = view.findViewById(R.id.tv_artist_name);
        tvFollowers = view.findViewById(R.id.tv_artist_followers);

        tvFollowers.setOnClickListener(v -> {
            UserListBottomSheet bottomSheet = UserListBottomSheet.newInstance("artist_followers", artistNameQuery);
            bottomSheet.show(getParentFragmentManager(), "UserListBottomSheet");
        });

        btnFollowArtist = view.findViewById(R.id.btn_artist_follow);
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        myUsername = prefs.getString("userUsername", "");

        btnFollowArtist.setOnClickListener(v -> toggleArtistFollow());
        tvBio = view.findViewById(R.id.tv_artist_bio);
        rvSongs = view.findViewById(R.id.rv_artist_songs);
        btnPlayAll = view.findViewById(R.id.btn_artist_play_all);
        toolbar = view.findViewById(R.id.toolbar_artist);
        collapsingToolbar = view.findViewById(R.id.collapsingToolbar);

        layoutGlobalLoading = view.findViewById(R.id.layout_global_loading);

        btnArtistComment = view.findViewById(R.id.btn_artist_comment);
        btnArtistShare = view.findViewById(R.id.btn_artist_share);
        btnSearchSong = view.findViewById(R.id.btn_search_song);
        layoutSearchBarArtist = view.findViewById(R.id.layout_search_bar_artist);
        etSearchSong = view.findViewById(R.id.et_search_song);
        btnCloseSearchArtist = view.findViewById(R.id.btn_close_search_artist);
        btnPostCommunity = view.findViewById(R.id.btn_post_community);
        btnReadMoreBio = view.findViewById(R.id.btn_read_more_bio);

        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() != null) {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        setupSocialListeners();
        setupSearchLogic();

        rvSongs.setLayoutManager(new LinearLayoutManager(requireContext()));

        songAdapter = new SongAdapter(requireContext(), artistSongs, (songs, position) -> {
            Intent serviceIntent = new Intent(requireContext(), MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_PLAY);
            serviceIntent.putExtra("EXTRA_SONG_LIST", (Serializable) songs);
            serviceIntent.putExtra("EXTRA_SONG_POSITION", position);
            requireContext().startService(serviceIntent);
        });
        rvSongs.setAdapter(songAdapter);

        fetchArtistData();

        btnPlayAll.setOnClickListener(v -> {
            if (artistSongs.isEmpty()) {
                Toast.makeText(requireContext(), "Nghệ sĩ này chưa có bài hát nào!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent serviceIntent = new Intent(requireContext(), MusicService.class);
            serviceIntent.setAction(MusicService.ACTION_PLAY);
            serviceIntent.putExtra("EXTRA_SONG_LIST", (Serializable) artistSongs);
            serviceIntent.putExtra("EXTRA_SONG_POSITION", 0);
            requireContext().startService(serviceIntent);
        });

        return view;
    }

    private void fetchArtistData() {
        Call<Artist> call = RetrofitClient.getApiService().getArtistDetails(artistNameQuery, myUsername);

        if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.VISIBLE);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<Artist>() {
            @Override
            public void onSuccess(Artist result) {
                if (result != null && result.success && result.artist != null) {
                    Artist.ArtistInfo info = result.artist;

                    tvArtistName.setText(info.name);
                    tvFollowers.setText(formatFollowers(info.followersCount) + " Người theo dõi");
                    collapsingToolbar.setTitle(info.name);

                    updateFollowArtistUI(info.is_following);

                    tvBio.setText(info.bio);
                    tvBio.post(() -> {
                        android.text.Layout layout = tvBio.getLayout();
                        if (layout != null) {
                            int lines = layout.getLineCount();
                            if (lines > 0 && layout.getEllipsisCount(lines - 1) > 0) {
                                btnReadMoreBio.setVisibility(View.VISIBLE);
                                btnReadMoreBio.setOnClickListener(v -> {
                                    isBioExpanded = !isBioExpanded;
                                    if (isBioExpanded) {
                                        tvBio.setMaxLines(Integer.MAX_VALUE);
                                        btnReadMoreBio.setText("Ẩn bớt");
                                    } else {
                                        tvBio.setMaxLines(3);
                                        btnReadMoreBio.setText("Đọc thêm");
                                    }
                                });
                            } else {
                                btnReadMoreBio.setVisibility(View.GONE);
                            }
                        }
                    });

                    if (getContext() == null) return;

                    artistSongs.clear();
                    fullSongsList.clear();
                    if (result.songs != null) {
                        fullSongsList.addAll(result.songs);
                        artistSongs.addAll(result.songs);
                    }
                    songAdapter.notifyDataSetChanged();

                    String bannerUrl = (info.bannerImageUrl != null) ? info.bannerImageUrl : info.profileImageUrl;

                    final int[] loadedCount = {0};

                    GlideHelper.OnImageLoadListener imageLoadListener = () -> {
                        loadedCount[0]++;

                        if (loadedCount[0] >= 2) {
                            if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                            if (btnPlayAll != null) btnPlayAll.setVisibility(View.VISIBLE);
                        }
                    };

                    GlideHelper.loadAvatar(ArtistFragment.this, info.profileImageUrl, imgAvatar, imageLoadListener);

                    GlideHelper.loadCenterCrop(ArtistFragment.this, bannerUrl, imgBanner, imageLoadListener);

                } else {
                    if (getContext() != null) Toast.makeText(requireContext(), "Không tìm thấy nghệ sĩ", Toast.LENGTH_SHORT).show();
                    if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                    if (getActivity() != null) requireActivity().getSupportFragmentManager().popBackStack();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (getContext() != null) Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                if (btnPlayAll != null) btnPlayAll.setVisibility(View.GONE);
            }
        });
    }

    private String formatFollowers(int count) {
        if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000) return String.format("%.1fK", count / 1000.0);
        return String.valueOf(count);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.ACTION_UPDATE_UI);
        filter.addAction("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        ContextCompat.registerReceiver(requireContext(), updateUIReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(updateUIReceiver);
        } catch (IllegalArgumentException e) { }
    }

    private void setupSocialListeners() {
        if (btnArtistComment != null) {
            btnArtistComment.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Tính năng Bình luận Nghệ sĩ đang phát triển", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnArtistShare != null) {
            btnArtistShare.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Chia sẻ trang nghệ sĩ: " + artistNameQuery, Toast.LENGTH_SHORT).show();
            });
        }

        if (btnPostCommunity != null) {
            btnPostCommunity.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Chuyển đến màn hình Viết bài đăng Cộng đồng", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupSearchLogic() {
        if (btnSearchSong == null || etSearchSong == null) return;

        btnSearchSong.setOnClickListener(v -> {
            if (layoutSearchBarArtist.getVisibility() == View.GONE) {
                layoutSearchBarArtist.setVisibility(View.VISIBLE);
                int accentColor = ContextCompat.getColor(requireContext(), R.color.color_accent);
                btnSearchSong.setColorFilter(accentColor);

                etSearchSong.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearchSong, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            } else {
                btnCloseSearchArtist.performClick();
            }
        });

        btnCloseSearchArtist.setOnClickListener(v -> {
            etSearchSong.setText("");
            layoutSearchBarArtist.setVisibility(View.GONE);

            int secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
            btnSearchSong.setColorFilter(secondaryColor);

            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearchSong.getWindowToken(), 0);

            artistSongs.clear();
            artistSongs.addAll(fullSongsList);
            if (songAdapter != null) songAdapter.notifyDataSetChanged();
        });

        etSearchSong.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterSongs(s.toString());
            }
        });
    }

    private void filterSongs(String query) {
        if (query.isEmpty()) {
            artistSongs.clear();
            artistSongs.addAll(fullSongsList);
            if (songAdapter != null) songAdapter.notifyDataSetChanged();
            return;
        }

        List<Song> filteredList = new ArrayList<>();
        String normalizedQuery = removeAccents(query).trim();

        for (Song song : fullSongsList) {
            String normalizedTitle = removeAccents(song.getTitle());
            if (normalizedTitle.contains(normalizedQuery)) {
                filteredList.add(song);
            }
        }

        artistSongs.clear();
        artistSongs.addAll(filteredList);

        if (songAdapter != null) songAdapter.notifyDataSetChanged();
    }

    private String removeAccents(String str) {
        if (str == null) return "";
        try {
            String temp = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
            return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'd').toLowerCase();
        } catch (Exception e) {
            return str.toLowerCase();
        }
    }
    private void toggleArtistFollow() {
        if (myUsername.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để theo dõi", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("username", myUsername);
        body.put("artist_name", artistNameQuery);

        Call<FollowResponse> call = RetrofitClient.getApiService().toggleArtistFollow(body);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<FollowResponse>() {
            @Override
            public void onSuccess(FollowResponse result) {
                if (result != null && result.isSuccess()) {
                    tvFollowers.setText(formatFollowers(result.getFollowersCount()) + " Người theo dõi");
                    updateFollowArtistUI(result.isFollowing());

                    Intent intent = new Intent("ACTION_FOLLOW_STATUS_CHANGED");
                    requireContext().sendBroadcast(intent);

                    Toast.makeText(getContext(), result.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(getContext(), "Lỗi: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateFollowArtistUI(boolean isFollowing) {
        if (isFollowing) {
            btnFollowArtist.setText("Đang theo dõi");
            btnFollowArtist.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_surface)));
            btnFollowArtist.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        } else {
            btnFollowArtist.setText("Theo dõi");
            btnFollowArtist.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            btnFollowArtist.setTextColor(Color.parseColor("#121212"));
        }
    }
}
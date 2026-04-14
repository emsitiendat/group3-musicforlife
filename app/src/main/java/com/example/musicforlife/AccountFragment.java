package com.example.musicforlife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;

public class AccountFragment extends Fragment implements ScrollToTopListener {

    private ImageView imgAvatar;
    private TextView tvUserName, tvUserEmail, tvUserBio;
    private TextView btnSettings, btnAbout, btnLogout, btnEditProfile, btnShareProfile;
    private View btnWrapped;
    private View layoutStatsBadges;
    private View layoutTopGrid;
    private RecyclerView rvTopSongsGrid;
    private GridSongAdapter gridSongAdapter;

    private LinearLayout layoutGlobalLoading;
    private LinearLayout layoutFollowingClick, layoutFollowersClick;

    private TextView tvFollowingCount, tvFollowersCount;

    private final BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (MusicService.ACTION_UPDATE_UI.equals(action)) {
                if (gridSongAdapter != null) {
                    gridSongAdapter.notifyDataSetChanged();
                }
            }
            else if ("ACTION_FOLLOW_STATUS_CHANGED".equals(action)) {
                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                String currentUsername = prefs.getString("userUsername", "");
                if (!currentUsername.isEmpty()) {
                    fetchUserStats(currentUsername);
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        updateUI();

        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.ACTION_UPDATE_UI);
        filter.addAction("ACTION_FOLLOW_STATUS_CHANGED");

        ContextCompat.registerReceiver(requireContext(), updateUIReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (gridSongAdapter != null) {
            gridSongAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(updateUIReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account, container, false);

        imgAvatar = view.findViewById(R.id.img_avatar);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);
        tvUserBio = view.findViewById(R.id.tv_user_bio);
        layoutStatsBadges = view.findViewById(R.id.layout_stats_badges);
        layoutFollowingClick = view.findViewById(R.id.layout_following_click);
        layoutFollowersClick = view.findViewById(R.id.layout_followers_click);

        tvFollowingCount = view.findViewById(R.id.tv_following_count);
        tvFollowersCount = view.findViewById(R.id.tv_followers_count);

        if (layoutFollowingClick != null) {
            layoutFollowingClick.setOnClickListener(v -> {
                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
                String currentUsername = prefs.getString("userUsername", "");

                if (isLoggedIn && !currentUsername.isEmpty()) {
                    UserListBottomSheet bottomSheet = UserListBottomSheet.newInstance("following", currentUsername);
                    bottomSheet.show(getParentFragmentManager(), "UserListBottomSheet");
                } else {
                    Toast.makeText(getContext(), "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (layoutFollowersClick != null) {
            layoutFollowersClick.setOnClickListener(v -> {
                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
                String currentUsername = prefs.getString("userUsername", "");

                if (isLoggedIn && !currentUsername.isEmpty()) {
                    UserListBottomSheet bottomSheet = UserListBottomSheet.newInstance("followers", currentUsername);
                    bottomSheet.show(getParentFragmentManager(), "UserListBottomSheet");
                } else {
                    Toast.makeText(getContext(), "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnSettings = view.findViewById(R.id.btn_settings);
        btnAbout = view.findViewById(R.id.btn_about);
        btnLogout = view.findViewById(R.id.btn_logout);
        btnWrapped = view.findViewById(R.id.btn_wrapped);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        btnShareProfile = view.findViewById(R.id.btn_share_profile);

        if (btnShareProfile != null) {
            btnShareProfile.setOnClickListener(v -> {
                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                String username = prefs.getString("userUsername", "");
                if (!username.isEmpty()) {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Music For Life Profile");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, "Hãy khám phá gu âm nhạc cực chất của tôi trên Music For Life! Tìm kiếm: @" + username);
                    startActivity(Intent.createChooser(shareIntent, "Chia sẻ hồ sơ qua..."));
                } else {
                    Toast.makeText(getContext(), "Vui lòng đăng nhập để chia sẻ!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        layoutTopGrid = view.findViewById(R.id.layout_top_grid);
        rvTopSongsGrid = view.findViewById(R.id.rv_top_songs_grid);
        rvTopSongsGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
        rvTopSongsGrid.setItemAnimator(null);

        layoutGlobalLoading = view.findViewById(R.id.layout_global_loading);

        if (btnWrapped != null) {
            btnWrapped.setOnClickListener(v -> {
                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

                if (isLoggedIn) {
                    if (getActivity() instanceof MainActivity) {
                        WrappedFragment wrappedFragment = new WrappedFragment();
                        ((MainActivity) getActivity()).navigateToDetailFragment(wrappedFragment, "WrappedFragment");
                    }
                } else {
                    Toast.makeText(getContext(), "Vui lòng đăng nhập để xem tổng kết!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                if (prefs.getBoolean("isLoggedIn", false)) {
                    Intent intent = new Intent(getContext(), EditProfileActivity.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(getContext(), "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    SettingsFragment settingsFragment = new SettingsFragment();
                    ((MainActivity) getActivity()).navigateToDetailFragment(settingsFragment, "SettingsFragment");
                }
            });
        }

        btnAbout.setOnClickListener(v -> Toast.makeText(getContext(), "Music For Life v1.0 - Trải nghiệm Âm nhạc Premium", Toast.LENGTH_SHORT).show());

        updateUI();

        return view;
    }

    private void updateUI() {
        if (getContext() == null || getActivity() == null) return;

        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String username = prefs.getString("userUsername", "");
        String avatarUrl = prefs.getString("userAvatar", "");

        if (isLoggedIn) {
            if (btnShareProfile != null) btnShareProfile.setVisibility(View.VISIBLE);
            if (btnEditProfile != null) btnEditProfile.setVisibility(View.VISIBLE);

            tvUserName.setText(prefs.getString("userName", "Người Dùng"));
            tvUserEmail.setText(username);

            String bio = prefs.getString("userBio", "");
            if (tvUserBio != null) {
                if (!bio.trim().isEmpty()) {
                    tvUserBio.setText(bio);
                    tvUserBio.setVisibility(View.VISIBLE);
                } else {
                    tvUserBio.setVisibility(View.GONE);
                }
            }

            GlideHelper.loadAvatar(this, avatarUrl, imgAvatar);

            if (layoutStatsBadges != null) layoutStatsBadges.setVisibility(View.VISIBLE);

            if (tvFollowersCount != null) tvFollowersCount.setText(String.valueOf(prefs.getInt("userFollowers", 0)));
            if (tvFollowingCount != null) tvFollowingCount.setText(String.valueOf(prefs.getInt("userFollowing", 0)));

            fetchUserStats(username);

            layoutTopGrid.setVisibility(View.VISIBLE);
            loadTop9SongsGrid(username);

            btnLogout.setText("Đăng xuất");
            btnLogout.setTextColor(Color.parseColor("#FF5252"));

            btnLogout.setOnClickListener(v -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();
                Toast.makeText(getContext(), "Đã đăng xuất!", Toast.LENGTH_SHORT).show();
                updateUI();

                Intent stopMusic = new Intent(getContext(), MusicService.class);
                requireActivity().stopService(stopMusic);
            });
        } else {
            if (btnShareProfile != null) btnShareProfile.setVisibility(View.GONE);
            if (btnEditProfile != null) btnEditProfile.setVisibility(View.GONE);

            tvUserName.setText("Khách");
            tvUserEmail.setText("Vui lòng đăng nhập để trải nghiệm");

            if (tvUserBio != null) tvUserBio.setVisibility(View.GONE);

            GlideHelper.loadAvatar(this, "", imgAvatar);

            if (layoutStatsBadges != null) layoutStatsBadges.setVisibility(View.GONE);
            layoutTopGrid.setVisibility(View.GONE);

            btnLogout.setText("Đăng nhập ngay");
            btnLogout.setTextColor(Color.WHITE);

            btnLogout.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AuthActivity.class);
                startActivity(intent);
            });
        }
    }

    private void fetchUserStats(String username) {
        Call<UserProfile> call = RetrofitClient.getApiService().getUserProfile(username, username);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                if (isAdded() && result != null) {
                    if (tvFollowingCount != null) tvFollowingCount.setText(String.valueOf(result.getFollowingCount()));
                    if (tvFollowersCount != null) tvFollowersCount.setText(String.valueOf(result.getFollowersCount()));

                    SharedPreferences.Editor editor = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit();
                    editor.putInt("userFollowers", result.getFollowersCount());
                    editor.putInt("userFollowing", result.getFollowingCount());
                    editor.apply();
                }
            }

            @Override
            public void onError(String errorMessage) {
            }
        });
    }

    private void loadTop9SongsGrid(String username) {
        if (username.isEmpty()) return;

        if (gridSongAdapter == null && layoutGlobalLoading != null) {
            layoutGlobalLoading.setVisibility(View.VISIBLE);
        }
        Call<List<Song>> call = RetrofitClient.getApiService().getListeningHistory(username);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null && !result.isEmpty()) {
                    List<Song> historyList = result;

                    new Thread(() -> {
                        HashMap<Integer, Integer> songCountMap = new HashMap<>();
                        HashMap<Integer, Song> songDetailsMap = new HashMap<>();

                        for (Song song : historyList) {
                            songCountMap.put(song.getId(), songCountMap.getOrDefault(song.getId(), 0) + 1);
                            songDetailsMap.put(song.getId(), song);
                        }

                        List<Map.Entry<Integer, Integer>> sortedList = new ArrayList<>(songCountMap.entrySet());
                        sortedList.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

                        List<Song> top9Songs = new ArrayList<>();
                        for (int i = 0; i < Math.min(9, sortedList.size()); i++) {
                            top9Songs.add(songDetailsMap.get(sortedList.get(i).getKey()));
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (isAdded() && getContext() != null) {
                                    if (gridSongAdapter == null) {
                                        gridSongAdapter = new GridSongAdapter(getContext(), top9Songs);
                                        rvTopSongsGrid.setAdapter(gridSongAdapter);
                                    } else {
                                        gridSongAdapter.updateData(top9Songs);
                                        if (rvTopSongsGrid.getAdapter() == null) {
                                            rvTopSongsGrid.setAdapter(gridSongAdapter);
                                        }
                                    }

                                    if (layoutGlobalLoading != null) {
                                        layoutGlobalLoading.setVisibility(View.GONE);
                                    }
                                }
                            });
                        }
                    }).start();

                } else {
                    layoutTopGrid.setVisibility(View.GONE);
                    if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                layoutTopGrid.setVisibility(View.GONE);
                if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void scrollToTop() {
        if (getView() != null) {
            android.widget.ScrollView scrollView = getView().findViewById(R.id.scroll_view_account);
            if (scrollView != null) {
                scrollView.smoothScrollTo(0, 0);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
            String currentUsername = prefs.getString("userUsername", "");

            if (isLoggedIn && !currentUsername.isEmpty()) {
                fetchUserStats(currentUsername);
            }
        }
    }
}
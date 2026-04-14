package com.example.musicforlife;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment implements ScrollToTopListener {
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;
    private ConstraintLayout layoutHeader;
    private TextView tvGreeting;
    private ImageButton btnNotifications;

    private TextView pillAll, pillMusic, pillPlaylist, pillArtist;
    private LinearLayout layoutDiscoverBlocks;
    private TextView tvFeedTitle;

    private LinearLayout layoutGlobalLoading;

    private LinearLayout layoutHomeBlendMix;

    private List<Song> rawSongs = new ArrayList<>();
    private List<Playlist> rawPlaylists = new ArrayList<>();
    private List<Song> rawChartSongs = new ArrayList<>();
    private List<FeedItem> mixedFeedList = new ArrayList<>();

    private List<Song> allLocalSongsPool = new ArrayList<>();

    private List<String> recentArtistsWindow = new ArrayList<>();

    private RecyclerView rvRecentlyPlayed, rvFeaturedPlaylists;
    private RecyclerView rvChartRanking, rvTopArtists;

    private RecyclerView rvTrendingStories, rvBlendMix, rvLocalHot;

    private SongAdapter recentlyPlayedAdapter;
    private PlaylistAdapter playlistAdapter;
    private ChartRankingAdapter chartRankingAdapter;
    private ArtistCircleAdapter artistCircleAdapter;

    private RecyclerView rvSocialListening, rvMoods;
    private SocialListeningAdapter socialListeningAdapter;
    private MoodAdapter moodAdapter;

    private RecyclerView rvAllSongs;
    private FeedAdapter feedAdapter;

    private StoryViralAdapter storyAdapter;
    private LocalHotAdapter localAdapter;

    private boolean isLoading = false;
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private boolean isLastPage = false;

    private int currentFilterType = 0;

    private ActivityResultLauncher<String> requestPermissionLauncher;

    private final BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MusicService.ACTION_UPDATE_UI.equals(action) || "com.example.musicforlife.ACTION_FAVORITE_CHANGED".equals(action)) {
                refreshAllAdapters();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                fetchCurrentLocation();
            } else {
                Toast.makeText(getContext(), "Bạn có thể chọn thành phố thủ công nhé!", Toast.LENGTH_SHORT).show();
            }
        });

        requestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Toast.makeText(getContext(), "Đã cấp quyền thông báo!", Toast.LENGTH_SHORT).show();

                        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                        String myUsername = prefs.getString("userUsername", "");
                        openNotificationSheet(myUsername);

                    } else {
                        Toast.makeText(getContext(), "Bạn cần cấp quyền để nhận thông báo mới!", Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        layoutHeader = view.findViewById(R.id.layout_home_header);
        tvGreeting = view.findViewById(R.id.tv_home_greeting);
        layoutDiscoverBlocks = view.findViewById(R.id.layout_discover_blocks);
        tvFeedTitle = view.findViewById(R.id.tv_feed_title);
        btnNotifications = view.findViewById(R.id.btn_notifications);
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v -> checkAndRequestNotificationPermission());
        }

        layoutGlobalLoading = view.findViewById(R.id.layout_global_loading);

        layoutHomeBlendMix = view.findViewById(R.id.layout_home_blend_mix);

        setDynamicGreeting();
        loadUserAvatar();

        pillAll = view.findViewById(R.id.pill_all);
        pillMusic = view.findViewById(R.id.pill_music);
        pillPlaylist = view.findViewById(R.id.pill_playlist);
        pillArtist = view.findViewById(R.id.pill_artist);
        setupFilterClicks();

        View imgAvatar = view.findViewById(R.id.img_home_avatar);
        if (imgAvatar != null) {
            imgAvatar.setOnClickListener(v -> {
                View navAccount = requireActivity().findViewById(R.id.nav_account);
                if (navAccount != null) navAccount.performClick();
            });
        }

        View cardRadar = view.findViewById(R.id.card_home_radar);
        if (cardRadar != null) {
            cardRadar.setOnClickListener(v -> {
                DiscoverFragment discoverFragment = new DiscoverFragment();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToDetailFragment(discoverFragment, "DiscoverFragment");
                }
            });
        }

        setupHorizontalLists(view);

        rvAllSongs = view.findViewById(R.id.rv_all_songs);
        if (rvAllSongs != null) {
            rvAllSongs.setLayoutManager(new LinearLayoutManager(getContext()));
            feedAdapter = new FeedAdapter(getContext(), new ArrayList<>());
            rvAllSongs.setAdapter(feedAdapter);
            setupPagination();
        }

        setupScrollAnimation(view);

        fetchFeaturedPlaylists();
        fetchAllSongs(false);
        fetchChartRankings();
        fetchTopArtists();

        return view;
    }

    private void setupHorizontalLists(View view) {
        SongAdapter.OnItemClickListener songClickListener = (songs, position) -> {
            Intent intent = new Intent(getContext(), MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY);
            intent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songs));
            intent.putExtra("EXTRA_SONG_POSITION", position);
            requireContext().startService(intent);
        };

        rvRecentlyPlayed = view.findViewById(R.id.rv_recently_played);
        if (rvRecentlyPlayed != null) {
            rvRecentlyPlayed.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            recentlyPlayedAdapter = new SongAdapter(getContext(), new ArrayList<>(), songClickListener);
            recentlyPlayedAdapter.setHorizontalList(true);
            rvRecentlyPlayed.setAdapter(recentlyPlayedAdapter);
        }

        rvFeaturedPlaylists = view.findViewById(R.id.rv_featured_playlists);
        if (rvFeaturedPlaylists != null) {
            rvFeaturedPlaylists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            playlistAdapter = new PlaylistAdapter(getContext(), new ArrayList<>());
            rvFeaturedPlaylists.setAdapter(playlistAdapter);
        }

        rvBlendMix = view.findViewById(R.id.rv_home_blend_mix);
        if (rvBlendMix != null) {
            rvBlendMix.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        }

        rvTrendingStories = view.findViewById(R.id.rv_trending_stories);
        if (rvTrendingStories != null) {
            rvTrendingStories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        }

        rvLocalHot = view.findViewById(R.id.rv_local_hot);
        if (rvLocalHot != null) {
            rvLocalHot.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        }

        rvChartRanking = view.findViewById(R.id.rv_chart_ranking);
        if (rvChartRanking != null) {
            rvChartRanking.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            chartRankingAdapter = new ChartRankingAdapter(getContext(), new ArrayList<>());
            rvChartRanking.setAdapter(chartRankingAdapter);
            setupChartSwipe();
        }

        rvTopArtists = view.findViewById(R.id.rv_top_artists);
        if (rvTopArtists != null) {
            rvTopArtists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            artistCircleAdapter = new ArtistCircleAdapter(getContext(), new ArrayList<>());
            rvTopArtists.setAdapter(artistCircleAdapter);
        }

        rvSocialListening = view.findViewById(R.id.rv_social_listening);
        if (rvSocialListening != null) {
            rvSocialListening.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        }

        rvMoods = view.findViewById(R.id.rv_moods);
        if (rvMoods != null) {
            rvMoods.setLayoutManager(new GridLayoutManager(getContext(), 3));
        }
    }

    private void setupMockDataForNewFeatures() {
        List<Mood> mockMoods = new ArrayList<>();
        mockMoods.add(new Mood("Thư giãn", "#2D4263"));
        mockMoods.add(new Mood("Tập luyện", "#C92A42"));
        mockMoods.add(new Mood("Tâm trạng", "#4A4E69"));
        mockMoods.add(new Mood("Tập trung", "#2A9D8F"));
        mockMoods.add(new Mood("Sôi động", "#F4A261"));
        mockMoods.add(new Mood("Lãng mạn", "#9B5DE5"));

        moodAdapter = new MoodAdapter(getContext(), mockMoods, mood -> {
            showMoodSongs(mood);
        });
        if (rvMoods != null) rvMoods.setAdapter(moodAdapter);

        List<FriendActivity> mockFriends = new ArrayList<>();
        Song dummySong1 = new Song(); dummySong1.setTitle("Bật tình yêu lên"); dummySong1.setArtist("Tăng Duy Tân");
        Song dummySong2 = new Song(); dummySong2.setTitle("Waiting For You"); dummySong2.setArtist("MONO");
        Song dummySong3 = new Song(); dummySong3.setTitle("Gió"); dummySong3.setArtist("Jank");

        mockFriends.add(new FriendActivity("Hải Nam", "https://i.pravatar.cc/150?img=11", dummySong1));
        mockFriends.add(new FriendActivity("Linh Chi", "https://i.pravatar.cc/150?img=5", dummySong2));
        mockFriends.add(new FriendActivity("Thành Phạm", "https://i.pravatar.cc/150?img=14", dummySong3));

        socialListeningAdapter = new SocialListeningAdapter(getContext(), mockFriends);
        if (rvSocialListening != null) rvSocialListening.setAdapter(socialListeningAdapter);

        SongAdapter.OnItemClickListener generalClickListener = (songs, position) -> {
            Intent intent = new Intent(getContext(), MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY);
            intent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songs));
            intent.putExtra("EXTRA_SONG_POSITION", position);
            requireContext().startService(intent);
        };

        if (!rawSongs.isEmpty()) {
            int limitStory = Math.min(rawSongs.size(), 5);
            List<Song> storySongs = new ArrayList<>(rawSongs.subList(0, limitStory));

            storyAdapter = new StoryViralAdapter(getContext(), storySongs, generalClickListener);
            if (rvTrendingStories != null) rvTrendingStories.setAdapter(storyAdapter);

            allLocalSongsPool = new ArrayList<>(rawSongs);

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation();
            } else {
                updateLocalHotUI("Hà Nội");
            }
        }

    }

    private void fetchBlendMixesForHome() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        if (username.isEmpty()) return;

        RetrofitClient.getApiService().getUserPlaylists(username).enqueue(new Callback<List<Playlist>>() {
            @Override
            public void onResponse(Call<List<Playlist>> call, retrofit2.Response<List<Playlist>> response) {
                if (!isAdded() || getContext() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    List<Playlist> blendMixes = new ArrayList<>();

                    for (Playlist p : response.body()) {
                        if (p.isBlend()) {
                            blendMixes.add(p);
                        }
                    }

                    if (layoutHomeBlendMix != null && rvBlendMix != null) {
                        if (layoutHomeBlendMix != null && rvBlendMix != null) {
                            if (blendMixes.isEmpty()) {
                                layoutHomeBlendMix.setVisibility(View.GONE);
                            } else {
                                layoutHomeBlendMix.setVisibility(View.VISIBLE);

                                BlendMixAdapter blendAdapter = new BlendMixAdapter(getContext(), blendMixes, playlist -> {
                                    PlaylistDetailFragment fragment = new PlaylistDetailFragment();
                                    Bundle args = new Bundle();
                                    args.putInt("EXTRA_PLAYLIST_ID", playlist.getId());
                                    args.putString("EXTRA_PLAYLIST_NAME", playlist.getName());
                                    args.putBoolean("EXTRA_IS_BLEND", true);

                                    args.putString("EXTRA_OWNER_AVATAR", playlist.getOwnerAvatar() != null ? playlist.getOwnerAvatar() : "");
                                    args.putString("EXTRA_PARTNER_AVATAR", playlist.getPartnerAvatar() != null ? playlist.getPartnerAvatar() : "");
                                    args.putString("EXTRA_PARTNER_USERNAME", playlist.getPartnerUsername() != null ? playlist.getPartnerUsername() : "");
                                    args.putString("EXTRA_OWNER_USERNAME", playlist.getOwnerUsername() != null ? playlist.getOwnerUsername() : "");

                                    fragment.setArguments(args);
                                    if (getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).navigateToDetailFragment(fragment, "PlaylistDetailFragment");
                                    }
                                });
                                rvBlendMix.setAdapter(blendAdapter);
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Playlist>> call, Throwable t) {
                if (isAdded() && layoutHomeBlendMix != null) {
                    layoutHomeBlendMix.setVisibility(View.GONE);
                }
            }
        });
    }

    private void fetchCurrentLocation() {
        if (getContext() == null) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateLocalHotUI("Hà Nội");
            return;
        }

        LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            try {
                Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location == null) location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (location != null) {
                    Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        String city = addresses.get(0).getAdminArea();
                        if (city == null) city = addresses.get(0).getLocality();
                        if (city != null) {
                            city = city.replace("Tỉnh ", "").replace("Thành phố ", "").replace("Thành Phố ", "");
                            updateLocalHotUI(city);
                            return;
                        }
                    }
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        updateLocalHotUI("Hà Nội");
    }

    private void updateLocalHotUI(String cityName) {
        if (getView() != null) {
            TextView tvLocalTitle = getView().findViewById(R.id.tv_local_hot_title);
            if (tvLocalTitle != null) {
                tvLocalTitle.setText("📍 Đang hot tại " + cityName + " ▾");

                tvLocalTitle.setOnClickListener(v -> showCitySelectionDialog());

                tvLocalTitle.setOnLongClickListener(v -> {
                    showLocalHotSongsBottomSheet(cityName);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    return true;
                });
            }
        }

        List<Song> shuffledList = new ArrayList<>(allLocalSongsPool);
        Collections.shuffle(shuffledList);
        int limit = Math.min(shuffledList.size(), 5);
        List<Song> localSongs = new ArrayList<>(shuffledList.subList(0, limit));

        SongAdapter.OnItemClickListener generalClickListener = (songs, position) -> {
            Intent intent = new Intent(getContext(), MusicService.class);
            intent.setAction(MusicService.ACTION_PLAY);
            intent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songs));
            intent.putExtra("EXTRA_SONG_POSITION", position);
            requireContext().startService(intent);
        };

        localAdapter = new LocalHotAdapter(getContext(), localSongs, cityName, generalClickListener);
        if (rvLocalHot != null) rvLocalHot.setAdapter(localAdapter);
    }

    private void showCitySelectionDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 40, 0, 40);
        layout.setBackgroundColor(Color.parseColor("#1A1A1A"));

        TextView title = new TextView(getContext());
        title.setText("Du lịch âm nhạc ✈️");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(50, 20, 50, 40);
        layout.addView(title);

        ListView listView = new ListView(getContext());
        String[] cities = {"📍 Vị trí hiện tại của tôi", "Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Đà Lạt", "New York", "Tokyo", "Seoul"};

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, cities) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.parseColor("#E0E0E0"));
                view.setTextSize(16);
                view.setPadding(50, 50, 50, 50);
                if (position == 0) view.setTextColor(Color.parseColor("#FF5252"));
                return view;
            }
        };

        listView.setAdapter(adapter);
        listView.setDivider(null);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    fetchCurrentLocation();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
                }
            } else {
                updateLocalHotUI(cities[position]);
            }
            dialog.dismiss();
        });

        layout.addView(listView);
        dialog.setContentView(layout);
        dialog.show();
    }

    private void showMoodSongs(Mood mood) {
        if (getContext() == null || rawSongs.isEmpty()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_mood_songs, null);
        dialog.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tv_mood_sheet_title);
        tvTitle.setText("Nhạc cho tâm trạng: " + mood.getTitle());

        LinearLayout container = sheetView.findViewById(R.id.layout_mood_song_container);
        container.removeAllViews();

        List<Song> filteredSongs = new ArrayList<>(rawSongs);
        Collections.shuffle(filteredSongs);
        int limit = Math.min(filteredSongs.size(), 15);

        ArrayList<Song> playlistToPlay = new ArrayList<>(filteredSongs.subList(0, limit));

        for (int i = 0; i < limit; i++) {
            Song s = playlistToPlay.get(i);
            View songView = LayoutInflater.from(getContext()).inflate(R.layout.item_song_mini, null);

            TextView tvSongTitle = songView.findViewById(R.id.tv_song_mini_title);
            TextView tvSongArtist = songView.findViewById(R.id.tv_song_mini_artist);
            ImageButton btnHeart = songView.findViewById(R.id.btn_song_mini_heart);
            ImageView imgSongMini = (ImageView) songView.findViewById(R.id.img_song_mini);
            tvSongTitle.setText(s.getTitle());
            tvSongArtist.setText(s.getArtist());
            GlideHelper.loadCenterCrop(this, s.getCoverArtPath(), imgSongMini);
            if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == s.getId()) {
                tvSongTitle.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            } else {
                tvSongTitle.setTextColor(android.graphics.Color.WHITE);
            }

            boolean isLiked = SongAdapter.globalLikedSongsCache.contains(s.getId());
            if (isLiked) {
                btnHeart.setImageResource(R.drawable.ic_heart_filled);
                btnHeart.setColorFilter(android.graphics.Color.parseColor("#FF5252"));
            } else {
                btnHeart.setImageResource(R.drawable.ic_heart_outline);
                btnHeart.setColorFilter(android.graphics.Color.parseColor("#E6FFFFFF"));
            }

            btnHeart.setOnClickListener(v -> toggleFavoriteBottomSheet(s, btnHeart));

            final int playIndex = i;

            songView.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(getContext(), MusicService.class);
                intent.setAction(MusicService.ACTION_PLAY);
                intent.putExtra("EXTRA_SONG_LIST", playlistToPlay);
                intent.putExtra("EXTRA_SONG_POSITION", playIndex);
                requireContext().startService(intent);
            });

            tvSongArtist.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(getContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("open_artist_detail", true);
                intent.putExtra("artist_name", s.getArtist());
                startActivity(intent);
            });

            container.addView(songView);
        }
        dialog.show();
    }

    private void showLocalHotSongsBottomSheet(String cityName) {
        if (getContext() == null || allLocalSongsPool == null || allLocalSongsPool.isEmpty()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_local_hot, null);
        dialog.setContentView(sheetView);

        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            bottomSheetInternal.getLayoutParams().height = (int) (screenHeight * 0.7);

            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }

        TextView tvTitle = sheetView.findViewById(R.id.tv_local_hot_sheet_title);
        tvTitle.setText("Top bài hát tại " + cityName);

        LinearLayout container = sheetView.findViewById(R.id.layout_local_hot_song_container);
        container.removeAllViews();

        List<Song> filteredSongs = new ArrayList<>(allLocalSongsPool);
        Collections.shuffle(filteredSongs);
        int limit = Math.min(filteredSongs.size(), 15);
        ArrayList<Song> playlistToPlay = new ArrayList<>(filteredSongs.subList(0, limit));

        for (int i = 0; i < limit; i++) {
            Song s = playlistToPlay.get(i);
            View songView = LayoutInflater.from(getContext()).inflate(R.layout.item_song_mini, null);

            TextView tvSongTitle = songView.findViewById(R.id.tv_song_mini_title);
            TextView tvSongArtist = songView.findViewById(R.id.tv_song_mini_artist);
            ImageButton btnHeart = songView.findViewById(R.id.btn_song_mini_heart);

            tvSongTitle.setText(s.getTitle());
            tvSongArtist.setText(s.getArtist());

            ImageView imgCover = songView.findViewById(R.id.img_song_mini);

            GlideHelper.loadCenterCropForAdapter(getContext(), s.getCoverArtPath(), imgCover);

            if (MusicService.globalCurrentSong != null && MusicService.globalCurrentSong.getId() == s.getId()) {
                tvSongTitle.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            } else {
                tvSongTitle.setTextColor(android.graphics.Color.WHITE);
            }

            boolean isLiked = SongAdapter.globalLikedSongsCache.contains(s.getId());
            if (isLiked) {
                btnHeart.setImageResource(R.drawable.ic_heart_filled);
                btnHeart.setColorFilter(android.graphics.Color.parseColor("#FF5252"));
            } else {
                btnHeart.setImageResource(R.drawable.ic_heart_outline);
                btnHeart.setColorFilter(android.graphics.Color.parseColor("#E6FFFFFF"));
            }

            btnHeart.setOnClickListener(v -> toggleFavoriteBottomSheet(s, btnHeart));

            final int playIndex = i;
            songView.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(getContext(), MusicService.class);
                intent.setAction(MusicService.ACTION_PLAY);
                intent.putExtra("EXTRA_SONG_LIST", playlistToPlay);
                intent.putExtra("EXTRA_SONG_POSITION", playIndex);
                requireContext().startService(intent);
            });

            tvSongArtist.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(getContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("open_artist_detail", true);
                intent.putExtra("artist_name", s.getArtist());
                startActivity(intent);
            });

            container.addView(songView);
        }

        dialog.show();
    }

    private void toggleFavoriteBottomSheet(Song song, ImageButton btnHeart) {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("isLoggedIn", false)) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để thả tim!", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = prefs.getString("userUsername", "");
        boolean isCurrentlyLiked = SongAdapter.globalLikedSongsCache.contains(song.getId());
        boolean newLikedState = !isCurrentlyLiked;

        if (newLikedState) {
            SongAdapter.globalLikedSongsCache.add(song.getId());
            btnHeart.setImageResource(R.drawable.ic_heart_filled);
            btnHeart.setColorFilter(android.graphics.Color.parseColor("#FF5252"));
            btnHeart.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                    .withEndAction(() -> btnHeart.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()).start();
        } else {
            SongAdapter.globalLikedSongsCache.remove(song.getId());
            btnHeart.setImageResource(R.drawable.ic_heart_outline);
            btnHeart.setColorFilter(android.graphics.Color.parseColor("#E6FFFFFF"));
        }

        btnHeart.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

        Intent favIntent = new Intent("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        favIntent.setPackage(requireContext().getPackageName());
        favIntent.putExtra("EXTRA_SONG", song);
        favIntent.putExtra("EXTRA_IS_LIKED", newLikedState);
        requireContext().sendBroadcast(favIntent);

        HashMap<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("song_id", song.getId());

        RetrofitClient.getApiService().toggleFavorite(body).enqueue(new Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<com.google.gson.JsonObject> call, @NonNull Response<com.google.gson.JsonObject> response) {}
            @Override
            public void onFailure(@NonNull Call<com.google.gson.JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private void fetchAllSongs(boolean isLoadMore) {
        if (isLoading || isLastPage) return;
        isLoading = true;

        Call<List<Song>> call = RetrofitClient.getApiService().getRecommendations(currentPage, PAGE_SIZE);

        if (!isLoadMore && layoutGlobalLoading != null) {
            layoutGlobalLoading.setVisibility(View.VISIBLE);
            if (rvAllSongs != null) rvAllSongs.setVisibility(View.GONE);
        } else if (isLoadMore && feedAdapter != null) {
            feedAdapter.showLoading();
        }

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (isLoadMore && feedAdapter != null) {
                    feedAdapter.hideLoading();
                }

                isLoading = false;

                if (result.isEmpty()) {
                    isLastPage = true;
                } else {
                    List<Song> strictlyNewSongs = new ArrayList<>();
                    List<Song> delayedSongs = new ArrayList<>();

                    for (Song newSong : result) {
                        boolean isDuplicate = false;
                        for (Song existingSong : rawSongs) {
                            if (existingSong.getId() == newSong.getId()) {
                                isDuplicate = true; break;
                            }
                        }
                        if (!isDuplicate) {
                            String artist = newSong.getArtist();
                            if (artist != null && recentArtistsWindow.contains(artist)) {
                                delayedSongs.add(newSong);
                            } else {
                                rawSongs.add(newSong);
                                strictlyNewSongs.add(newSong);
                                if (artist != null) {
                                    recentArtistsWindow.add(artist);
                                    if (recentArtistsWindow.size() > 5) recentArtistsWindow.remove(0);
                                }
                            }
                        }
                    }

                    for (Song delayed : delayedSongs) {
                        rawSongs.add(delayed);
                        strictlyNewSongs.add(delayed);
                        if (delayed.getArtist() != null) {
                            recentArtistsWindow.add(delayed.getArtist());
                            if (recentArtistsWindow.size() > 5) recentArtistsWindow.remove(0);
                        }
                    }

                    if (!isLoadMore) {
                        setupMockDataForNewFeatures();
                        filterFeed(currentFilterType, false);
                    } else {
                        if (currentFilterType == 0 || currentFilterType == 1) {
                            int startInsertIndex = mixedFeedList.size();
                            for (Song s : strictlyNewSongs) {
                                mixedFeedList.add(new FeedItem(s));
                            }
                            if (feedAdapter != null) {
                                feedAdapter.refreshPlayableSongs();
                                feedAdapter.notifyItemRangeInserted(startInsertIndex, strictlyNewSongs.size());
                            }
                        } else {
                            filterFeed(currentFilterType, false);
                        }
                    }

                    currentPage++;
                }

                if (!isLoadMore && layoutGlobalLoading != null) {
                    layoutGlobalLoading.setVisibility(View.GONE);
                    if (rvAllSongs != null) {
                        rvAllSongs.setVisibility(View.VISIBLE);
                        rvAllSongs.setAlpha(0f);
                        rvAllSongs.animate().alpha(1f).setDuration(300).start();
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (isLoadMore && feedAdapter != null) {
                    feedAdapter.hideLoading();
                }

                isLoading = false;

                if (!isLoadMore && layoutGlobalLoading != null) {
                    layoutGlobalLoading.setVisibility(View.GONE);
                    if (rvAllSongs != null) rvAllSongs.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void fetchFeaturedPlaylists() {
        Call<List<Playlist>> call = RetrofitClient.getApiService().getFeaturedPlaylists();
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> result) {
                rawPlaylists = result;
                if (playlistAdapter != null) {
                    List<Playlist> extendedPlaylists = new ArrayList<>(rawPlaylists);
                    extendedPlaylists.addAll(rawPlaylists);
                    playlistAdapter.updateData(extendedPlaylists);
                }
            }
            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void fetchChartRankings() {
        Call<List<Song>> call = RetrofitClient.getApiService().getRecommendations(1, 10);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                rawChartSongs = result;
                if (chartRankingAdapter != null) chartRankingAdapter.updateData(result);
            }
            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void fetchTopArtists() {
        Call<List<Song>> call = RetrofitClient.getApiService().getUniqueStoryArtists(10);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (artistCircleAdapter != null && result != null) {
                    artistCircleAdapter.updateData(result);
                }
            }
            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void fetchListeningHistory() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");

        TextView tvRecentTitle = getView() != null ? getView().findViewById(R.id.tv_recent_title) : null;
        RecyclerView rvRecentlyPlayed = getView() != null ? getView().findViewById(R.id.rv_recently_played) : null;

        if (username.isEmpty()) {
            if (tvRecentTitle != null) tvRecentTitle.setVisibility(View.GONE);
            if (rvRecentlyPlayed != null) rvRecentlyPlayed.setVisibility(View.GONE);
            return;
        }

        Call<List<Song>> call = RetrofitClient.getApiService().getListeningHistory(username);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null && !result.isEmpty() && recentlyPlayedAdapter != null) {
                    recentlyPlayedAdapter.updateData(result);
                    if (tvRecentTitle != null) tvRecentTitle.setVisibility(View.VISIBLE);
                    if (rvRecentlyPlayed != null) rvRecentlyPlayed.setVisibility(View.VISIBLE);
                } else {
                    if (tvRecentTitle != null) tvRecentTitle.setVisibility(View.GONE);
                    if (rvRecentlyPlayed != null) rvRecentlyPlayed.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (tvRecentTitle != null) tvRecentTitle.setVisibility(View.GONE);
                if (rvRecentlyPlayed != null) rvRecentlyPlayed.setVisibility(View.GONE);
            }
        });
    }

    private void setupChartSwipe() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.UP | ItemTouchHelper.DOWN) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) { return false; }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                chartRankingAdapter.notifyItemChanged(viewHolder.getAdapterPosition());
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvChartRanking);
    }

    private void setupScrollAnimation(View view) {
        AppBarLayout appBarLayout = view.findViewById(R.id.appbar_home);
        if (appBarLayout != null && layoutHeader != null) {
            appBarLayout.addOnOffsetChangedListener((appBar, verticalOffset) -> {
                float maxDistance = appBar.getTotalScrollRange() / 1.5f;
                if (maxDistance <= 0) maxDistance = 1;
                float alpha = 1f - (Math.abs(verticalOffset) / maxDistance);
                if (alpha < 0) alpha = 0f;
                if (alpha > 1) alpha = 1f;
                layoutHeader.setAlpha(alpha);
                layoutHeader.setTranslationY(verticalOffset * 0.5f);
            });
        }
    }

    private void setupPagination() {
        if (rvAllSongs != null && rvAllSongs.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) rvAllSongs.getLayoutManager();

            rvAllSongs.addOnScrollListener(new EndlessRecyclerViewScrollListener(layoutManager) {
                @Override
                public void onLoadMore() {
                    fetchAllSongs(true);
                }

                @Override
                public boolean isLoading() {
                    return isLoading;
                }

                @Override
                public boolean isLastPage() {
                    return isLastPage;
                }
            });
        }
    }

    private void setDynamicGreeting() {
        if (tvGreeting == null) return;
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
        if (timeOfDay >= 0 && timeOfDay < 12) tvGreeting.setText("Chào buổi sáng");
        else if (timeOfDay >= 12 && timeOfDay < 18) tvGreeting.setText("Chào buổi chiều");
        else tvGreeting.setText("Chào buổi tối");
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserAvatar();
        fetchListeningHistory();

        fetchBlendMixesForHome();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        filter.addAction(MusicService.ACTION_UPDATE_UI);
        ContextCompat.registerReceiver(requireContext(), updateUIReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        refreshAllAdapters();
    }

    @Override
    public void onPause() {
        super.onPause();
        try { requireContext().unregisterReceiver(updateUIReceiver); } catch (IllegalArgumentException e) { }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadUserAvatar();
            fetchListeningHistory();

            fetchBlendMixesForHome();

            refreshAllAdapters();
        }
    }

    @Override
    public void scrollToTop() {
        if (rvAllSongs != null) rvAllSongs.smoothScrollToPosition(0);
        View view = getView();
        if (view != null) {
            AppBarLayout appBarLayout = view.findViewById(R.id.appbar_home);
            if (appBarLayout != null) appBarLayout.setExpanded(true, true);
        }

        if (pillAll != null && currentFilterType != 0) {
            pillAll.performClick();
        }
    }

    private void setupFilterClicks() {
        if (pillAll == null) return;

        updatePillUI(pillAll);

        pillAll.setOnClickListener(v -> {
            updatePillUI(pillAll);
            filterFeed(0, true);
        });

        pillMusic.setOnClickListener(v -> {
            updatePillUI(pillMusic);
            filterFeed(1, true);
        });

        pillPlaylist.setOnClickListener(v -> {
            updatePillUI(pillPlaylist);
            filterFeed(2, true);
        });

        pillArtist.setOnClickListener(v -> {
            updatePillUI(pillArtist);
            filterFeed(3, true);
        });
    }

    private void filterFeed(int type, boolean isUserClick) {
        currentFilterType = type;
        if (rawSongs.isEmpty()) return;

        if (type == 0) {
            if (layoutDiscoverBlocks != null) layoutDiscoverBlocks.setVisibility(View.VISIBLE);
            if (tvFeedTitle != null) tvFeedTitle.setText("Dành riêng cho bạn");
        } else {
            if (layoutDiscoverBlocks != null) layoutDiscoverBlocks.setVisibility(View.GONE);
        }

        if (type == 2) {
            if (tvFeedTitle != null) tvFeedTitle.setText("Playlist nổi bật");
            if (isUserClick && rvAllSongs != null) {
                rvAllSongs.setLayoutManager(new GridLayoutManager(getContext(), 2));
                PlaylistAdapter gridAdapter = new PlaylistAdapter(getContext(), rawPlaylists);
                rvAllSongs.setAdapter(gridAdapter);
            }
        }
        else {
            if (isUserClick && rvAllSongs != null) {
                rvAllSongs.setLayoutManager(new LinearLayoutManager(getContext()));
                rvAllSongs.setAdapter(feedAdapter);
            }

            mixedFeedList.clear();

            if (type == 0) {
                int songCount = 0;
                boolean addedPlaylist = false;
                boolean addedChart = false;
                boolean addedArtist = false;

                for (int i = 0; i < rawSongs.size(); i++) {
                    Song song = rawSongs.get(i);
                    mixedFeedList.add(new FeedItem(song));
                    songCount++;

                    if (songCount == 5 && !addedPlaylist && !rawPlaylists.isEmpty()) {
                        mixedFeedList.add(new FeedItem(rawPlaylists, FeedItem.TYPE_PLAYLIST_CAROUSEL));
                        addedPlaylist = true;
                    }
                    else if (songCount == 10 && !addedChart && !rawChartSongs.isEmpty()) {
                        mixedFeedList.add(new FeedItem(FeedItem.TYPE_CHART_CAROUSEL, rawChartSongs));
                        addedChart = true;
                    }
                    else if (songCount == 15 && !addedArtist && song.getArtist() != null) {
                        mixedFeedList.add(new FeedItem(FeedItem.TYPE_ARTIST_SPOTLIGHT, song));
                        addedArtist = true;
                    }
                }
            }
            else if (type == 1) {
                if (tvFeedTitle != null) tvFeedTitle.setText("Khám phá âm nhạc");
                for (Song song : rawSongs) {
                    mixedFeedList.add(new FeedItem(song));
                }
            }
            else if (type == 3) {
                if (tvFeedTitle != null) tvFeedTitle.setText("Tiêu điểm nghệ sĩ");
                java.util.Set<String> uniqueArtists = new java.util.HashSet<>();
                for (Song song : rawSongs) {
                    String artistName = song.getArtist();
                    if (artistName != null && !uniqueArtists.contains(artistName)) {
                        uniqueArtists.add(artistName);
                        mixedFeedList.add(new FeedItem(FeedItem.TYPE_ARTIST_SPOTLIGHT, song));
                    }
                }
            }

            if (feedAdapter != null) feedAdapter.updateData(mixedFeedList);
        }

        if (isUserClick) {
            if (rvAllSongs != null) rvAllSongs.smoothScrollToPosition(0);
            AppBarLayout appBarLayout = getView().findViewById(R.id.appbar_home);
            if (appBarLayout != null) appBarLayout.setExpanded(true, true);
        }
    }

    private void updatePillUI(TextView selectedPill) {
        if (getContext() == null) return;
        TextView[] allPills = {pillAll, pillMusic, pillPlaylist, pillArtist};

        for (TextView pill : allPills) {
            pill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1AFFFFFF")));
            pill.setTextColor(android.graphics.Color.parseColor("#E0E0E0"));
        }

        int accentColor = androidx.core.content.ContextCompat.getColor(getContext(), R.color.color_accent);
        selectedPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        selectedPill.setTextColor(android.graphics.Color.WHITE);
    }

    private void loadUserAvatar() {
        if (getView() == null || getActivity() == null) return;
        ImageView imgHomeAvatar = getView().findViewById(R.id.img_home_avatar);
        if (imgHomeAvatar != null) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            String avatarUrl = prefs.getString("userAvatar", "");
            if (avatarUrl.isEmpty()) {
                imgHomeAvatar.setImageResource(R.drawable.default_avatar);
            } else {
                GlideHelper.loadAvatar(this, avatarUrl, imgHomeAvatar);
            }
        }
    }

    private void refreshAllAdapters() {
        if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
        if (recentlyPlayedAdapter != null) recentlyPlayedAdapter.notifyDataSetChanged();
        if (chartRankingAdapter != null) chartRankingAdapter.notifyDataSetChanged();
        if (storyAdapter != null) storyAdapter.notifyDataSetChanged();
        if (localAdapter != null) localAdapter.notifyDataSetChanged();
    }

    private void checkAndRequestNotificationPermission() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String myUsername = prefs.getString("userUsername", "");

        if (myUsername.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng đăng nhập để xem thông báo!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openNotificationSheet(myUsername);
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Toast.makeText(getContext(), "App cần quyền để báo cho bạn khi có thông báo mới!", Toast.LENGTH_LONG).show();
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            openNotificationSheet(myUsername);
        }
    }

    private void openNotificationSheet(String username) {
        NotificationBottomSheet bottomSheet = NotificationBottomSheet.newInstance(username);
        bottomSheet.show(getParentFragmentManager(), "NotificationBottomSheet");
    }
}
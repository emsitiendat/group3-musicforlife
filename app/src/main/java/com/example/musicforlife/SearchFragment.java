package com.example.musicforlife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;

public class SearchFragment extends Fragment implements ScrollToTopListener {

    private EditText etSearch;
    private ImageButton btnSearchAction;
    private ProgressBar progressBarSearch;
    private FrameLayout layoutResultsWrapper;
    private NestedScrollView layoutBrowseCategories;

    private NestedScrollView scrollSearchResults;
    private View layoutResultArtists, layoutResultPlaylists, layoutResultSongs;
    private RecyclerView rvResultArtists, rvResultPlaylists, rvResults;
    private TextView tvTitleResultSongs;

    private RecyclerView rvArtistStories;
    private RecyclerView rvMusicMoments;
    private RecyclerView rvCategories;

    private View layoutSearchFilters;
    private TextView pillAll, pillMusic, pillArtist, pillPlaylist;
    private ImageView imgSearchAvatar;
    private int currentFilterType = 0;

    private List<Song> rawSearchResults = new ArrayList<>();
    private List<Playlist> globalPlaylists = new ArrayList<>();

    private SongAdapter songAdapter;
    private ArtistCircleAdapter searchArtistAdapter;
    private PlaylistAdapter searchPlaylistAdapter;

    private SearchNewReleaseSquareAdapter newReleaseAdapter;
    private SearchMomentCinematicAdapter cinematicMomentAdapter;

    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private boolean isLoading = false;
    private boolean isLastPage = false;
    private int currentPage = 1;
    private final int PAGE_SIZE = 20;
    private String currentQuery = "";

    private final BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MusicService.ACTION_UPDATE_UI.equals(action)) {
                Song realSong = (Song) intent.getSerializableExtra("EXTRA_SONG");
                if (realSong != null) {
                    MusicService.globalCurrentSong = realSong;
                    if (songAdapter != null) songAdapter.notifyDataSetChanged();
                    if (newReleaseAdapter != null) newReleaseAdapter.notifyDataSetChanged();
                    if (cinematicMomentAdapter != null) cinematicMomentAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        etSearch = view.findViewById(R.id.et_search);
        btnSearchAction = view.findViewById(R.id.btn_search_action);
        progressBarSearch = view.findViewById(R.id.progress_bar_search);
        layoutResultsWrapper = view.findViewById(R.id.layout_results_wrapper);

        layoutBrowseCategories = view.findViewById(R.id.layout_browse_categories);

        imgSearchAvatar = view.findViewById(R.id.img_search_avatar);

        if (imgSearchAvatar != null) {
            imgSearchAvatar.setOnClickListener(v -> {
                if (getActivity() != null) {
                    View navAccount = requireActivity().findViewById(R.id.nav_account);
                    if (navAccount != null) {
                        navAccount.performClick();
                    }
                }
            });
        }

        loadUserAvatar();

        rvArtistStories = view.findViewById(R.id.rv_artist_stories);
        rvMusicMoments = view.findViewById(R.id.rv_music_moments);
        rvCategories = view.findViewById(R.id.rv_categories);

        layoutSearchFilters = view.findViewById(R.id.layout_search_filters);
        pillAll = view.findViewById(R.id.pill_all);
        pillMusic = view.findViewById(R.id.pill_music);
        pillArtist = view.findViewById(R.id.pill_artist);
        pillPlaylist = view.findViewById(R.id.pill_playlist);

        scrollSearchResults = view.findViewById(R.id.scroll_search_results);
        layoutResultArtists = view.findViewById(R.id.layout_result_artists);
        layoutResultPlaylists = view.findViewById(R.id.layout_result_playlists);
        layoutResultSongs = view.findViewById(R.id.layout_result_songs);
        rvResultArtists = view.findViewById(R.id.rv_result_artists);
        rvResultPlaylists = view.findViewById(R.id.rv_result_playlists);
        rvResults = view.findViewById(R.id.rv_search_results);
        tvTitleResultSongs = view.findViewById(R.id.tv_title_result_songs);

        songAdapter = new SongAdapter(getContext(), new ArrayList<>(), (songs, position) -> {
            Intent playIntent = new Intent(getContext(), MusicService.class);
            playIntent.setAction(MusicService.ACTION_PLAY);
            playIntent.putExtra("EXTRA_SONG_LIST", new ArrayList<>(songs));
            playIntent.putExtra("EXTRA_SONG_POSITION", position);
            requireContext().startService(playIntent);
        });
        songAdapter.setGridView(true);
        rvResults.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvResults.setAdapter(songAdapter);

        searchArtistAdapter = new ArtistCircleAdapter(getContext(), new ArrayList<>());
        rvResultArtists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvResultArtists.setAdapter(searchArtistAdapter);

        searchPlaylistAdapter = new PlaylistAdapter(getContext(), new ArrayList<>());
        rvResultPlaylists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvResultPlaylists.setAdapter(searchPlaylistAdapter);

        fetchGlobalPlaylists();
        setupPagination();
        setupFilterClicks();

        setupNewReleases();
        setupCinematicMoments();
        setupCategoryRecyclerView();
        setupTrendClicks(view);
        setupSearchLogic();

        return view;
    }

    private void setupFilterClicks() {
        if (pillAll == null) return;
        updatePillUI(pillAll);

        pillAll.setOnClickListener(v -> { updatePillUI(pillAll); filterSearchResults(0, true); });
        pillMusic.setOnClickListener(v -> { updatePillUI(pillMusic); filterSearchResults(1, true); });
        pillArtist.setOnClickListener(v -> { updatePillUI(pillArtist); filterSearchResults(2, true); });
        pillPlaylist.setOnClickListener(v -> { updatePillUI(pillPlaylist); filterSearchResults(3, true); });
    }

    private void updatePillUI(TextView selectedPill) {
        if (getContext() == null) return;
        TextView[] allPills = {pillAll, pillMusic, pillArtist, pillPlaylist};

        for (TextView pill : allPills) {
            pill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1AFFFFFF")));
            pill.setTextColor(Color.parseColor("#E0E0E0"));
            pill.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        selectedPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.color_accent)));
        selectedPill.setTextColor(Color.WHITE);
        selectedPill.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void filterSearchResults(int type, boolean isScrollToTop) {
        currentFilterType = type;
        if (getContext() == null || rawSearchResults == null) return;

        Set<String> seenArtists = new HashSet<>();
        List<Song> artistList = new ArrayList<>();
        for (Song song : rawSearchResults) {
            String artistName = song.getArtist();
            if (artistName != null && !seenArtists.contains(artistName)) {
                seenArtists.add(artistName);
                artistList.add(song);
            }
        }

        List<Playlist> filteredPlaylists = new ArrayList<>();
        String query = currentQuery.toLowerCase().trim();
        for (Playlist p : globalPlaylists) {
            if (p.getName().toLowerCase().contains(query)) {
                filteredPlaylists.add(p);
            }
        }

        searchArtistAdapter.updateData(artistList);
        searchPlaylistAdapter.updateData(filteredPlaylists);
        songAdapter.updateData(rawSearchResults);

        if (type == 0) {
            layoutResultArtists.setVisibility(artistList.isEmpty() ? View.GONE : View.VISIBLE);
            layoutResultPlaylists.setVisibility(filteredPlaylists.isEmpty() ? View.GONE : View.VISIBLE);
            layoutResultSongs.setVisibility(rawSearchResults.isEmpty() ? View.GONE : View.VISIBLE);
            tvTitleResultSongs.setVisibility(View.VISIBLE);

            rvResultArtists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            rvResultPlaylists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            rvResults.setLayoutManager(new GridLayoutManager(getContext(), 2));

        } else if (type == 1) {
            layoutResultArtists.setVisibility(View.GONE);
            layoutResultPlaylists.setVisibility(View.GONE);
            layoutResultSongs.setVisibility(rawSearchResults.isEmpty() ? View.GONE : View.VISIBLE);
            tvTitleResultSongs.setVisibility(View.GONE);
            rvResults.setLayoutManager(new GridLayoutManager(getContext(), 2));

        } else if (type == 2) {
            layoutResultArtists.setVisibility(artistList.isEmpty() ? View.GONE : View.VISIBLE);
            layoutResultPlaylists.setVisibility(View.GONE);
            layoutResultSongs.setVisibility(View.GONE);
            rvResultArtists.setLayoutManager(new GridLayoutManager(getContext(), 3));

        } else if (type == 3) {
            layoutResultArtists.setVisibility(View.GONE);
            layoutResultPlaylists.setVisibility(filteredPlaylists.isEmpty() ? View.GONE : View.VISIBLE);
            layoutResultSongs.setVisibility(View.GONE);
            rvResultPlaylists.setLayoutManager(new GridLayoutManager(getContext(), 2));
        }

        if (isScrollToTop && scrollSearchResults != null) {
            scrollSearchResults.post(() -> scrollSearchResults.smoothScrollTo(0, 0));
        }
    }

    private void setupSearchLogic() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().isEmpty()) {
                    btnSearchAction.setImageResource(R.drawable.ic_search);
                    btnSearchAction.setClickable(false);
                    layoutBrowseCategories.setVisibility(View.VISIBLE);
                    layoutResultsWrapper.setVisibility(View.GONE);
                    if (layoutSearchFilters != null) layoutSearchFilters.setVisibility(View.GONE);
                    if (progressBarSearch != null) progressBarSearch.setVisibility(View.GONE);
                } else {
                    btnSearchAction.setImageResource(R.drawable.ic_clear);
                    btnSearchAction.setClickable(true);
                    layoutBrowseCategories.setVisibility(View.GONE);
                    layoutResultsWrapper.setVisibility(View.VISIBLE);
                    if (layoutSearchFilters != null) layoutSearchFilters.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchHandler.removeCallbacks(searchRunnable);
                String query = s.toString().trim();

                if (!query.isEmpty()) {
                    currentQuery = query;

                    isLoading = true;
                    isLastPage = false;
                    currentPage = 1;

                    rawSearchResults.clear();
                    songAdapter.updateData(new ArrayList<>());

                    updatePillUI(pillAll);
                    currentFilterType = 0;

                    searchRunnable = () -> {
                        isLoading = false;
                        searchSongs(currentQuery, false);
                    };
                    searchHandler.postDelayed(searchRunnable, 500);
                }
            }
        });

        btnSearchAction.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
        });
    }

    private void setupPagination() {
        if (scrollSearchResults != null) {
            scrollSearchResults.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {

                if (isLoading || isLastPage) return;
                if (currentFilterType != 0 && currentFilterType != 1) return;

                if (scrollY > oldScrollY) {
                    if (v.getChildAt(0) != null) {
                        int bottomPosition = v.getChildAt(0).getMeasuredHeight() - v.getMeasuredHeight();

                        if (scrollY >= bottomPosition - 200) {
                            searchSongs(currentQuery, true);
                        }
                    }
                }
            });
        }
    }
    private void searchSongs(String query, boolean isLoadMore) {
        if (isLoading || isLastPage) return;
        isLoading = true;

        Call<List<Song>> call = RetrofitClient.getApiService().searchSongs(query, currentPage, PAGE_SIZE);

        ProgressBar mainProgressBar = isLoadMore ? null : progressBarSearch;
        RecyclerView mainView = isLoadMore ? null : rvResults;

        ApiHelper.request(call, mainProgressBar, mainView, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                isLoading = false;

                if (result == null || result.isEmpty()) {
                    isLastPage = true;
                    if (!isLoadMore) fetchRandomSongsFallback();
                } else {
                    if (!isLoadMore) {
                        rawSearchResults.clear();
                        rawSearchResults.addAll(result);
                        filterSearchResults(currentFilterType, true);
                    } else {
                        boolean hasNewData = false;
                        for (Song newSong : result) {
                            boolean isDuplicate = false;
                            for (Song existingSong : rawSearchResults) {
                                if (existingSong.getId() == newSong.getId()) {
                                    isDuplicate = true; break;
                                }
                            }
                            if (!isDuplicate) { hasNewData = true; break; }
                        }

                        if (!hasNewData) {
                            isLastPage = true; return;
                        }

                        int startInsertIndex = rawSearchResults.size();
                        rawSearchResults.addAll(result);

                        if(songAdapter != null) {
                            songAdapter.appendData(result);
                        }
                    }

                    if (result.size() < PAGE_SIZE) isLastPage = true;
                    else currentPage++;
                }
            }
            @Override
            public void onError(String errorMessage) {
                isLoading = false;
                if (!isLoadMore) fetchRandomSongsFallback();
            }
        });
    }
    private void fetchGlobalPlaylists() {
        Call<List<Playlist>> call = RetrofitClient.getApiService().getFeaturedPlaylists();
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> result) {
                if (result != null) globalPlaylists.addAll(result);
            }
            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void setupNewReleases() {
        if (rvArtistStories == null) return;
        rvArtistStories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        newReleaseAdapter = new SearchNewReleaseSquareAdapter(getContext(), new ArrayList<>());
        rvArtistStories.setAdapter(newReleaseAdapter);
        Call<List<Song>> call = RetrofitClient.getApiService().getRecommendations(1, 10);
        ApiHelper.request(call, null, rvArtistStories, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null) newReleaseAdapter.updateData(result);
            }
            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void setupCinematicMoments() {
        if (rvMusicMoments == null) return;
        rvMusicMoments.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        cinematicMomentAdapter = new SearchMomentCinematicAdapter(getContext(), new ArrayList<>());
        rvMusicMoments.setAdapter(cinematicMomentAdapter);
        Call<List<Song>> call = RetrofitClient.getApiService().getRecommendations(2, 8);
        ApiHelper.request(call, null, rvMusicMoments, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null) cinematicMomentAdapter.updateData(result);
            }
            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void setupCategoryRecyclerView() {
        if (rvCategories == null) return;
        List<Category> categoryList = new ArrayList<>();
        categoryList.add(new Category(1, "Pop Âu Mỹ", "#E13300", R.drawable.default_cover));
        categoryList.add(new Category(2, "Hip-Hop", "#148A08", R.drawable.default_cover));
        categoryList.add(new Category(3, "Nhạc Điện Tử", "#7358FF", R.drawable.default_cover));
        categoryList.add(new Category(4, "Acoustic", "#E8115B", R.drawable.default_cover));
        categoryList.add(new Category(5, "Rock", "#1E3264", R.drawable.default_cover));
        categoryList.add(new Category(6, "Indie", "#D84000", R.drawable.default_cover));
        rvCategories.setLayoutManager(new GridLayoutManager(getContext(), 2));
        CategoryAdapter adapter = new CategoryAdapter(categoryList, category -> performQuickSearch(category.getName()));
        rvCategories.setAdapter(adapter);
    }

    private void setupTrendClicks(View view) {
        View tag1 = view.findViewById(R.id.tag_trend_1);
        View tag2 = view.findViewById(R.id.tag_trend_2);
        View tag3 = view.findViewById(R.id.tag_trend_3);
        if (tag1 != null) tag1.setOnClickListener(v -> performQuickSearch("Trending"));
        if (tag2 != null) tag2.setOnClickListener(v -> performQuickSearch("Chill"));
        if (tag3 != null) tag3.setOnClickListener(v -> performQuickSearch("SoiDong"));
    }

    private void performQuickSearch(String keyword) {
        etSearch.setText(keyword);
        etSearch.setSelection(keyword.length());
    }

    private void fetchRandomSongsFallback() {
        Call<List<Song>> call = RetrofitClient.getApiService().getRecommendations(1, 30);
        ApiHelper.request(call, progressBarSearch, rvResults, new ApiHelper.CallbackResult<List<Song>>() {
            @Override
            public void onSuccess(List<Song> result) {
                if (result != null) {
                    Collections.shuffle(result);

                    rawSearchResults.clear();
                    rawSearchResults.addAll(result);

                    filterSearchResults(currentFilterType, true);
                }
            }
            @Override
            public void onError(String errorMessage) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserAvatar();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.musicforlife.ACTION_FAVORITE_CHANGED");
        filter.addAction(MusicService.ACTION_UPDATE_UI);
        ContextCompat.registerReceiver(requireContext(), updateUIReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (songAdapter != null) songAdapter.notifyDataSetChanged();
        if (newReleaseAdapter != null) newReleaseAdapter.notifyDataSetChanged();
        if (cinematicMomentAdapter != null) cinematicMomentAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        try { requireContext().unregisterReceiver(updateUIReceiver); } catch (IllegalArgumentException e) { }
    }

    @Override
    public void scrollToTop() {
        if (scrollSearchResults != null && layoutResultsWrapper.getVisibility() == View.VISIBLE) {
            scrollSearchResults.smoothScrollTo(0, 0);
        } else if (layoutBrowseCategories != null) {
            layoutBrowseCategories.smoothScrollTo(0, 0);
        }
        if (pillAll != null && currentFilterType != 0) pillAll.performClick();
    }

    private void loadUserAvatar() {
        if (imgSearchAvatar == null || getActivity() == null) return;

        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String avatarUrl = prefs.getString("userAvatar", "");

        if (avatarUrl.isEmpty()) {
            imgSearchAvatar.setImageResource(R.drawable.default_avatar);
        } else {
            GlideHelper.loadAvatar(this, avatarUrl, imgSearchAvatar);
        }
    }
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadUserAvatar();
        }
    }
}
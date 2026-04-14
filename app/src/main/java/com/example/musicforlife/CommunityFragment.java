package com.example.musicforlife;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

public class CommunityFragment extends Fragment implements ScrollToTopListener {
    private RecyclerView rvFeed;
    private CommunityAdapter feedAdapter;
    private List<CommunityItem> feedItems = new ArrayList<>();

    private RecyclerView rvStories;
    private StoryAdapter storyAdapter;
    private List<StoryUser> storyItems = new ArrayList<>();

    private ImageView imgCommunityAvatar;
    private LinearLayout layoutGlobalLoading;
    private boolean isFirstLoad = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_community, container, false);

        rvFeed = view.findViewById(R.id.rv_community_feed);
        rvStories = view.findViewById(R.id.rv_stories_feed);
        imgCommunityAvatar = view.findViewById(R.id.img_community_avatar);
        layoutGlobalLoading = view.findViewById(R.id.layout_global_loading);

        if (imgCommunityAvatar != null) {
            imgCommunityAvatar.setOnClickListener(v -> {
                if (getActivity() != null) {
                    View navAccount = requireActivity().findViewById(R.id.nav_account);
                    if (navAccount != null) {
                        navAccount.performClick();
                    }
                }
            });
        }

        loadUserAvatar();

        rvStories.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        storyAdapter = new StoryAdapter(getContext(), storyItems);
        rvStories.setAdapter(storyAdapter);

        rvFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        RecyclerView.ItemAnimator animator = rvFeed.getItemAnimator();
        if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        feedAdapter = new CommunityAdapter(getContext(), feedItems);
        rvFeed.setAdapter(feedAdapter);

        fetchStoriesFeed();
        fetchRealCommunityFeed();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserAvatar();
        fetchStoriesFeed();
        fetchRealCommunityFeed();
    }

    private void fetchStoriesFeed() {
        SharedPreferences prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUsername = prefs.getString("userUsername", "");

        if (currentUsername.isEmpty()) return;

        Call<List<StoryUser>> call = RetrofitClient.getApiService().getStoriesFeed(currentUsername);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<StoryUser>>() {
            @Override
            public void onSuccess(List<StoryUser> result) {
                if (result != null) {
                    storyItems.clear();
                    storyItems.addAll(result);
                    storyAdapter.notifyDataSetChanged();


                    if (storyItems.isEmpty()) {
                        rvStories.setVisibility(View.GONE);
                    } else {
                        rvStories.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
            }
        });
    }

    private void fetchRealCommunityFeed() {
        SharedPreferences prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String currentUsername = prefs.getString("userUsername", "");

        isFirstLoad = feedItems.isEmpty();

        if (isFirstLoad) {
            if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.VISIBLE);
            if (rvFeed != null) rvFeed.setVisibility(View.INVISIBLE);
        }

        Call<List<CommunityItem>> call = RetrofitClient.getApiService().getCommunityFeed(currentUsername);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<CommunityItem>>() {
            @Override
            public void onSuccess(List<CommunityItem> result) {
                if (result != null) {
                    feedItems.clear();
                    feedItems.addAll(result);
                    feedAdapter.notifyDataSetChanged();
                }

                if (isFirstLoad) {
                    isFirstLoad = false;
                    if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                    if (rvFeed != null) {
                        rvFeed.setVisibility(View.VISIBLE);
                        rvFeed.setAlpha(0f);
                        rvFeed.animate().alpha(1f).setDuration(300).start();
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (isFirstLoad) {
                    if (layoutGlobalLoading != null) layoutGlobalLoading.setVisibility(View.GONE);
                    if (rvFeed != null) {
                        rvFeed.setAlpha(1f);
                        rvFeed.setVisibility(View.VISIBLE);
                    }
                }
                if (getContext() != null) {
                    Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void scrollToTop() {
        if (rvFeed != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) rvFeed.getLayoutManager();
            if (layoutManager != null) {
                int firstCompletelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition();

                if (firstCompletelyVisible != 0) {
                    int firstVisible = layoutManager.findFirstVisibleItemPosition();

                    if (firstVisible > 15) {
                        rvFeed.scrollToPosition(0);
                    } else {
                        rvFeed.smoothScrollToPosition(0);
                    }
                }
            }
        }
    }

    private void loadUserAvatar() {
        if (imgCommunityAvatar == null || getActivity() == null) return;
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String avatarUrl = prefs.getString("userAvatar", "");
        if (avatarUrl.isEmpty()) {
            imgCommunityAvatar.setImageResource(R.drawable.default_avatar);
        } else {
            GlideHelper.loadAvatar(this, avatarUrl, imgCommunityAvatar);
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
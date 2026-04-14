package com.example.musicforlife;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.List;
import retrofit2.Call;

public class NotificationBottomSheet extends BottomSheetDialogFragment {

    private String username;
    private RecyclerView rvNotifs;
    private ProgressBar progressBar;
    private NotificationAdapter adapter;

    public static NotificationBottomSheet newInstance(String username) {
        NotificationBottomSheet fragment = new NotificationBottomSheet();
        Bundle args = new Bundle();
        args.putString("username", username);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            username = getArguments().getString("username");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_users, container, false);

        TextView tvTitle = view.findViewById(R.id.tv_bottom_sheet_title);
        rvNotifs = view.findViewById(R.id.rv_users);
        progressBar = view.findViewById(R.id.progress_bar_users);

        tvTitle.setText("Thông báo");
        rvNotifs.setLayoutManager(new LinearLayoutManager(getContext()));

        loadNotifications();

        return view;
    }

    private void loadNotifications() {
        progressBar.setVisibility(View.VISIBLE);
        Call<List<Notification>> call = RetrofitClient.getApiService().getNotificationHistory(username);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);
                    if (result != null && !result.isEmpty()) {
                        adapter = new NotificationAdapter(getContext(), result);
                        rvNotifs.setAdapter(adapter);
                    } else {
                        Toast.makeText(getContext(), "Bạn chưa có thông báo nào!", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Lỗi tải thông báo: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
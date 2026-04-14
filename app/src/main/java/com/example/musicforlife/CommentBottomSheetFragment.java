package com.example.musicforlife;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;

public class CommentBottomSheetFragment extends BottomSheetDialogFragment {
    private int songId;
    private String username;
    private RecyclerView rvComments;
    private TextView tvEmptyComments;
    private ProgressBar progressBarComments;
    private EditText edtInput;
    private ImageButton btnSend;
    private CommentAdapter adapter;
    private List<Comment> commentList = new ArrayList<>();

    public static CommentBottomSheetFragment newInstance(int songId, String username) {
        CommentBottomSheetFragment fragment = new CommentBottomSheetFragment();
        Bundle args = new Bundle();
        args.putInt("SONG_ID", songId);
        args.putString("USERNAME", username);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comment_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            songId = getArguments().getInt("SONG_ID");
            username = getArguments().getString("USERNAME");
        }

        rvComments = view.findViewById(R.id.rv_comments);
        tvEmptyComments = view.findViewById(R.id.tv_no_comments);
        progressBarComments = view.findViewById(R.id.progress_bar_comment);
        edtInput = view.findViewById(R.id.edt_comment_input);
        btnSend = view.findViewById(R.id.btn_send_comment);

        adapter = new CommentAdapter(commentList);
        rvComments.setLayoutManager(new LinearLayoutManager(getContext()));
        rvComments.setAdapter(adapter);

        loadComments();

        btnSend.setOnClickListener(v -> {
            String content = edtInput.getText().toString().trim();
            if (!content.isEmpty()) {
                handleSendCommentOptimistic(content);
            }
        });
    }

    private void handleSendCommentOptimistic(String content) {
        Comment tempComment = new Comment();
        tempComment.setText(content);
        tempComment.setOptimistic(true);

        Comment.UserInfo info = new Comment.UserInfo();
        info.name_display = "Bạn";
        tempComment.setUser(info);

        commentList.add(0, tempComment);
        adapter.notifyItemInserted(0);
        rvComments.scrollToPosition(0);
        edtInput.setText("");

        updateEmptyState();

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("song_id", songId);
        body.addProperty("text", content);

        Call<JsonObject> call = RetrofitClient.getApiService().addCommentV2(body);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                loadCommentsSilently();
            }

            @Override
            public void onError(String errorMessage) {
                commentList.remove(tempComment);
                adapter.notifyDataSetChanged();
                updateEmptyState();
                if (getContext() != null) Toast.makeText(getContext(), "Không thể gửi bình luận. Thử lại sau!", Toast.LENGTH_SHORT).show();
                edtInput.setText(content);
            }
        });
    }

    private void loadComments() {
        rvComments.setVisibility(View.GONE);
        tvEmptyComments.setVisibility(View.GONE);

        Call<List<Comment>> call = RetrofitClient.getApiService().getCommentsV2(songId);

        ApiHelper.request(call, progressBarComments, null, new ApiHelper.CallbackResult<List<Comment>>() {
            @Override
            public void onSuccess(List<Comment> result) {
                commentList.clear();
                if (result != null) {
                    commentList.addAll(result);
                }
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(String errorMessage) {
                updateEmptyState();
                if (getContext() != null) Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCommentsSilently() {
        Call<List<Comment>> call = RetrofitClient.getApiService().getCommentsV2(songId);

        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<List<Comment>>() {
            @Override
            public void onSuccess(List<Comment> result) {
                commentList.clear();
                if (result != null) {
                    commentList.addAll(result);
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                updateEmptyState();
            }

            @Override
            public void onError(String errorMessage) {
            }
        });
    }

    private void updateEmptyState() {
        if (commentList.isEmpty()) {
            rvComments.setVisibility(View.GONE);
            tvEmptyComments.setVisibility(View.VISIBLE);
        } else {
            rvComments.setVisibility(View.VISIBLE);
            tvEmptyComments.setVisibility(View.GONE);
        }
    }
}
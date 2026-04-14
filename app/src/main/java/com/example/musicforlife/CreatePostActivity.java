package com.example.musicforlife;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;

public class CreatePostActivity extends AppCompatActivity {
    private EditText edtCaption;
    private Button btnSubmit;
    private TextView tvSongTitle;

    private int currentSongId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        edtCaption = findViewById(R.id.edt_post_caption);
        btnSubmit = findViewById(R.id.btn_submit_post);
        tvSongTitle = findViewById(R.id.tv_post_song_title);

        currentSongId = getIntent().getIntExtra("SONG_ID", -1);
        String songTitle = getIntent().getStringExtra("SONG_TITLE");

        if (currentSongId == -1) {
            Toast.makeText(this, "Lỗi: Không tìm thấy bài hát", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvSongTitle.setText("Đang chia sẻ: " + songTitle);

        btnSubmit.setOnClickListener(v -> postToCommunity());
    }

    private void postToCommunity() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("userUsername", "");
        String caption = edtCaption.getText().toString().trim();

        if (username.isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để đăng bài!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Đang đăng...");

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("song_id", currentSongId);
        body.put("caption", caption);

        Call<SimpleResponse> call = RetrofitClient.getApiService().createCommunityPost(body);
        ApiHelper.request(call, null, null, new ApiHelper.CallbackResult<SimpleResponse>() {
            @Override
            public void onSuccess(SimpleResponse result) {
                if (result != null && result.isSuccess()) {
                    Toast.makeText(CreatePostActivity.this, "Đã đăng bài thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(CreatePostActivity.this, result.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Đăng bài");
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(CreatePostActivity.this, "Lỗi mạng: " + errorMessage, Toast.LENGTH_SHORT).show();
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Đăng bài");
            }
        });
    }
}
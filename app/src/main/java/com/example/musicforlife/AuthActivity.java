package com.example.musicforlife;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;

import java.util.HashMap;

import retrofit2.Call;

public class AuthActivity extends AppCompatActivity {

    private ImageView imgAuthBg;
    private LinearLayout layoutLoginForm, layoutRegisterForm;
    private TextView tvSwitchAction, tvSwitchPrompt;

    private TextInputLayout tilLoginUsername, tilLoginPassword;
    private TextInputLayout tilRegisterName, tilRegisterUsername, tilRegisterPassword;

    private LinearLayout layoutGlobalLoading;

    private boolean isLoginView = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("isLoggedIn", false)) {
            startActivity(new Intent(AuthActivity.this, MainActivity.class));
            finish();
            return;
        }

        initViews();
        setupAnimations();

        tvSwitchAction.setOnClickListener(v -> switchForm());

        findViewById(R.id.btn_auth_login).setOnClickListener(v -> handleLogin());
        findViewById(R.id.btn_auth_register).setOnClickListener(v -> handleRegister());
    }

    private void initViews() {
        imgAuthBg = findViewById(R.id.img_auth_bg);
        layoutLoginForm = findViewById(R.id.layout_login_form);
        layoutRegisterForm = findViewById(R.id.layout_register_form);

        tvSwitchAction = findViewById(R.id.tv_auth_switch_action);
        tvSwitchPrompt = findViewById(R.id.tv_auth_switch_prompt);

        tilLoginUsername = findViewById(R.id.til_login_username);
        tilLoginPassword = findViewById(R.id.til_login_password);
        tilRegisterName = findViewById(R.id.til_register_name);
        tilRegisterUsername = findViewById(R.id.til_register_username);
        tilRegisterPassword = findViewById(R.id.til_register_password);

        layoutGlobalLoading = findViewById(R.id.layout_global_loading);
    }

    private void setupAnimations() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(imgAuthBg, "translationX", 0f, -500f);
        animator.setDuration(30000);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    private void switchForm() {
        isLoginView = !isLoginView;
        clearErrors();

        if (isLoginView) {
            animateSwitch(layoutRegisterForm, layoutLoginForm, "Đăng ký ngay", "Chưa có tài khoản?");
        } else {
            animateSwitch(layoutLoginForm, layoutRegisterForm, "Đăng nhập", "Đã có tài khoản?");
        }
    }

    private void animateSwitch(View outView, View inView, String actionText, String promptText) {
        ObjectAnimator outAlpha = ObjectAnimator.ofFloat(outView, "alpha", 1f, 0f);
        ObjectAnimator outSlide = ObjectAnimator.ofFloat(outView, "translationY", 0f, dpToPx(50));

        ObjectAnimator inAlpha = ObjectAnimator.ofFloat(inView, "alpha", 0f, 1f);
        ObjectAnimator inSlide = ObjectAnimator.ofFloat(inView, "translationY", -dpToPx(50), 0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(outAlpha, outSlide);
        set.setDuration(300);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                outView.setVisibility(View.GONE);
                inView.setVisibility(View.VISIBLE);
                tvSwitchAction.setText(actionText);
                tvSwitchPrompt.setText(promptText);

                AnimatorSet setIn = new AnimatorSet();
                setIn.playTogether(inAlpha, inSlide);
                setIn.setDuration(400);
                setIn.start();
            }
        });
        set.start();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void handleLogin() {
        String username = tilLoginUsername.getEditText().getText().toString().trim();
        String password = tilLoginPassword.getEditText().getText().toString().trim();

        if (username.isEmpty()) { tilLoginUsername.setError("Nhập tên đăng nhập"); return; }
        if (password.isEmpty()) { tilLoginPassword.setError("Nhập mật khẩu"); return; }

        HashMap<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);

        Call<JsonObject> call = RetrofitClient.getApiService().login(body);

        ApiHelper.request(call, layoutGlobalLoading, null, new ApiHelper.CallbackResult<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                boolean success = result.has("success") && result.get("success").getAsBoolean();
                if (success) {
                    SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();

                    editor.putBoolean("isLoggedIn", true);
                    editor.putString("userUsername", username);

                    if (result.has("display_name") && !result.get("display_name").isJsonNull()) {
                        editor.putString("userName", result.get("display_name").getAsString());
                    } else if (result.has("user_name") && !result.get("user_name").isJsonNull()) {
                        editor.putString("userName", result.get("user_name").getAsString());
                    }

                    if (result.has("avatar_url") && !result.get("avatar_url").isJsonNull()) {
                        editor.putString("userAvatar", result.get("avatar_url").getAsString());
                    }

                    editor.apply();

                    Toast.makeText(AuthActivity.this, "Chào mừng quay trở lại!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(AuthActivity.this, MainActivity.class));
                    finish();
                } else {
                    String msg = result.has("message") ? result.get("message").getAsString() : "Lỗi đăng nhập";
                    tilLoginPassword.setError(msg);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AuthActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleRegister() {
        String name = tilRegisterName.getEditText().getText().toString().trim();
        String username = tilRegisterUsername.getEditText().getText().toString().trim();
        String password = tilRegisterPassword.getEditText().getText().toString().trim();

        if (name.isEmpty()) { tilRegisterName.setError("Nhập tên hiển thị"); return; }
        if (username.isEmpty()) { tilRegisterUsername.setError("Nhập tên đăng nhập"); return; }
        if (password.length() < 6) { tilRegisterPassword.setError("Mật khẩu ít nhất 6 ký tự"); return; }

        HashMap<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("username", username);
        body.put("password", password);

        Call<JsonObject> call = RetrofitClient.getApiService().register(body);

        ApiHelper.request(call, layoutGlobalLoading, null, new ApiHelper.CallbackResult<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                boolean success = result.has("success") && result.get("success").getAsBoolean();
                String msg = result.has("message") ? result.get("message").getAsString() : "Thao tác thành công";

                if (success) {
                    Toast.makeText(AuthActivity.this, msg, Toast.LENGTH_SHORT).show();
                    switchForm();
                    tilLoginUsername.getEditText().setText(username);
                    tilLoginPassword.getEditText().setText("");
                } else {
                    tilRegisterUsername.setError(msg);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (errorMessage.contains("409") || errorMessage.contains("400")) {
                    tilRegisterUsername.setError("Tên đăng nhập đã tồn tại");
                } else {
                    Toast.makeText(AuthActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void clearErrors() {
        tilLoginUsername.setError(null);
        tilLoginPassword.setError(null);
        tilRegisterName.setError(null);
        tilRegisterUsername.setError(null);
        tilRegisterPassword.setError(null);
    }
}
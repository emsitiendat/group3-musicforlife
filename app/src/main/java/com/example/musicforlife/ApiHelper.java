package com.example.musicforlife;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiHelper {

    public interface CallbackResult<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    /**
     * Hàm gọi API dùng chung bao gồm cả hiệu ứng Loading
     *
     * @param call          Lệnh gọi API từ RetrofitClient
     * @param loadingView   View vòng xoay (ProgressBar). Truyền null nếu không có.
     * @param contentToHide View nội dung cần ẩn đi khi đang tải. Truyền null nếu không cần ẩn.
     * @param callback      Hành động tiếp theo khi API thành công hoặc thất bại
     */
    public static <T> void request(Call<T> call, View loadingView, View contentToHide, CallbackResult<T> callback) {

        if (loadingView != null) loadingView.setVisibility(View.VISIBLE);
        if (contentToHide != null) contentToHide.setVisibility(View.GONE);

        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                if (loadingView != null) loadingView.setVisibility(View.GONE);
                if (contentToHide != null) contentToHide.setVisibility(View.VISIBLE);

                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Lỗi máy chủ: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                if (loadingView != null) loadingView.setVisibility(View.GONE);
                if (contentToHide != null) contentToHide.setVisibility(View.VISIBLE);

                callback.onError("Lỗi kết nối mạng: " + t.getMessage());
            }
        });
    }
}
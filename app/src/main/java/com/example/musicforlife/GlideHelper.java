package com.example.musicforlife;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

public class GlideHelper {

    public interface OnImageLoadListener {
        void onCompleted();
    }

    public enum ScaleType {
        CENTER_CROP,
        FIT_CENTER
    }
    private static boolean isValidContext(Context context) {
        if (context == null) return false;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isDestroyed() && !activity.isFinishing();
        }
        return true;
    }

    public static void loadCenterCropForAdapter(Context context, String url, ImageView imageView) {
        if (!isValidContext(context)) return;

        String normalized = Utils.normalizeUrl(url);

        if (normalized == null || normalized.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.default_cover);
            return;
        }


        Glide.with(context)
                .load(normalized)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(imageView.getDrawable())
                .error(R.drawable.default_cover)
                .centerCrop()
                .dontAnimate()
                .into(imageView);
    }

    public static void loadCircleForAdapter(Context context, String url, ImageView imageView) {
        if (!isValidContext(context)) return;

        String normalized = Utils.normalizeUrl(url);

        if (normalized == null || normalized.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.default_avatar);
            return;
        }

        Glide.with(context)
                .load(normalized)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(imageView.getDrawable())
                .error(R.drawable.default_avatar)
                .circleCrop()
                .dontAnimate()
                .into(imageView);
    }

    public static void loadRoundedForAdapter(Context context, String url, ImageView imageView, int radius) {
        if (!isValidContext(context)) return;

        String normalized = Utils.normalizeUrl(url);

        if (normalized == null || normalized.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.default_cover);
            return;
        }

        Glide.with(context)
                .load(normalized)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(imageView.getDrawable())
                .error(R.drawable.default_cover)
                .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                        new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radius))
                .dontAnimate()
                .into(imageView);
    }

    public static void loadRoundedWithSizeForAdapter(Context context, String url, ImageView imageView, int radius, int width, int height) {
        if (!isValidContext(context)) return;

        String normalized = Utils.normalizeUrl(url);

        if (normalized == null || normalized.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.default_cover);
            return;
        }

        Glide.with(context)
                .load(normalized)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(width, height)
                .placeholder(imageView.getDrawable())
                .error(R.drawable.default_cover)
                .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                        new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radius))
                .dontAnimate()
                .into(imageView);
    }


    public static void loadCenterCrop(Fragment fragment, String url, ImageView imageView, @Nullable OnImageLoadListener listener) {
        executeLoad(fragment, null, url, imageView, ScaleType.CENTER_CROP, 0, 0, listener);
    }

    public static void loadCenterCrop(Fragment fragment, String url, ImageView imageView) {
        executeLoad(fragment, null, url, imageView, ScaleType.CENTER_CROP, 0, 0, null);
    }

    public static void loadFitCenter(Fragment fragment, String url, ImageView imageView, @Nullable OnImageLoadListener listener) {
        executeLoad(fragment, null, url, imageView, ScaleType.FIT_CENTER, 0, 0, listener);
    }

    public static void loadWithSize(Fragment fragment, String url, ImageView imageView, int width, int height, ScaleType scaleType, @Nullable OnImageLoadListener listener) {
        executeLoad(fragment, null, url, imageView, scaleType, width, height, listener);
    }

    public static void loadCenterCrop(Activity activity, String url, ImageView imageView, @Nullable OnImageLoadListener listener) {
        executeLoad(null, activity, url, imageView, ScaleType.CENTER_CROP, 0, 0, listener);
    }

    public static void loadFitCenter(Activity activity, String url, ImageView imageView, @Nullable OnImageLoadListener listener) {
        executeLoad(null, activity, url, imageView, ScaleType.FIT_CENTER, 0, 0, listener);
    }

    public static void loadAvatar(Fragment fragment, String url, ImageView imageView, @Nullable OnImageLoadListener listener) {
        if (fragment == null || fragment.getContext() == null || !fragment.isAdded()) {
            if (listener != null) listener.onCompleted();
            return;
        }

        Glide.with(fragment).clear(imageView);

        Glide.with(fragment)
                .load(Utils.normalizeUrl(url))
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .circleCrop()
                .thumbnail(0.25f)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .listener(createListener(listener))
                .into(imageView);
    }

    public static void loadAvatar(Fragment fragment, String url, ImageView imageView) {
        loadAvatar(fragment, url, imageView, null);
    }

    private static void executeLoad(Fragment fragment, Activity activity, String url, ImageView imageView, ScaleType scaleType, int width, int height, @Nullable OnImageLoadListener listener) {
        RequestManager requestManager;

        if (fragment != null) {
            if (fragment.getContext() == null || !fragment.isAdded()) {
                if (listener != null) listener.onCompleted();
                return;
            }
            requestManager = Glide.with(fragment);
        } else if (activity != null) {
            if (activity.isDestroyed() || activity.isFinishing()) {
                if (listener != null) listener.onCompleted();
                return;
            }
            requestManager = Glide.with(activity);
        } else {
            if (listener != null) listener.onCompleted();
            return;
        }

        requestManager.clear(imageView);

        RequestBuilder<Drawable> request = requestManager
                .load(Utils.normalizeUrl(url))
                .placeholder(R.drawable.default_cover)
                .error(R.drawable.default_cover)
                .thumbnail(0.25f)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .listener(createListener(listener));

        if (scaleType == ScaleType.CENTER_CROP) {
            request = request.centerCrop();
        } else {
            request = request.fitCenter();
        }

        if (width > 0 && height > 0) {
            request = request.override(width, height);
        }

        request.into(imageView);
    }

    private static RequestListener<Drawable> createListener(OnImageLoadListener listener) {
        return new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                if (listener != null) listener.onCompleted();
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                if (listener != null) listener.onCompleted();
                return false;
            }
        };
    }

    public static void loadBitmap(Context context, String url, com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap> target) {
        if (context == null || target == null) return;
        Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(Utils.normalizeUrl(url))
                .into(target);
    }

    public static void clearTarget(Context context, com.bumptech.glide.request.target.CustomTarget<?> target) {
        if (context == null || target == null) return;
        Glide.with(context.getApplicationContext()).clear(target);
    }
    public static void loadBitmap(Activity activity, String url, com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap> target) {
        if (!isValidContext(activity) || target == null) return;

        Glide.with(activity)
                .asBitmap()
                .load(Utils.normalizeUrl(url))
                .dontAnimate()
                .into(target);
    }
}
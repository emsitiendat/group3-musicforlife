package com.example.musicforlife;

import android.content.Context;
import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

@GlideModule
public final class MyAppGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        int memoryCacheSizeBytes = 1024 * 1024 * 50;
        builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));

        int diskCacheSizeBytes = 1024 * 1024 * 250;
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, diskCacheSizeBytes));

        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .error(R.drawable.default_cover)
        );
    }
}
package com.example.musicforlife;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class DiscoverAdapter extends RecyclerView.Adapter<DiscoverAdapter.DiscoverViewHolder> {

    private Context context;
    private List<Song> songList;
    private DiscoverFragment parentFragment;
    private long lastClickTime = 0;


    public interface OnItemClickListener {
        void onItemClick(List<Song> songs, int position);
    }

    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public DiscoverAdapter(Context context, List<Song> songList, DiscoverFragment parentFragment) {
        this.context = context;
        this.songList = songList;
        this.parentFragment = parentFragment;
    }

    @NonNull
    @Override
    public DiscoverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_discover_card, parent, false);
        return new DiscoverViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DiscoverViewHolder holder, int position) {
        Song song = songList.get(position);
        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getArtist());

        GlideHelper.loadCenterCropForAdapter(context, song.getCoverArtPath(), holder.imgCover);

        if (holder.tvArtist != null) {
            holder.tvArtist.setOnClickListener(v -> {
                if (System.currentTimeMillis() - lastClickTime < 1000) return;
                lastClickTime = System.currentTimeMillis();

                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    int backStackCount = activity.getSupportFragmentManager().getBackStackEntryCount();
                    if (backStackCount > 0) {
                        String topFragmentName = activity.getSupportFragmentManager().getBackStackEntryAt(backStackCount - 1).getName();
                        if ("ArtistFragment".equals(topFragmentName)) return;
                    }
                    ArtistFragment artistFragment = ArtistFragment.newInstance(song.getArtist());
                    activity.navigateToDetailFragment(artistFragment, "ArtistFragment");
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(songList, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return songList == null ? 0 : songList.size();
    }

    public static class DiscoverViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTitle, tvArtist;

        public DiscoverViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.img_discover_cover);
            tvTitle = itemView.findViewById(R.id.tv_discover_title);
            tvArtist = itemView.findViewById(R.id.tv_discover_artist);
        }
    }
}
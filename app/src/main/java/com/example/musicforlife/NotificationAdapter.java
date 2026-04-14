package com.example.musicforlife;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private Context context;
    private List<Notification> list;

    public NotificationAdapter(Context context, List<Notification> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification item = list.get(position);

        holder.tvMessage.setText(item.getMessage());

        if ("follow".equals(item.getType())) {
            holder.imgIcon.setImageResource(R.drawable.ic_person);
        } else if ("blend_invite".equals(item.getType())) {
            holder.imgIcon.setImageResource(R.drawable.ic_add_friend);
        } else {
            holder.imgIcon.setImageResource(R.drawable.ic_bell);
        }
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView tvMessage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_item_user_avatar);
            tvMessage = itemView.findViewById(R.id.tv_item_user_name);
        }
    }
}
package io.tubetvlol.tubetv.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import io.tubetvlol.tubetv.R;
import io.tubetvlol.tubetv.models.Channel;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private List<Channel> channels;
    private OnChannelClickListener listener;
    private boolean showChannelNumbers = true;

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public ChannelAdapter(List<Channel> channels, OnChannelClickListener listener) {
        this.channels = channels;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_channel_card, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        Channel channel = channels.get(position);
        holder.bind(channel);
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    public void updateChannels(List<Channel> newChannels) {
        this.channels = newChannels;
        notifyDataSetChanged();
    }

    public void setShowChannelNumbers(boolean show) {
        this.showChannelNumbers = show;
        notifyDataSetChanged();
    }

    class ChannelViewHolder extends RecyclerView.ViewHolder {
        private ImageView channelLogo;
        private TextView channelNumber;
        private TextView channelName;
        private TextView channelDescription;

        public ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            channelLogo = itemView.findViewById(R.id.channel_logo);
            channelNumber = itemView.findViewById(R.id.channel_number);
            channelName = itemView.findViewById(R.id.channel_name);
            channelDescription = itemView.findViewById(R.id.channel_description);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onChannelClick(channels.get(position));
                    }
                }
            });
        }

        public void bind(Channel channel) {
            channelNumber.setText(channel.getNumber());
            channelNumber.setVisibility(showChannelNumbers ? View.VISIBLE : View.GONE);
            channelName.setText(channel.getName());
            channelDescription.setText(channel.getDescription());
            
            if (channel.getLogo() != null && !channel.getLogo().isEmpty()) {
                int logoResId = itemView.getContext().getResources()
                        .getIdentifier(channel.getLogo(), "drawable", itemView.getContext().getPackageName());
                if (logoResId != 0) {
                    channelLogo.setImageResource(logoResId);
                } else {
                    channelLogo.setImageResource(R.drawable.tubetv_logo);
                }
            } else {
                channelLogo.setImageResource(R.drawable.tubetv_logo);
            }
        }
    }
}
package org.tubetvproject.tubetv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private List<Channel> channels;
    private OnChannelClickListener listener;

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

    class ChannelViewHolder extends RecyclerView.ViewHolder {
        private TextView channelNumber;
        private TextView channelName;

        public ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            channelNumber = itemView.findViewById(R.id.channel_number);
            channelName = itemView.findViewById(R.id.channel_name);

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
            channelName.setText(channel.getName());
        }
    }
}

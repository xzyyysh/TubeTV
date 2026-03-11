package io.tubetvlol.tubetv.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import io.tubetvlol.tubetv.R;
import io.tubetvlol.tubetv.models.Channel;
import io.tubetvlol.tubetv.adapters.ChannelAdapter;

public class MainActivity extends Activity implements ChannelAdapter.OnChannelClickListener {

    private TextView currentTimeTextView;
    private TextView channelsCountTextView;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private RecyclerView channelsRecyclerView;
    private ChannelAdapter channelAdapter;
    private List<Channel> channelList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        initializeViews();
        setupFadeInAnimation();
        startTimeUpdater();
        loadChannels();
    }

    private void hideSystemUI() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void initializeViews() {
        currentTimeTextView = findViewById(R.id.current_time);
        channelsCountTextView = findViewById(R.id.channels_count);
        channelsRecyclerView = findViewById(R.id.channels_recycler_view);

        channelList = new ArrayList<>();
        channelAdapter = new ChannelAdapter(channelList, this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        channelsRecyclerView.setLayoutManager(layoutManager);
        channelsRecyclerView.setAdapter(channelAdapter);
    }

    private void setupFadeInAnimation() {
        View mainContainer = findViewById(R.id.main_container);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        mainContainer.setAlpha(1.0f);
        mainContainer.startAnimation(fadeIn);
    }

    private void startTimeUpdater() {
        timeHandler = new Handler();
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void updateTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
        String currentTime = timeFormat.format(new Date());
        currentTimeTextView.setText(currentTime);
    }

    private void loadChannels() {
        try {
            String jsonString = loadJSONFromAsset();
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray channelsArray = jsonObject.getJSONArray("channels");

            channelList.clear();
            for (int i = 0; i < channelsArray.length(); i++) {
                JSONObject channelObj = channelsArray.getJSONObject(i);

                boolean enabled = channelObj.getBoolean("enabled");
                if (enabled) {
                    Channel channel = new Channel(
                        channelObj.getInt("id"),
                        channelObj.getString("name"),
                        channelObj.getString("number"),
                        enabled,
                        channelObj.getString("description"),
                        channelObj.getString("streamUrl")
                    );
                    channelList.add(channel);
                }
            }

            channelAdapter.updateChannels(channelList);
            updateChannelsCount();

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading channels", Toast.LENGTH_SHORT).show();
        }
    }

    private String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getAssets().open("channels.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    private void updateChannelsCount() {
        int count = channelList.size();
        String countText = count + " canales";
        channelsCountTextView.setText(countText);
    }

    @Override
    public void onChannelClick(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", channel.getName());
        intent.putExtra("channel_number", channel.getNumber());
        intent.putExtra("stream_url", channel.getStreamUrl());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }
}
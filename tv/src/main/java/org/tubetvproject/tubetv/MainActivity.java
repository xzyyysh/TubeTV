package org.tubetvproject.tubetv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.animation.AlphaAnimation;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
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

public class MainActivity extends Activity implements ChannelAdapter.OnChannelClickListener {

    private TextView currentTimeTextView;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private RecyclerView channelsRecyclerView;
    private ChannelAdapter channelAdapter;
    private List<Channel> channelList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupFadeInAnimation();
        startTimeUpdater();
        loadChannels();
    }

    private void initializeViews() {
        currentTimeTextView = findViewById(R.id.current_time);
        channelsRecyclerView = findViewById(R.id.channels_recycler_view);

        channelList = new ArrayList<>();
        channelAdapter = new ChannelAdapter(channelList, this);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 4);
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
                        channelObj.getString("description")
                    );
                    channelList.add(channel);
                }
            }

            channelAdapter.updateChannels(channelList);

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

    @Override
    public void onChannelClick(Channel channel) {
        Toast.makeText(this, "Selected: " + channel.getName() + " Canal " + channel.getNumber(),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }
}

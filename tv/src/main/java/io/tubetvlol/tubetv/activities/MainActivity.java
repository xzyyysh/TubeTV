package io.tubetvlol.tubetv.activities;

import androidx.media3.common.util.UnstableApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
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
import io.tubetvlol.tubetv.utils.PreferencesManager;

@UnstableApi
public class MainActivity extends Activity implements ChannelAdapter.OnChannelClickListener {

    private static final String TAG = "MainActivity";
    private static final int FADE_IN_DURATION = 1000;
    private static final long TIME_UPDATE_INTERVAL = 60000;

    private TextView currentTimeTextView;
    private TextView channelsCountTextView;
    private TextView recentChannelsCountTextView;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private RecyclerView channelsRecyclerView;
    private RecyclerView recentChannelsRecyclerView;
    private LinearLayout recentChannelsSection;
    private ChannelAdapter channelAdapter;
    private ChannelAdapter recentChannelAdapter;
    private List<Channel> channelList;
    private List<Channel> recentChannelList;
    private ImageButton settingsButton;
    private PreferencesManager prefsManager;
    private FirebaseAnalytics analytics;
    private FirebaseCrashlytics crashlytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        analytics = FirebaseAnalytics.getInstance(this);
        crashlytics = FirebaseCrashlytics.getInstance();
        
        prefsManager = new PreferencesManager(this);
        initializeViews();
        setupFadeInAnimation();
        startTimeUpdater();
        loadChannels();
        
        logScreenView("home_screen");
    }

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void initializeViews() {
        currentTimeTextView = findViewById(R.id.current_time);
        channelsCountTextView = findViewById(R.id.channels_count);
        recentChannelsCountTextView = findViewById(R.id.recent_channels_count);
        channelsRecyclerView = findViewById(R.id.channels_recycler_view);
        recentChannelsRecyclerView = findViewById(R.id.recent_channels_recycler_view);
        recentChannelsSection = findViewById(R.id.recent_channels_section);
        settingsButton = findViewById(R.id.settings_button);

        channelList = new ArrayList<>();
        recentChannelList = new ArrayList<>();
        channelAdapter = new ChannelAdapter(channelList, this);
        recentChannelAdapter = new ChannelAdapter(recentChannelList, this);

        int savedColumns = prefsManager.getGridColumns();
        GridLayoutManager layoutManager = new GridLayoutManager(this, savedColumns);
        channelsRecyclerView.setLayoutManager(layoutManager);
        channelsRecyclerView.setAdapter(channelAdapter);

        GridLayoutManager recentLayoutManager = new GridLayoutManager(this, savedColumns);
        recentChannelsRecyclerView.setLayoutManager(recentLayoutManager);
        recentChannelsRecyclerView.setAdapter(recentChannelAdapter);

        channelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());
        recentChannelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());

        setupSettingsListeners();
    }

    private void setupFadeInAnimation() {
        View mainContainer = findViewById(R.id.main_container);
        if (mainContainer != null) {
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(FADE_IN_DURATION);
            mainContainer.setAlpha(1.0f);
            mainContainer.startAnimation(fadeIn);
        }
    }

    private void startTimeUpdater() {
        timeHandler = new Handler();
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                timeHandler.postDelayed(this, TIME_UPDATE_INTERVAL);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void updateTime() {
        if (currentTimeTextView != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            String currentTime = timeFormat.format(new Date());
            currentTimeTextView.setText(currentTime);
        }
    }

    private void loadChannels() {
        try {
            String jsonString = loadJSONFromAsset();
            if (jsonString == null) {
                Toast.makeText(this, "Error loading channels", Toast.LENGTH_SHORT).show();
                return;
            }
            
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray channelsArray = jsonObject.getJSONArray("channels");

            channelList.clear();
            for (int i = 0; i < channelsArray.length(); i++) {
                JSONObject channelObj = channelsArray.getJSONObject(i);

                boolean enabled = channelObj.optBoolean("enabled", false);
                if (enabled) {
                    String logo = channelObj.optString("logo", "");
                    Channel channel = new Channel(
                        channelObj.optInt("id", 0),
                        channelObj.optString("name", ""),
                        channelObj.optString("number", ""),
                        enabled,
                        channelObj.optString("description", ""),
                        channelObj.optString("streamUrl", ""),
                        logo
                    );
                    channelList.add(channel);
                }
            }

            channelAdapter.updateChannels(channelList);
            updateChannelsCount();
            loadRecentChannels();

        } catch (JSONException e) {
            crashlytics.recordException(e);
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
        if (channelsCountTextView != null) {
            int count = channelList.size();
            String countText = count + " canales";
            channelsCountTextView.setText(countText);
        }
    }

    private void loadRecentChannels() {
        if (prefsManager == null) return;
        
        prefsManager.cleanExpiredRecentChannels();
        List<Integer> recentIds = prefsManager.getRecentChannelIds();
        
        recentChannelList.clear();
        for (Integer id : recentIds) {
            for (Channel channel : channelList) {
                if (channel.getId() == id) {
                    recentChannelList.add(channel);
                    break;
                }
            }
        }
        
        if (recentChannelsSection != null) {
            if (recentChannelList.isEmpty()) {
                recentChannelsSection.setVisibility(View.GONE);
            } else {
                recentChannelsSection.setVisibility(View.VISIBLE);
                recentChannelAdapter.updateChannels(recentChannelList);
                updateRecentChannelsCount();
            }
        }
    }

    private void updateRecentChannelsCount() {
        if (recentChannelsCountTextView != null) {
            int count = recentChannelList.size();
            String countText = count + (count == 1 ? " canal" : " canales");
            recentChannelsCountTextView.setText(countText);
        }
    }

    private void setupSettingsListeners() {
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onChannelClick(Channel channel) {
        if (channel == null || prefsManager == null || analytics == null) return;
        
        prefsManager.addRecentChannel(channel.getId());
        
        Bundle params = new Bundle();
        params.putString("channel_id", String.valueOf(channel.getId()));
        params.putString("channel_name", channel.getName());
        params.putString("channel_number", channel.getNumber());
        analytics.logEvent("channel_selected", params);
        
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", channel.getName());
        intent.putExtra("channel_number", channel.getNumber());
        intent.putExtra("channel_logo", channel.getLogo());
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
        loadRecentChannels();
        
        // reload settings in case they changed
        int savedColumns = prefsManager.getGridColumns();
        GridLayoutManager layoutManager = new GridLayoutManager(this, savedColumns);
        channelsRecyclerView.setLayoutManager(layoutManager);
        GridLayoutManager recentLayoutManager = new GridLayoutManager(this, savedColumns);
        recentChannelsRecyclerView.setLayoutManager(recentLayoutManager);
        
        channelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());
        recentChannelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());
    }

    private void logScreenView(String screenName) {
        if (analytics == null || screenName == null) return;
        
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName);
        params.putString(FirebaseAnalytics.Param.SCREEN_CLASS, getClass().getSimpleName());
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params);
    }

    private void logEvent(String eventName, String paramKey, String paramValue) {
        if (analytics == null || eventName == null) return;
        
        Bundle params = new Bundle();
        if (paramKey != null && paramValue != null) {
            params.putString(paramKey, paramValue);
        }
        analytics.logEvent(eventName, params);
    }
}
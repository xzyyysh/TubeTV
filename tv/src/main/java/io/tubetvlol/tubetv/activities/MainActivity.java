package io.tubetvlol.tubetv.activities;

import androidx.media3.common.util.UnstableApi;
import androidx.appcompat.content.res.AppCompatResources;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.widget.NestedScrollView;
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
    private static final int PAGE_FADE_DURATION = 200;
    private static final long TIME_UPDATE_INTERVAL = 1000;

    private TextView currentTimeTextView;
    private TextView channelsCountTextView;
    private TextView recentChannelsCountTextView;
    private TextView recentsEmptyText;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private RecyclerView channelsRecyclerView;
    private RecyclerView recentChannelsRecyclerView;
    private NestedScrollView pageHome;
    private NestedScrollView pageRecents;
    private ImageButton sidebarHome;
    private ImageButton sidebarRecents;
    private ImageButton settingsButton;
    private ChannelAdapter channelAdapter;
    private ChannelAdapter recentChannelAdapter;
    private List<Channel> channelList;
    private List<Channel> recentChannelList;
    private PreferencesManager prefsManager;
    private FirebaseAnalytics analytics;
    private FirebaseCrashlytics crashlytics;
    private boolean onHomePage = true;

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
        recentsEmptyText = findViewById(R.id.recents_empty_text);
        channelsRecyclerView = findViewById(R.id.channels_recycler_view);
        recentChannelsRecyclerView = findViewById(R.id.recent_channels_recycler_view);
        pageHome = findViewById(R.id.page_home);
        pageRecents = findViewById(R.id.page_recents);
        sidebarHome = findViewById(R.id.sidebar_home);
        sidebarRecents = findViewById(R.id.sidebar_recents);
        settingsButton = findViewById(R.id.settings_button);

        channelList = new ArrayList<>();
        recentChannelList = new ArrayList<>();
        channelAdapter = new ChannelAdapter(channelList, this);
        recentChannelAdapter = new ChannelAdapter(recentChannelList, this);

        int savedColumns = prefsManager.getGridColumns();
        channelsRecyclerView.setLayoutManager(new GridLayoutManager(this, savedColumns));
        channelsRecyclerView.setAdapter(channelAdapter);

        recentChannelsRecyclerView.setLayoutManager(new GridLayoutManager(this, savedColumns));
        recentChannelsRecyclerView.setAdapter(recentChannelAdapter);

        channelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());
        recentChannelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());

        sidebarHome.setOnClickListener(v -> navigateToPage(true));
        sidebarRecents.setOnClickListener(v -> navigateToPage(false));

        settingsButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
    }

    private void navigateToPage(boolean goHome) {
        if (onHomePage == goHome) return;

        final View pageOut = goHome ? pageRecents : pageHome;
        final View pageIn = goHome ? pageHome : pageRecents;

        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(PAGE_FADE_DURATION);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                pageOut.setVisibility(View.GONE);
                pageIn.setAlpha(0f);
                pageIn.setVisibility(View.VISIBLE);
                AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
                fadeIn.setDuration(PAGE_FADE_DURATION);
                pageIn.startAnimation(fadeIn);
                pageIn.setAlpha(1f);
            }
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
        });
        pageOut.startAnimation(fadeOut);

        onHomePage = goHome;
        sidebarHome.setBackground(goHome
                ? AppCompatResources.getDrawable(this, R.drawable.sidebar_item_selected)
                : null);
        sidebarRecents.setBackground(goHome
                ? null
                : AppCompatResources.getDrawable(this, R.drawable.sidebar_item_selected));

        if (!goHome) {
            loadRecentChannels();
            logScreenView("recents_screen");
        } else {
            logScreenView("home_screen");
        }
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
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
            currentTimeTextView.setText(timeFormat.format(new Date()));
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
                    channelList.add(new Channel(
                        channelObj.optInt("id", 0),
                        channelObj.optString("name", ""),
                        channelObj.optString("number", ""),
                        enabled,
                        channelObj.optString("description", ""),
                        channelObj.optString("streamUrl", ""),
                        channelObj.optString("logo", "")
                    ));
                }
            }

            channelAdapter.updateChannels(channelList);
            updateChannelsCount();

        } catch (JSONException e) {
            crashlytics.recordException(e);
            Toast.makeText(this, "Error loading channels", Toast.LENGTH_SHORT).show();
        }
    }

    private String loadJSONFromAsset() {
        try {
            InputStream is = getAssets().open("channels.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void updateChannelsCount() {
        if (channelsCountTextView != null) {
            channelsCountTextView.setText(getResources().getQuantityString(R.plurals.channels_count, channelList.size(), channelList.size()));
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

        recentChannelAdapter.updateChannels(recentChannelList);

        if (recentChannelList.isEmpty()) {
            recentChannelsRecyclerView.setVisibility(View.GONE);
            recentsEmptyText.setVisibility(View.VISIBLE);
            if (recentChannelsCountTextView != null) recentChannelsCountTextView.setText("");
        } else {
            recentChannelsRecyclerView.setVisibility(View.VISIBLE);
            recentsEmptyText.setVisibility(View.GONE);
            if (recentChannelsCountTextView != null) {
                int count = recentChannelList.size();
                recentChannelsCountTextView.setText(getResources().getQuantityString(R.plurals.recent_channels_count, count, count));
            }
        }
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
    protected void onResume() {
        super.onResume();
        hideSystemUI();

        int savedColumns = prefsManager.getGridColumns();
        channelsRecyclerView.setLayoutManager(new GridLayoutManager(this, savedColumns));
        recentChannelsRecyclerView.setLayoutManager(new GridLayoutManager(this, savedColumns));

        channelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());
        recentChannelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());

        if (!onHomePage) loadRecentChannels();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }

    private void logScreenView(String screenName) {
        if (analytics == null || screenName == null) return;
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName);
        params.putString(FirebaseAnalytics.Param.SCREEN_CLASS, getClass().getSimpleName());
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params);
    }
}

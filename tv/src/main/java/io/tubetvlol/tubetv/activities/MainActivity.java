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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Switch;
import android.widget.FrameLayout;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
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
    private static final int OVERLAY_FADE_DURATION = 200;
    private static final int DIALOG_FADE_DURATION = 300;
    private static final int DIALOG_FADE_DELAY = 100;
    private static final int OVERLAY_FADE_DELAY = 100;
    private static final int DIALOG_FADE_OUT_DURATION = 250;
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
    private ImageButton backButton;
    private LinearLayout contentArea;
    private FrameLayout settingsOverlay;
    private LinearLayout settingsDialog;
    private boolean isSettingsVisible = false;
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
        backButton = findViewById(R.id.back_button);
        contentArea = findViewById(R.id.content_area);
        settingsOverlay = findViewById(R.id.settings_overlay);
        settingsDialog = findViewById(R.id.settings_dialog);

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
        settingsButton.setOnClickListener(v -> showSettings());
        backButton.setOnClickListener(v -> hideSettings());
        settingsOverlay.setOnClickListener(v -> hideSettings());
        settingsDialog.setOnClickListener(v -> {});
        settingsDialog.setClickable(true);
        initializeSettingsControls();
    }

    private void initializeSettingsControls() {
        Spinner controlsTimeoutSpinner = findViewById(R.id.controls_timeout_spinner);
        Spinner gridColumnsSpinner = findViewById(R.id.grid_columns_spinner);
        Switch showChannelNumbersSwitch = findViewById(R.id.show_channel_numbers_switch);
        Switch keepScreenOnSwitch = findViewById(R.id.keep_screen_on_switch);

        ArrayAdapter<CharSequence> timeoutAdapter = ArrayAdapter.createFromResource(this,
                R.array.controls_timeout_options, android.R.layout.simple_spinner_item);
        timeoutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        controlsTimeoutSpinner.setAdapter(timeoutAdapter);

        ArrayAdapter<CharSequence> columnsAdapter = ArrayAdapter.createFromResource(this,
                R.array.grid_columns_options, android.R.layout.simple_spinner_item);
        columnsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridColumnsSpinner.setAdapter(columnsAdapter);

        loadSettingsValues(controlsTimeoutSpinner, gridColumnsSpinner, showChannelNumbersSwitch, keepScreenOnSwitch);

        controlsTimeoutSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String[] values = getResources().getStringArray(R.array.controls_timeout_values);
                int timeout = Integer.parseInt(values[position]);
                prefsManager.setControlsTimeout(timeout);
                logEvent("setting_changed", "controls_timeout", String.valueOf(timeout));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        gridColumnsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int columns = position + 2;
                prefsManager.setGridColumns(columns);
                GridLayoutManager layoutManager = new GridLayoutManager(MainActivity.this, columns);
                channelsRecyclerView.setLayoutManager(layoutManager);
                GridLayoutManager recentLayoutManager = new GridLayoutManager(MainActivity.this, columns);
                recentChannelsRecyclerView.setLayoutManager(recentLayoutManager);
                logEvent("setting_changed", "grid_columns", String.valueOf(columns));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        showChannelNumbersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setShowChannelNumbers(isChecked);
            channelAdapter.setShowChannelNumbers(isChecked);
            recentChannelAdapter.setShowChannelNumbers(isChecked);
            logEvent("setting_changed", "show_channel_numbers", String.valueOf(isChecked));
        });

        keepScreenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setKeepScreenOn(isChecked);
            logEvent("setting_changed", "keep_screen_on", String.valueOf(isChecked));
        });
    }

    private void loadSettingsValues(Spinner timeoutSpinner, Spinner columnsSpinner, 
                                   Switch numbersSwitch, Switch screenSwitch) {
        int savedTimeout = prefsManager.getControlsTimeout();
        String[] timeoutValues = getResources().getStringArray(R.array.controls_timeout_values);
        for (int i = 0; i < timeoutValues.length; i++) {
            if (Integer.parseInt(timeoutValues[i]) == savedTimeout) {
                timeoutSpinner.setSelection(i);
                break;
            }
        }

        int savedColumns = prefsManager.getGridColumns();
        columnsSpinner.setSelection(savedColumns - 2);

        numbersSwitch.setChecked(prefsManager.getShowChannelNumbers());
        screenSwitch.setChecked(prefsManager.getKeepScreenOn());
    }

    private void showSettings() {
        if (!isSettingsVisible && settingsOverlay != null && settingsDialog != null) {
            settingsOverlay.setVisibility(View.VISIBLE);
            settingsOverlay.setFocusable(true);
            settingsOverlay.setFocusableInTouchMode(true);
            settingsOverlay.requestFocus();
            
            if (contentArea != null) {
                contentArea.setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
                contentArea.setEnabled(false);
            }
            
            if (settingsButton != null) {
                settingsButton.setFocusable(false);
            }
            
            settingsDialog.setDescendantFocusability(LinearLayout.FOCUS_AFTER_DESCENDANTS);
            
            AlphaAnimation overlayFadeIn = new AlphaAnimation(0.0f, 1.0f);
            overlayFadeIn.setDuration(OVERLAY_FADE_DURATION);
            
            AlphaAnimation dialogFadeIn = new AlphaAnimation(0.0f, 1.0f);
            dialogFadeIn.setDuration(DIALOG_FADE_DURATION);
            dialogFadeIn.setStartOffset(DIALOG_FADE_DELAY);
            
            settingsOverlay.startAnimation(overlayFadeIn);
            settingsDialog.startAnimation(dialogFadeIn);
            
            dialogFadeIn.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (backButton != null) {
                        backButton.requestFocus();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            isSettingsVisible = true;
            logEvent("settings_opened", null, null);
        }
    }

    private void hideSettings() {
        if (isSettingsVisible && settingsOverlay != null && settingsDialog != null) {
            AlphaAnimation overlayFadeOut = new AlphaAnimation(1.0f, 0.0f);
            overlayFadeOut.setDuration(OVERLAY_FADE_DURATION);
            overlayFadeOut.setStartOffset(OVERLAY_FADE_DELAY);
            
            AlphaAnimation dialogFadeOut = new AlphaAnimation(1.0f, 0.0f);
            dialogFadeOut.setDuration(DIALOG_FADE_OUT_DURATION);
            
            overlayFadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    settingsOverlay.setVisibility(View.GONE);
                    settingsOverlay.setFocusable(false);
                    settingsOverlay.setFocusableInTouchMode(false);
                    
                    if (contentArea != null) {
                        contentArea.setDescendantFocusability(LinearLayout.FOCUS_BEFORE_DESCENDANTS);
                        contentArea.setEnabled(true);
                    }
                    
                    if (settingsButton != null) {
                        settingsButton.setFocusable(true);
                        settingsButton.requestFocus();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            settingsOverlay.startAnimation(overlayFadeOut);
            settingsDialog.startAnimation(dialogFadeOut);
            
            isSettingsVisible = false;
        }
    }

    @Override
    public void onBackPressed() {
        if (isSettingsVisible) {
            hideSettings();
        } else {
            super.onBackPressed();
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
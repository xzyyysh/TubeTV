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

    private TextView currentTimeTextView;
    private TextView channelsCountTextView;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private RecyclerView channelsRecyclerView;
    private ChannelAdapter channelAdapter;
    private List<Channel> channelList;
    private ImageButton settingsButton;
    private ImageButton backButton;
    private LinearLayout contentArea;
    private FrameLayout settingsOverlay;
    private LinearLayout settingsDialog;
    private boolean isSettingsVisible = false;
    private PreferencesManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        prefsManager = new PreferencesManager(this);
        initializeViews();
        setupFadeInAnimation();
        startTimeUpdater();
        loadChannels();
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
        channelsRecyclerView = findViewById(R.id.channels_recycler_view);
        settingsButton = findViewById(R.id.settings_button);
        backButton = findViewById(R.id.back_button);
        contentArea = findViewById(R.id.content_area);
        settingsOverlay = findViewById(R.id.settings_overlay);
        settingsDialog = findViewById(R.id.settings_dialog);

        channelList = new ArrayList<>();
        channelAdapter = new ChannelAdapter(channelList, this);

        int savedColumns = prefsManager.getGridColumns();
        GridLayoutManager layoutManager = new GridLayoutManager(this, savedColumns);
        channelsRecyclerView.setLayoutManager(layoutManager);
        channelsRecyclerView.setAdapter(channelAdapter);

        channelAdapter.setShowChannelNumbers(prefsManager.getShowChannelNumbers());

        setupSettingsListeners();
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
                timeHandler.postDelayed(this, 60000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void updateTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
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
                    String logo = channelObj.optString("logo", "");
                    Channel channel = new Channel(
                        channelObj.getInt("id"),
                        channelObj.getString("name"),
                        channelObj.getString("number"),
                        enabled,
                        channelObj.getString("description"),
                        channelObj.getString("streamUrl"),
                        logo
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

    private void setupSettingsListeners() {
        settingsButton.setOnClickListener(v -> showSettings());
        backButton.setOnClickListener(v -> hideSettings());
        settingsOverlay.setOnClickListener(v -> {
            if (v == settingsOverlay) {
                hideSettings();
            }
        });
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
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        showChannelNumbersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setShowChannelNumbers(isChecked);
            channelAdapter.setShowChannelNumbers(isChecked);
        });

        keepScreenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setKeepScreenOn(isChecked);
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
        if (!isSettingsVisible) {
            settingsOverlay.setVisibility(View.VISIBLE);
            
            AlphaAnimation overlayFadeIn = new AlphaAnimation(0.0f, 1.0f);
            overlayFadeIn.setDuration(200);
            
            AlphaAnimation dialogFadeIn = new AlphaAnimation(0.0f, 1.0f);
            dialogFadeIn.setDuration(300);
            dialogFadeIn.setStartOffset(100);
            
            settingsOverlay.startAnimation(overlayFadeIn);
            settingsDialog.startAnimation(dialogFadeIn);
            
            isSettingsVisible = true;
        }
    }

    private void hideSettings() {
        if (isSettingsVisible) {
            AlphaAnimation overlayFadeOut = new AlphaAnimation(1.0f, 0.0f);
            overlayFadeOut.setDuration(200);
            overlayFadeOut.setStartOffset(100);
            
            AlphaAnimation dialogFadeOut = new AlphaAnimation(1.0f, 0.0f);
            dialogFadeOut.setDuration(250);
            
            overlayFadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    settingsOverlay.setVisibility(View.GONE);
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
    }
}
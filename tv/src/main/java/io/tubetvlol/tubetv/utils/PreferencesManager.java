package io.tubetvlol.tubetv.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {
    private static final String PREFS_NAME = "TubeTVPrefs";
    private static final String KEY_CONTROLS_TIMEOUT = "controls_timeout";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String KEY_SHOW_CHANNEL_NUMBERS = "show_channel_numbers";
    private static final String KEY_GRID_COLUMNS = "grid_columns";

    private SharedPreferences prefs;

    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getControlsTimeout() {
        return prefs.getInt(KEY_CONTROLS_TIMEOUT, 3000);
    }

    public void setControlsTimeout(int timeout) {
        prefs.edit().putInt(KEY_CONTROLS_TIMEOUT, timeout).apply();
    }

    public boolean getKeepScreenOn() {
        return prefs.getBoolean(KEY_KEEP_SCREEN_ON, true);
    }

    public void setKeepScreenOn(boolean keepOn) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply();
    }

    public boolean getShowChannelNumbers() {
        return prefs.getBoolean(KEY_SHOW_CHANNEL_NUMBERS, true);
    }

    public void setShowChannelNumbers(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_CHANNEL_NUMBERS, show).apply();
    }

    public int getGridColumns() {
        return prefs.getInt(KEY_GRID_COLUMNS, 2);
    }

    public void setGridColumns(int columns) {
        prefs.edit().putInt(KEY_GRID_COLUMNS, columns).apply();
    }
}
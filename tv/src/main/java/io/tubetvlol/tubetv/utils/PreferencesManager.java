package io.tubetvlol.tubetv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PreferencesManager {
    private static final String TAG = "PreferencesManager";
    private static final String PREFS_NAME = "TubeTVPrefs";
    private static final String KEY_CONTROLS_TIMEOUT = "controls_timeout";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String KEY_SHOW_CHANNEL_NUMBERS = "show_channel_numbers";
    private static final String KEY_GRID_COLUMNS = "grid_columns";
    private static final String KEY_RECENT_CHANNELS = "recent_channels";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final long RECENT_CHANNEL_DURATION = 60 * 60 * 1000;
    private static final long UPDATE_COOLDOWN_DURATION = 24 * 60 * 60 * 1000;

    private final SharedPreferences prefs;

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

    public long getLastUpdateCheckTime() {
        return prefs.getLong(KEY_LAST_UPDATE_CHECK, 0);
    }

    public void setLastUpdateCheckTime(long time) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, time).apply();
    }

    public boolean shouldCheckForUpdate() {
        long lastCheck = getLastUpdateCheckTime();
        long now = System.currentTimeMillis();
        return (now - lastCheck) >= UPDATE_COOLDOWN_DURATION;
    }

    public void addRecentChannel(int channelId) {
        try {
            JSONArray recentChannels = getRecentChannelsArray();
            long currentTime = System.currentTimeMillis();
            
            JSONObject channelEntry = null;
            int existingIndex = -1;
            
            for (int i = 0; i < recentChannels.length(); i++) {
                JSONObject entry = recentChannels.getJSONObject(i);
                if (entry.getInt("id") == channelId) {
                    channelEntry = entry;
                    existingIndex = i;
                    break;
                }
            }
            
            if (channelEntry != null) {
                recentChannels.remove(existingIndex);
            } else {
                channelEntry = new JSONObject();
                channelEntry.put("id", channelId);
            }
            
            channelEntry.put("timestamp", currentTime);
            
            JSONArray newArray = new JSONArray();
            newArray.put(channelEntry);
            
            for (int i = 0; i < recentChannels.length(); i++) {
                newArray.put(recentChannels.getJSONObject(i));
            }
            
            prefs.edit().putString(KEY_RECENT_CHANNELS, newArray.toString()).apply();
            
        } catch (JSONException e) {
            Log.e(TAG, "Error adding recent channel", e);
        }
    }

    public List<Integer> getRecentChannelIds() {
        List<Integer> recentIds = new ArrayList<>();
        try {
            JSONArray recentChannels = getRecentChannelsArray();
            long currentTime = System.currentTimeMillis();
            
            for (int i = 0; i < recentChannels.length(); i++) {
                JSONObject entry = recentChannels.getJSONObject(i);
                long timestamp = entry.getLong("timestamp");
                
                if (currentTime - timestamp < RECENT_CHANNEL_DURATION) {
                    recentIds.add(entry.getInt("id"));
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error getting recent channel IDs", e);
        }
        return recentIds;
    }

    public void cleanExpiredRecentChannels() {
        try {
            JSONArray recentChannels = getRecentChannelsArray();
            JSONArray cleanedArray = new JSONArray();
            long currentTime = System.currentTimeMillis();
            
            for (int i = 0; i < recentChannels.length(); i++) {
                JSONObject entry = recentChannels.getJSONObject(i);
                long timestamp = entry.getLong("timestamp");
                
                if (currentTime - timestamp < RECENT_CHANNEL_DURATION) {
                    cleanedArray.put(entry);
                }
            }
            
            prefs.edit().putString(KEY_RECENT_CHANNELS, cleanedArray.toString()).apply();
            
        } catch (JSONException e) {
            Log.e(TAG, "Error cleaning expired recent channels", e);
        }
    }

    private JSONArray getRecentChannelsArray() {
        String json = prefs.getString(KEY_RECENT_CHANNELS, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing recent channels JSON", e);
            return new JSONArray();
        }
    }
}
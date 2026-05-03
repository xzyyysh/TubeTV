package io.tubetvlol.tubetv.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import io.tubetvlol.tubetv.R;
import io.tubetvlol.tubetv.utils.PreferencesManager;

public class SettingsActivity extends AppCompatActivity {

    private PreferencesManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        hideSystemUI();

        prefsManager = new PreferencesManager(this);

        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        initializeSettingsControls();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void initializeSettingsControls() {
        Spinner controlsTimeoutSpinner = findViewById(R.id.controls_timeout_spinner);
        Spinner gridColumnsSpinner = findViewById(R.id.grid_columns_spinner);
        SwitchCompat showChannelNumbersSwitch = findViewById(R.id.show_channel_numbers_switch);
        SwitchCompat keepScreenOnSwitch = findViewById(R.id.keep_screen_on_switch);

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
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        showChannelNumbersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> 
            prefsManager.setShowChannelNumbers(isChecked)
        );

        keepScreenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> 
            prefsManager.setKeepScreenOn(isChecked)
        );
    }

    private void loadSettingsValues(Spinner timeoutSpinner, Spinner columnsSpinner, 
                                   SwitchCompat numbersSwitch, SwitchCompat screenSwitch) {
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
}
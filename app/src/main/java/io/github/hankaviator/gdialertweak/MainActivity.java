package io.github.hankaviator.gdialertweak;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.hankaviator.gdialertweak.testcall.TestCallManager;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.app_name);
        configureEdgeToEdge();
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.app_root));

        SharedPreferences prefs = openPreferences();
        prefs.edit()
                .putBoolean(Prefs.KEEP_APP, prefs.getBoolean(Prefs.KEEP_APP, true))
                .putBoolean(Prefs.RECORDING, prefs.getBoolean(Prefs.RECORDING, false))
                .apply();

        bindSetting(R.id.keep_app_row, R.id.keep_app_switch, prefs, Prefs.KEEP_APP, true);
        bindSetting(R.id.recording_row, R.id.recording_switch, prefs, Prefs.RECORDING, false);

        findViewById(R.id.enable_test_account).setOnClickListener(view -> {
            TestCallManager.register(this);
            startActivity(new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
        });
        findViewById(R.id.simulate_incoming_call).setOnClickListener(view -> {
            try {
                TestCallManager.startIncoming(this);
                Toast.makeText(this, R.string.test_call_requested, Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, R.string.enable_test_account_first, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindSetting(int rowId, int switchId, SharedPreferences prefs,
                             String key, boolean defaultValue) {
        View row = findViewById(rowId);
        MaterialSwitch toggle = findViewById(switchId);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener(
                (button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
    }

    private void configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private void applySystemBarInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(0, bars.top, 0, bars.bottom);
            int color = MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorSurface, Color.BLACK);
            boolean light = ColorUtils.calculateLuminance(color) > 0.5;
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                    getWindow(), getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(light);
            controller.setAppearanceLightNavigationBars(light);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @SuppressWarnings("deprecation")
    private SharedPreferences openPreferences() {
        try {
            return getSharedPreferences(Prefs.FILE, MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            Toast.makeText(this, R.string.open_through_lsposed, Toast.LENGTH_LONG).show();
            return getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        }
    }
}

package io.github.hankaviator.gdialertweak;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.hankaviator.gdialertweak.testcall.TestCallManager;

public final class MainActivity extends Activity {
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("GDialer Tweak");

        SharedPreferences prefs = openPreferences();
        // Re-save both values so LSPosed applies the requested world-readable mode
        // even when upgrading from a build that created the file as MODE_PRIVATE.
        prefs.edit()
                .putBoolean(Prefs.KEEP_APP, prefs.getBoolean(Prefs.KEEP_APP, true))
                .putBoolean(Prefs.RECORDING, prefs.getBoolean(Prefs.RECORDING, false))
                .apply();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(247, 248, 252));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(30), dp(24), dp(32));
        scroll.addView(page);

        TextView title = text("GDialer Tweak", 28, Color.rgb(28, 31, 38));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        page.addView(title);
        TextView subtitle = text("Hooks for Google Phone", 15, Color.rgb(88, 94, 105));
        subtitle.setPadding(0, dp(4), 0, dp(24));
        page.addView(subtitle);

        addSwitch(page, prefs, Prefs.KEEP_APP,
                "Keep the current app after answering",
                "Applies only when Answer is tapped in the incoming-call notification. Lock screen and launcher answers still open Google Phone.", true);
        addSwitch(page, prefs, Prefs.RECORDING,
                "Enable call recording",
                "Unlocks Google Phone's built-in recording controls. Local laws and Google's audible disclosure still apply.", false);

        TextView testTitle = text("Test incoming call", 19, Color.rgb(28, 31, 38));
        testTitle.setTypeface(testTitle.getTypeface(), Typeface.BOLD);
        testTitle.setPadding(0, dp(28), 0, dp(8));
        page.addView(testTitle);
        TextView help = text("Android requires the test calling account to be enabled once. The simulated call never reaches a carrier.", 14, Color.rgb(88, 94, 105));
        help.setPadding(0, 0, 0, dp(12));
        page.addView(help);

        Button configure = new Button(this);
        configure.setText(R.string.enable_test_account);
        configure.setOnClickListener(v -> {
            TestCallManager.register(this);
            startActivity(new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
        });
        page.addView(configure);

        Button simulate = new Button(this);
        simulate.setText(R.string.simulate_incoming_call);
        simulate.setOnClickListener(v -> {
            try {
                TestCallManager.startIncoming(this);
                Toast.makeText(this, "Test call requested", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Enable the test calling account first", Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(8);
        page.addView(simulate, buttonParams);

        TextView note = text("After changing a switch, force-stop Google Phone or reboot so its process reloads the hooks and preferences.", 13, Color.rgb(88, 94, 105));
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        note.setPadding(dp(8), dp(28), dp(8), 0);
        page.addView(note);
        setContentView(scroll);
    }

    @SuppressWarnings("deprecation")
    private SharedPreferences openPreferences() {
        try {
            // LSPosed API 93+ intercepts this mode for modules declaring
            // xposedsharedprefs and exposes the file to XSharedPreferences.
            return getSharedPreferences(Prefs.FILE, MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            Toast.makeText(this, "Open this screen through LSPosed to activate shared preferences",
                    Toast.LENGTH_LONG).show();
            return getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        }
    }

    private void addSwitch(LinearLayout page, SharedPreferences prefs, String key,
                           String label, String detail, boolean defaultValue) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextSize(17);
        toggle.setTextColor(Color.rgb(28, 31, 38));
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setPadding(0, dp(12), 0, dp(4));
        toggle.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        page.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(detail, 14, Color.rgb(88, 94, 105));
        description.setPadding(0, 0, 0, dp(12));
        page.addView(description);
    }
}

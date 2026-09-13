package io.github.hankaviator.gdialertweak;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.appbar.MaterialToolbar;

import io.github.hankaviator.gdialertweak.testcall.TestCallManager;

public final class MainActivity extends Activity {
    private int surfaceColor;
    private int surfaceContainerColor;
    private int onSurfaceColor;
    private int onSurfaceVariantColor;

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.app_name);
        configureEdgeToEdge();

        surfaceColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, Color.BLACK);
        surfaceContainerColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainer, surfaceColor);
        onSurfaceColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        onSurfaceVariantColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant, onSurfaceColor);

        SharedPreferences prefs = openPreferences();
        prefs.edit()
                .putBoolean(Prefs.KEEP_APP, prefs.getBoolean(Prefs.KEEP_APP, true))
                .putBoolean(Prefs.RECORDING, prefs.getBoolean(Prefs.RECORDING, false))
                .apply();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(surfaceColor);
        applySystemBarInsets(root);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.app_name);
        toolbar.setTitleTextColor(onSurfaceColor);
        toolbar.setBackgroundColor(surfaceColor);
        toolbar.setContentInsetsRelative(dp(16), dp(16));
        toolbar.setMinimumHeight(dp(64));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(surfaceColor);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(8), dp(20), dp(32));
        scroll.addView(page, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addFeatureCard(page, prefs, Prefs.KEEP_APP,
                "Keep current app after answering",
                "Answer from the notification without replacing the app you are using. Lock-screen and launcher answers still show Google Phone.",
                true);
        addFeatureCard(page, prefs, Prefs.RECORDING,
                "Enable call recording",
                "Unlock Google Phone's built-in recording controls. Google's audible disclosure and local laws still apply.",
                false);

        addTestCallCard(page);

        TextView note = text(
                "LSPosed scopes: Google Phone and Xiaomi InCallUI. Reboot after changing module scope.",
                13, onSurfaceVariantColor);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(12), dp(22), dp(12), 0);
        page.addView(note, matchWidthWrapHeight());

        setContentView(root);
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

    private void addFeatureCard(LinearLayout page, SharedPreferences prefs, String key,
                                String label, String detail, boolean defaultValue) {
        MaterialCardView card = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(18), dp(16), dp(18));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 17, onSurfaceColor);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        copy.addView(title, matchWidthWrapHeight());
        TextView description = text(detail, 14, onSurfaceVariantColor);
        description.setPadding(0, dp(6), dp(8), 0);
        copy.addView(description, matchWidthWrapHeight());
        row.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setContentDescription(label);
        toggle.setOnCheckedChangeListener(
                (button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(row);
        card.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        page.addView(card, cardLayoutParams());
    }

    private void addTestCallCard(LinearLayout page) {
        MaterialCardView card = card();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = text("Test incoming call", 20, onSurfaceColor);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        content.addView(title, matchWidthWrapHeight());
        TextView help = text(
                "Creates a local Telecom call without contacting a carrier. Enable the test calling account once before simulating a call.",
                14, onSurfaceVariantColor);
        help.setPadding(0, dp(6), 0, dp(16));
        content.addView(help, matchWidthWrapHeight());

        MaterialButton configure = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        configure.setText(R.string.enable_test_account);
        configure.setAllCaps(false);
        configure.setOnClickListener(view -> {
            TestCallManager.register(this);
            startActivity(new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
        });
        content.addView(configure, matchWidthWrapHeight());

        MaterialButton simulate = new MaterialButton(this);
        simulate.setText(R.string.simulate_incoming_call);
        simulate.setAllCaps(false);
        simulate.setOnClickListener(view -> {
            try {
                TestCallManager.startIncoming(this);
                Toast.makeText(this, "Test call requested", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "Enable the test calling account first",
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams simulateParams = matchWidthWrapHeight();
        simulateParams.topMargin = dp(8);
        content.addView(simulate, simulateParams);

        card.addView(content);
        LinearLayout.LayoutParams cardParams = cardLayoutParams();
        cardParams.topMargin = dp(20);
        page.addView(card, cardParams);
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(surfaceContainerColor);
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setAllCornerSizes(dp(24))
                .build());
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    private LinearLayout.LayoutParams cardLayoutParams() {
        LinearLayout.LayoutParams params = matchWidthWrapHeight();
        params.bottomMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @SuppressWarnings("deprecation")
    private SharedPreferences openPreferences() {
        try {
            return getSharedPreferences(Prefs.FILE, MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            Toast.makeText(this, "Open this screen through LSPosed to activate shared preferences",
                    Toast.LENGTH_LONG).show();
            return getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        }
    }
}

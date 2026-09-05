package com.jglass.diagnostic;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class JGlassHomeActivity extends Activity
        implements StartupGreetingController.Listener {
    private static final int COLOR_ACCENT = Color.rgb(67, 216, 255);
    private static final long CLOCK_REFRESH_MILLIS = 15_000L;
    private static final String GLASS_ACTION_GO_TO_SETTINGS =
            "com.google.glass.action.ACTION_GO_TO_SETTINGS";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshClock = new Runnable() {
        @Override
        public void run() {
            updateClock();
            if (resumed) main.postDelayed(this, CLOCK_REFRESH_MILLIS);
        }
    };
    private final Runnable restoreIdle = new Runnable() {
        @Override
        public void run() {
            if (resumed) showIdle();
        }
    };

    private StartupGreetingStore startupStore;
    private StartupGreetingController startupController;
    private DeviceConfigController deviceConfigController;
    private TextView clockView;
    private TextView dateView;
    private TextView statusView;
    private TextView detailView;
    private boolean resumed;
    private boolean greetingAttempted;
    private boolean newBootSession;
    private boolean centerLongPressed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startupStore = new StartupGreetingStore(this);
        startupController = new StartupGreetingController(this, startupStore, this);
        deviceConfigController = new DeviceConfigController(startupStore);
        newBootSession = startupStore.claimNewBootSession();
        setContentView(createContent());
        showIdle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        main.removeCallbacks(refreshClock);
        refreshClock.run();
        deviceConfigController.refresh();
        if (!greetingAttempted) {
            greetingAttempted = true;
            startGreeting();
        } else {
            showIdle();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        main.removeCallbacks(refreshClock);
        main.removeCallbacks(restoreIdle);
        startupController.cancel();
        deviceConfigController.cancel();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        startupController.cancel();
        deviceConfigController.cancel();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        openSystemSettings();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSystemSettings();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0) {
                centerLongPressed = false;
                event.startTracking();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            centerLongPressed = true;
            openSystemSettings();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!centerLongPressed && !event.isCanceled()) openAssistant();
            centerLongPressed = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onPlaybackStarted(final String summary, final String attribution) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!resumed) return;
                statusView.setText(summary);
                statusView.setTextColor(Color.WHITE);
                detailView.setText(attribution);
            }
        });
    }

    @Override
    public void onTextOnlyUnavailable(final String fallbackText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!resumed) return;
                statusView.setText(fallbackText);
                statusView.setTextColor(Color.LTGRAY);
                detailView.setText("");
                main.removeCallbacks(restoreIdle);
                main.postDelayed(restoreIdle, 2_000L);
            }
        });
    }

    @Override
    public void onFinished() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (resumed) showIdle();
            }
        });
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClickable(true);
        root.setFocusable(true);
        root.setLongClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAssistant();
            }
        });
        root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                openSystemSettings();
                return true;
            }
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(32), dp(8), dp(32), dp(8));
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        TextView brand = text(17, Color.LTGRAY);
        brand.setText(R.string.home_brand);
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(brand, row(dp(22), 0));

        clockView = text(58, Color.WHITE);
        clockView.setGravity(Gravity.CENTER);
        content.addView(clockView, row(0, 1));

        dateView = text(19, Color.LTGRAY);
        dateView.setGravity(Gravity.CENTER);
        dateView.setSingleLine(true);
        content.addView(dateView, row(dp(26), 0));

        statusView = text(18, COLOR_ACCENT);
        statusView.setGravity(Gravity.CENTER);
        statusView.setMaxLines(2);
        content.addView(statusView, row(dp(42), 0));

        detailView = text(13, Color.GRAY);
        detailView.setGravity(Gravity.CENTER);
        detailView.setSingleLine(true);
        content.addView(detailView, row(dp(18), 0));

        TextView controls = text(14, Color.DKGRAY);
        controls.setText(R.string.home_controls);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setSingleLine(true);
        content.addView(controls, row(dp(20), 0));
        return root;
    }

    private void startGreeting() {
        statusView.setText(R.string.startup_connecting);
        statusView.setTextColor(COLOR_ACCENT);
        detailView.setText("");
        boolean explicitForce = getIntent().getBooleanExtra(
                MainActivity.EXTRA_FORCE_STARTUP_GREETING, false);
        getIntent().removeExtra(MainActivity.EXTRA_FORCE_STARTUP_GREETING);
        boolean force = isDebuggable()
                && (explicitForce || (newBootSession && startupStore.debugReplayAlways()));
        if (!startupController.start(force, true)) showIdle();
    }

    private void showIdle() {
        main.removeCallbacks(restoreIdle);
        statusView.setText(R.string.home_ready);
        statusView.setTextColor(COLOR_ACCENT);
        detailView.setText(R.string.home_ready_detail);
    }

    private void updateClock() {
        Date now = new Date();
        clockView.setText(new SimpleDateFormat("h:mm", Locale.US).format(now));
        dateView.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now));
    }

    private void openAssistant() {
        startupController.cancel();
        Intent assistant = new Intent(this, MainActivity.class);
        assistant.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(assistant);
    }

    private void openSystemSettings() {
        Intent settings = resolvedSettingsIntent();
        if (settings == null) {
            statusView.setText(R.string.home_settings_unavailable);
            statusView.setTextColor(Color.LTGRAY);
            detailView.setText("");
            return;
        }
        startupController.cancel();
        startActivity(settings);
    }

    private Intent resolvedSettingsIntent() {
        Intent standard = new Intent(Settings.ACTION_SETTINGS);
        if (standard.resolveActivity(getPackageManager()) != null) return standard;
        Intent glass = new Intent(GLASS_ACTION_GO_TO_SETTINGS);
        return glass.resolveActivity(getPackageManager()) == null ? null : glass;
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private TextView text(int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setSingleLine(false);
        return view;
    }

    private LinearLayout.LayoutParams row(int height, int weight) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height,
                weight);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

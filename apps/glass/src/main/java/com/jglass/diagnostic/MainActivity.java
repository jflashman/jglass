package com.jglass.diagnostic;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class MainActivity extends Activity implements
        AudioLoopbackController.Listener,
        BleGatewayClient.Listener,
        StartupGreetingController.Listener {
    public static final String EXTRA_BRIDGE_HEALTH_URL = "bridge_health_url";
    public static final String EXTRA_GATEWAY_PROBE_MODE = "gateway_probe_mode";
    public static final String EXTRA_STARTUP_BRIDGE_URL = "startup_bridge_url";
    public static final String EXTRA_STARTUP_DEVICE_TOKEN = "startup_device_token";
    public static final String EXTRA_FORCE_STARTUP_GREETING = "force_startup_greeting";

    private static final int COLOR_READY = Color.rgb(67, 216, 255);
    private static final int COLOR_RECORDING = Color.rgb(255, 82, 82);
    private static final int COLOR_PLAYING = Color.rgb(255, 205, 86);
    private static final int COLOR_ERROR = Color.rgb(255, 138, 101);

    private AudioLoopbackController audio;
    private BleGatewayClient gatewayClient;
    private StartupGreetingStore startupStore;
    private StartupGreetingController startupController;
    private View readyContent;
    private View startupContent;
    private StartupLoaderView startupLoader;
    private TextView startupSummary;
    private TextView startupAttribution;
    private TextView stateView;
    private TextView instructionView;
    private TextView networkView;
    private TextView bluetoothView;
    private TextView gatewayView;
    private boolean resumed;
    private boolean diagnosticsStarted;
    private final Runnable finishTextOnlyStartup = new Runnable() {
        @Override
        public void run() {
            if (resumed) finishStartup();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audio = new AudioLoopbackController(this);
        gatewayClient = new BleGatewayClient(this, this);
        startupStore = new StartupGreetingStore(this);
        startupController = new StartupGreetingController(startupStore, this);
        provisionStartupConfiguration(getIntent());
        setContentView(createRootContent());
        showReady();
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (startupSummary != null) startupSummary.removeCallbacks(finishTextOnlyStartup);
        startupController.cancel();
        startupLoader.stop();
        gatewayClient.stop();
        audio.cancel();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (bluetoothView != null) {
            bluetoothView.setText(BluetoothReadiness.summarize(this));
        }
        if (!diagnosticsStarted) {
            startStartupExperience();
        } else {
            activateDiagnostics();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        startupController.cancel();
        startupSummary.removeCallbacks(finishTextOnlyStartup);
        startupLoader.stop();
        gatewayClient.stop();
        setIntent(intent);
        provisionStartupConfiguration(intent);
        diagnosticsStarted = false;
        if (resumed) {
            startStartupExperience();
        }
    }

    @Override
    public void onBackPressed() {
        startupController.cancel();
        audio.cancel();
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (diagnosticsStarted) toggleAudio();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRecordingStarted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stateView.setText(R.string.state_recording);
                stateView.setTextColor(COLOR_RECORDING);
                instructionView.setText(R.string.instruction_recording);
            }
        });
    }

    @Override
    public void onPlaybackStarted(final int durationMillis) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stateView.setText(R.string.state_playing);
                stateView.setTextColor(COLOR_PLAYING);
                instructionView.setText(String.format(
                        Locale.US,
                        "%s (%.1f s)",
                        getString(R.string.instruction_playing),
                        durationMillis / 1000.0));
            }
        });
    }

    @Override
    public void onIdle() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showReady();
            }
        });
    }

    @Override
    public void onError(final String safeMessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stateView.setText(R.string.state_error);
                stateView.setTextColor(COLOR_ERROR);
                instructionView.setText(getString(R.string.error_retry, safeMessage));
            }
        });
    }

    @Override
    public void onPlaybackStarted(final String summary, final String attribution) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!resumed) return;
                startupLoader.stop();
                startupLoader.setVisibility(View.GONE);
                startupSummary.setText(summary);
                startupSummary.setTextColor(Color.WHITE);
                startupAttribution.setText(attribution);
            }
        });
    }

    @Override
    public void onTextOnlyUnavailable(final String fallbackText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!resumed) return;
                startupLoader.stop();
                startupLoader.setVisibility(View.GONE);
                startupSummary.setText(fallbackText);
                startupSummary.setTextColor(Color.LTGRAY);
                startupAttribution.setText("");
                startupSummary.removeCallbacks(finishTextOnlyStartup);
                startupSummary.postDelayed(finishTextOnlyStartup, 1800L);
            }
        });
    }

    @Override
    public void onFinished() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (resumed) finishStartup();
            }
        });
    }

    @Override
    public void onGatewayStatus(
            final BleGatewayClient.Status status,
            final GatewayProbeCoordinator.Snapshot snapshot) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (gatewayView == null) {
                    return;
                }
                switch (status) {
                    case SCANNING:
                        gatewayView.setText(R.string.gateway_scanning);
                        gatewayView.setTextColor(Color.GRAY);
                        break;
                    case CONNECTING:
                        gatewayView.setText(R.string.gateway_connecting);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case DISCOVERING:
                        gatewayView.setText(R.string.gateway_discovering);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case SUBSCRIBING:
                        gatewayView.setText(R.string.gateway_subscribing);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case SENDING_HELLO:
                        gatewayView.setText(R.string.gateway_sending_hello);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case WAITING_HELLO_ACK:
                        gatewayView.setText(R.string.gateway_waiting_hello_ack);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case SENDING_PING:
                        gatewayView.setText(R.string.gateway_sending_ping);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case WAITING_PONG:
                        gatewayView.setText(R.string.gateway_waiting_pong);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case WAITING_RETRY:
                        gatewayView.setText(R.string.gateway_waiting_retry);
                        gatewayView.setTextColor(Color.GRAY);
                        break;
                    case PONG_INTERNET:
                        gatewayView.setText(R.string.gateway_pong_internet);
                        gatewayView.setTextColor(COLOR_READY);
                        break;
                    case PONG_OFFLINE:
                        gatewayView.setText(R.string.gateway_pong_offline);
                        gatewayView.setTextColor(COLOR_PLAYING);
                        break;
                    case NOT_FOUND:
                        gatewayView.setText(R.string.gateway_not_found);
                        gatewayView.setTextColor(Color.GRAY);
                        break;
                    case BLUETOOTH_OFF:
                        gatewayView.setText(R.string.gateway_bluetooth_off);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case BLE_UNAVAILABLE:
                        gatewayView.setText(R.string.gateway_ble_unavailable);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case CONNECTION_FAILED:
                        gatewayView.setText(R.string.gateway_connection_failed);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case SERVICE_INVALID:
                        gatewayView.setText(R.string.gateway_service_invalid);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case SUBSCRIBE_FAILED:
                        gatewayView.setText(R.string.gateway_subscribe_failed);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case WRITE_FAILED:
                        gatewayView.setText(R.string.gateway_write_failed);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case PROTOCOL_FAILED:
                        gatewayView.setText(R.string.gateway_protocol_failed);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case TIMED_OUT:
                        gatewayView.setText(R.string.gateway_timed_out);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                    case START_FAILED:
                    default:
                        gatewayView.setText(R.string.gateway_scan_failed);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                }
                gatewayView.append("\nProbe: " + snapshot.summary());
            }
        });
    }

    private View createRootContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        readyContent = createReadyContent();
        startupContent = createStartupContent();
        root.addView(readyContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(startupContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View createReadyContent() {
        int horizontal = dp(32);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(horizontal, dp(20), horizontal, dp(16));
        content.setBackgroundColor(Color.BLACK);
        content.setClickable(true);
        content.setFocusable(true);
        content.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleAudio();
            }
        });

        TextView title = text(18, Color.LTGRAY);
        title.setText(R.string.title);
        content.addView(title, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        stateView = text(38, COLOR_READY);
        stateView.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(stateView, row(LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        instructionView = text(22, Color.WHITE);
        content.addView(instructionView, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        networkView = text(16, Color.GRAY);
        networkView.setText(R.string.network_not_configured);
        content.addView(networkView, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        bluetoothView = text(14, Color.GRAY);
        bluetoothView.setText(BluetoothReadiness.summarize(this));
        content.addView(bluetoothView, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        gatewayView = text(14, Color.GRAY);
        gatewayView.setMaxLines(2);
        gatewayView.setText(R.string.gateway_waiting);
        content.addView(gatewayView, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        TextView footer = text(14, Color.DKGRAY);
        footer.setText(R.string.footer);
        content.addView(footer, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        return content;
    }

    private View createStartupContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(10), dp(28), dp(10));
        content.setBackgroundColor(Color.BLACK);

        TextView brand = text(18, Color.LTGRAY);
        brand.setText(R.string.startup_brand);
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(brand, row(dp(28), 0));

        startupLoader = new StartupLoaderView(this);
        content.addView(startupLoader, row(0, 1));

        startupSummary = text(22, Color.WHITE);
        startupSummary.setGravity(Gravity.CENTER);
        startupSummary.setMaxLines(3);
        content.addView(startupSummary, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        startupAttribution = text(13, Color.GRAY);
        startupAttribution.setGravity(Gravity.CENTER_HORIZONTAL);
        startupAttribution.setMaxLines(1);
        content.addView(startupAttribution, row(dp(22), 0));
        return content;
    }

    private void toggleAudio() {
        if (!audio.isBusy()) {
            audio.startRecording();
        } else if (audio.isRecording()) {
            audio.stopRecordingAndPlay();
        } else {
            audio.cancel();
        }
    }

    private void showReady() {
        stateView.setText(R.string.state_ready);
        stateView.setTextColor(COLOR_READY);
        instructionView.setText(R.string.instruction_ready);
    }

    private void startStartupExperience() {
        startupSummary.removeCallbacks(finishTextOnlyStartup);
        readyContent.setVisibility(View.GONE);
        startupContent.setVisibility(View.VISIBLE);
        startupLoader.setVisibility(View.VISIBLE);
        startupSummary.setText(R.string.startup_connecting);
        startupAttribution.setText("");
        startupLoader.start();
        if (!startupController.start(forceStartupGreeting())) {
            finishStartup();
        }
    }

    private void finishStartup() {
        diagnosticsStarted = true;
        startupLoader.stop();
        startupContent.setVisibility(View.GONE);
        readyContent.setVisibility(View.VISIBLE);
        showReady();
        activateDiagnostics();
    }

    private void activateDiagnostics() {
        readyContent.setVisibility(View.VISIBLE);
        startupContent.setVisibility(View.GONE);
        beginLanProbe();
        if (resumed) gatewayClient.start(currentGatewayProbeMode());
    }

    private void provisionStartupConfiguration(Intent intent) {
        if (!isDebuggable()) return;
        String bridgeUrl = intent.getStringExtra(EXTRA_STARTUP_BRIDGE_URL);
        String token = intent.getStringExtra(EXTRA_STARTUP_DEVICE_TOKEN);
        if (bridgeUrl != null || token != null) {
            startupStore.saveConfiguration(bridgeUrl, token);
            intent.removeExtra(EXTRA_STARTUP_BRIDGE_URL);
            intent.removeExtra(EXTRA_STARTUP_DEVICE_TOKEN);
        }
    }

    private boolean forceStartupGreeting() {
        return isDebuggable() && getIntent().getBooleanExtra(EXTRA_FORCE_STARTUP_GREETING, false);
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private void beginLanProbe() {
        String healthUrl = getIntent().getStringExtra(EXTRA_BRIDGE_HEALTH_URL);
        if (!BridgeHealthProbe.isSafeHealthUrl(healthUrl)
                && StartupGreetingClient.isSafeBridgeBaseUrl(startupStore.bridgeUrl())) {
            String base = startupStore.bridgeUrl();
            healthUrl = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                    + "/health";
        }
        if (!BridgeHealthProbe.isSafeHealthUrl(healthUrl)) {
            networkView.setText(R.string.network_not_configured);
            return;
        }

        networkView.setText(R.string.network_checking);
        BridgeHealthProbe.check(healthUrl, new BridgeHealthProbe.Listener() {
            @Override
            public void onResult(final boolean reachable) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        networkView.setText(reachable
                                ? R.string.network_ok
                                : R.string.network_failed);
                        networkView.setTextColor(reachable ? COLOR_READY : COLOR_ERROR);
                    }
                });
            }
        });
    }

    private GatewayProbeCoordinator.Mode currentGatewayProbeMode() {
        return GatewayProbeCoordinator.Mode.fromValue(
                getIntent().getStringExtra(EXTRA_GATEWAY_PROBE_MODE));
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

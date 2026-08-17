package com.jglass.diagnostic;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class MainActivity extends Activity implements
        AudioLoopbackController.Listener,
        BleGatewayScanner.Listener {
    public static final String EXTRA_BRIDGE_HEALTH_URL = "bridge_health_url";

    private static final int COLOR_READY = Color.rgb(67, 216, 255);
    private static final int COLOR_RECORDING = Color.rgb(255, 82, 82);
    private static final int COLOR_PLAYING = Color.rgb(255, 205, 86);
    private static final int COLOR_ERROR = Color.rgb(255, 138, 101);

    private AudioLoopbackController audio;
    private BleGatewayScanner gatewayScanner;
    private TextView stateView;
    private TextView instructionView;
    private TextView networkView;
    private TextView bluetoothView;
    private TextView gatewayView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audio = new AudioLoopbackController(this);
        gatewayScanner = new BleGatewayScanner(this, this);
        setContentView(createContentView());
        showReady();
        beginLanProbe();
    }

    @Override
    protected void onPause() {
        gatewayScanner.stop();
        audio.cancel();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothView != null) {
            bluetoothView.setText(BluetoothReadiness.summarize(this));
        }
        gatewayScanner.start();
    }

    @Override
    public void onBackPressed() {
        audio.cancel();
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleAudio();
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
    public void onGatewayScanStatus(final BleGatewayScanner.Status status) {
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
                    case FOUND:
                        gatewayView.setText(R.string.gateway_found);
                        gatewayView.setTextColor(COLOR_READY);
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
                    case START_FAILED:
                    default:
                        gatewayView.setText(R.string.gateway_scan_failed);
                        gatewayView.setTextColor(COLOR_ERROR);
                        break;
                }
            }
        });
    }

    private View createContentView() {
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
        gatewayView.setText(R.string.gateway_waiting);
        content.addView(gatewayView, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

        TextView footer = text(14, Color.DKGRAY);
        footer.setText(R.string.footer);
        content.addView(footer, row(LinearLayout.LayoutParams.WRAP_CONTENT, 0));

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

    private void beginLanProbe() {
        String healthUrl = getIntent().getStringExtra(EXTRA_BRIDGE_HEALTH_URL);
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

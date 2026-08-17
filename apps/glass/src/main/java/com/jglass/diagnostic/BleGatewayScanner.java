package com.jglass.diagnostic;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;

final class BleGatewayScanner {
    static final UUID GATEWAY_SERVICE_UUID =
            UUID.fromString("c91249f4-d2a1-4d93-8f66-ce0a24753b9d");
    static final long SCAN_DURATION_MILLIS = 10_000L;

    enum Status {
        SCANNING,
        FOUND,
        NOT_FOUND,
        BLUETOOTH_OFF,
        BLE_UNAVAILABLE,
        START_FAILED
    }

    interface Listener {
        void onGatewayScanStatus(Status status);
    }

    private final Context context;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean scanning;

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            if (!scanning) {
                return;
            }
            stopInternal();
            listener.onGatewayScanStatus(Status.NOT_FOUND);
        }
    };

    private final BluetoothAdapter.LeScanCallback callback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    if (!scanning) {
                        return;
                    }

                    // The platform filter matched the public JGlass service UUID.
                    // Deliberately do not inspect or retain the device, address,
                    // signal strength, or advertisement bytes.
                    stopInternal();
                    listener.onGatewayScanStatus(Status.FOUND);
                }
            };

    BleGatewayScanner(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    @SuppressLint("MissingPermission")
    void start() {
        stop();

        if (adapter == null || !context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            listener.onGatewayScanStatus(Status.BLE_UNAVAILABLE);
            return;
        }
        if (!adapter.isEnabled()) {
            listener.onGatewayScanStatus(Status.BLUETOOTH_OFF);
            return;
        }

        listener.onGatewayScanStatus(Status.SCANNING);
        scanning = true;
        boolean started = adapter.startLeScan(
                new UUID[]{GATEWAY_SERVICE_UUID},
                callback);
        if (!started) {
            scanning = false;
            listener.onGatewayScanStatus(Status.START_FAILED);
            return;
        }
        mainHandler.postDelayed(timeout, SCAN_DURATION_MILLIS);
    }

    void stop() {
        if (scanning) {
            stopInternal();
        } else {
            mainHandler.removeCallbacks(timeout);
        }
    }

    @SuppressLint("MissingPermission")
    private void stopInternal() {
        mainHandler.removeCallbacks(timeout);
        adapter.stopLeScan(callback);
        scanning = false;
    }
}

package com.jglass.diagnostic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Locale;

final class BluetoothReadiness {
    private BluetoothReadiness() {
    }

    static String summarize(Context context) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return "Bluetooth: unavailable";
        }
        if (!adapter.isEnabled()) {
            return "Bluetooth: off";
        }

        PackageManager packages = context.getPackageManager();
        boolean hasBle = packages.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        int bondedCount = adapter.getBondedDevices().size();
        boolean headsetConnected = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                == BluetoothAdapter.STATE_CONNECTED;
        boolean mediaConnected = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                == BluetoothAdapter.STATE_CONNECTED;

        return String.format(
                Locale.US,
                "Bluetooth: on · BLE %s · paired %d · headset %s · media %s",
                hasBle ? "yes" : "no",
                bondedCount,
                headsetConnected ? "connected" : "idle",
                mediaConnected ? "connected" : "idle");
    }
}

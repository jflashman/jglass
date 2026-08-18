package com.jglass.diagnostic;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class BleGatewayClient {
    static final UUID GATEWAY_SERVICE_UUID =
            UUID.fromString("c91249f4-d2a1-4d93-8f66-ce0a24753b9d");
    static final UUID CONTROL_UPLINK_UUID =
            UUID.fromString("fe398f65-82ad-4e3e-b46b-5978725413c1");
    static final UUID CONTROL_DOWNLINK_UUID =
            UUID.fromString("1d40843f-bab8-4cec-a70e-72e839205998");

    private static final UUID CLIENT_CONFIGURATION_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final long SCAN_DURATION_MILLIS = 10_000L;
    private static final long PHASE_TIMEOUT_MILLIS = 15_000L;
    private static final int ATT_PACKET_LENGTH = 20;

    enum Status {
        SCANNING,
        CONNECTING,
        DISCOVERING,
        SUBSCRIBING,
        SENDING_HELLO,
        WAITING_HELLO_ACK,
        SENDING_PING,
        WAITING_PONG,
        WAITING_RETRY,
        PONG_INTERNET,
        PONG_OFFLINE,
        NOT_FOUND,
        BLUETOOTH_OFF,
        BLE_UNAVAILABLE,
        START_FAILED,
        CONNECTION_FAILED,
        SERVICE_INVALID,
        SUBSCRIBE_FAILED,
        WRITE_FAILED,
        PROTOCOL_FAILED,
        TIMED_OUT
    }

    interface Listener {
        void onGatewayStatus(Status status, GatewayProbeCoordinator.Snapshot snapshot);
    }

    private enum ExpectedResponse {
        NONE,
        HELLO_ACK,
        PONG
    }

    private final Context context;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BleControlFraming.Reassembler reassembler =
            new BleControlFraming.Reassembler();

    private boolean active;
    private boolean connectionCycleActive;
    private boolean exchangeTerminalHandled;
    private boolean scanning;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic uplink;
    private BluetoothGattCharacteristic downlink;
    private List<byte[]> pendingWrites = Collections.emptyList();
    private int pendingWriteIndex;
    private int nextStreamId = 1;
    private int nextMessageSequence;
    private ExpectedResponse expectedResponse = ExpectedResponse.NONE;
    private GatewayProbeCoordinator coordinator;
    private String sessionId;
    private String helloRequestId;
    private String pingRequestId;
    private int helloSequence;
    private int pingSequence;

    private final Runnable scanTimeout = new Runnable() {
        @Override
        public void run() {
            if (!active || !connectionCycleActive || !scanning) {
                return;
            }
            stopScan();
            fail(Status.NOT_FOUND);
        }
    };

    private final Runnable phaseTimeout = new Runnable() {
        @Override
        public void run() {
            if (active && connectionCycleActive) {
                fail(Status.TIMED_OUT);
            }
        }
    };

    private final Runnable scheduledAction = new Runnable() {
        @Override
        public void run() {
            if (!active || coordinator == null) {
                return;
            }
            GatewayProbeCoordinator.Snapshot snapshot = coordinator.snapshot();
            if (snapshot.mode == GatewayProbeCoordinator.Mode.CONNECTED_WAKE) {
                if (!connectionCycleActive || gatt == null || uplink == null) {
                    fail(Status.CONNECTION_FAILED);
                    return;
                }
                exchangeTerminalHandled = false;
                coordinator.onConnectedPingStarted();
                sendPing();
                return;
            }
            startConnectionCycle();
        }
    };

    private final BluetoothAdapter.LeScanCallback scanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    if (!active || !connectionCycleActive || !scanning
                            || !BleAdvertisementMatcher.containsServiceUuid(
                            scanRecord, GATEWAY_SERVICE_UUID)) {
                        return;
                    }

                    // Retain the device object only for this bounded connection. Never inspect or
                    // expose its address, name, signal strength, or full advertisement payload.
                    stopScan();
                    notifyStatus(Status.CONNECTING);
                    schedulePhaseTimeout();
                    gatt = device.connectGatt(context, false, gattCallback);
                    if (gatt == null) {
                        fail(Status.CONNECTION_FAILED);
                    }
                }
            };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(
                BluetoothGatt callbackGatt,
                int status,
                int newState) {
            if (!active || !connectionCycleActive) {
                callbackGatt.close();
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS
                    || newState != BluetoothProfile.STATE_CONNECTED) {
                fail(Status.CONNECTION_FAILED);
                return;
            }

            notifyStatus(Status.DISCOVERING);
            schedulePhaseTimeout();
            if (!callbackGatt.discoverServices()) {
                fail(Status.SERVICE_INVALID);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt callbackGatt, int status) {
            if (!active || !connectionCycleActive) {
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(Status.SERVICE_INVALID);
                return;
            }

            BluetoothGattService service = callbackGatt.getService(GATEWAY_SERVICE_UUID);
            if (service == null) {
                fail(Status.SERVICE_INVALID);
                return;
            }
            uplink = service.getCharacteristic(CONTROL_UPLINK_UUID);
            downlink = service.getCharacteristic(CONTROL_DOWNLINK_UUID);
            if (uplink == null || downlink == null
                    || (uplink.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
                    || (downlink.getProperties() & (BluetoothGattCharacteristic.PROPERTY_INDICATE
                    | BluetoothGattCharacteristic.PROPERTY_NOTIFY)) == 0) {
                fail(Status.SERVICE_INVALID);
                return;
            }

            BluetoothGattDescriptor configuration =
                    downlink.getDescriptor(CLIENT_CONFIGURATION_UUID);
            if (configuration == null
                    || !callbackGatt.setCharacteristicNotification(downlink, true)) {
                fail(Status.SUBSCRIBE_FAILED);
                return;
            }

            notifyStatus(Status.SUBSCRIBING);
            byte[] configurationValue =
                    (downlink.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                            ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            configuration.setValue(configurationValue);
            schedulePhaseTimeout();
            if (!callbackGatt.writeDescriptor(configuration)) {
                fail(Status.SUBSCRIBE_FAILED);
            }
        }

        @Override
        public void onDescriptorWrite(
                BluetoothGatt callbackGatt,
                BluetoothGattDescriptor descriptor,
                int status) {
            if (!active || !connectionCycleActive
                    || !CLIENT_CONFIGURATION_UUID.equals(descriptor.getUuid())) {
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(Status.SUBSCRIBE_FAILED);
                return;
            }
            sendHello();
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt callbackGatt,
                BluetoothGattCharacteristic characteristic,
                int status) {
            if (!active || !connectionCycleActive
                    || !CONTROL_UPLINK_UUID.equals(characteristic.getUuid())) {
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(Status.WRITE_FAILED);
                return;
            }
            pendingWriteIndex++;
            if (pendingWriteIndex < pendingWrites.size()) {
                writeNextFragment();
                return;
            }
            notifyStatus(expectedResponse == ExpectedResponse.HELLO_ACK
                    ? Status.WAITING_HELLO_ACK
                    : Status.WAITING_PONG);
            schedulePhaseTimeout();
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt callbackGatt,
                BluetoothGattCharacteristic characteristic) {
            if (!active || !connectionCycleActive
                    || !CONTROL_DOWNLINK_UUID.equals(characteristic.getUuid())) {
                return;
            }
            try {
                byte[] completed = reassembler.accept(
                        characteristic.getValue(), SystemClock.elapsedRealtime());
                if (completed != null) {
                    handleResponse(completed);
                }
            } catch (RuntimeException error) {
                fail(Status.PROTOCOL_FAILED);
            }
        }
    };

    BleGatewayClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    @SuppressLint("MissingPermission")
    void start(GatewayProbeCoordinator.Mode mode) {
        stop();
        active = true;
        coordinator = new GatewayProbeCoordinator(mode);
        startConnectionCycle();
    }

    @SuppressLint("MissingPermission")
    private void startConnectionCycle() {
        if (!active || coordinator == null) {
            return;
        }
        mainHandler.removeCallbacks(scheduledAction);
        closeGatt();
        reassembler.reset();
        pendingWrites = Collections.emptyList();
        expectedResponse = ExpectedResponse.NONE;
        connectionCycleActive = true;
        exchangeTerminalHandled = false;
        coordinator.onConnectionCycleStarted();
        if (adapter == null || !context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            fail(Status.BLE_UNAVAILABLE);
            return;
        }
        if (!adapter.isEnabled()) {
            fail(Status.BLUETOOTH_OFF);
            return;
        }

        sessionId = "s" + Long.toHexString(SystemClock.elapsedRealtime());
        helloRequestId = "h" + Long.toHexString(SystemClock.elapsedRealtime());
        nextMessageSequence = 1;
        notifyStatus(Status.SCANNING);
        scanning = true;
        if (!adapter.startLeScan(scanCallback)) {
            scanning = false;
            fail(Status.START_FAILED);
            return;
        }
        mainHandler.postDelayed(scanTimeout, SCAN_DURATION_MILLIS);
    }

    void stop() {
        active = false;
        connectionCycleActive = false;
        exchangeTerminalHandled = true;
        mainHandler.removeCallbacks(scanTimeout);
        mainHandler.removeCallbacks(phaseTimeout);
        mainHandler.removeCallbacks(scheduledAction);
        if (scanning) {
            stopScan();
        }
        closeGatt();
        reassembler.reset();
        pendingWrites = Collections.emptyList();
        expectedResponse = ExpectedResponse.NONE;
        coordinator = null;
    }

    private void sendHello() {
        try {
            helloSequence = nextMessageSequence++;
            JSONObject payload = new JSONObject()
                    .put("transport", "ble-gatt")
                    .put("role", "glass-central");
            sendMessage("gateway.hello", helloRequestId, helloSequence, payload,
                    ExpectedResponse.HELLO_ACK, Status.SENDING_HELLO);
        } catch (JSONException error) {
            fail(Status.PROTOCOL_FAILED);
        }
    }

    private void sendPing() {
        try {
            pingRequestId = "p" + Long.toHexString(SystemClock.elapsedRealtime())
                    + Integer.toHexString(nextMessageSequence);
            pingSequence = nextMessageSequence++;
            sendMessage("gateway.ping", pingRequestId, pingSequence, new JSONObject(),
                    ExpectedResponse.PONG, Status.SENDING_PING);
        } catch (JSONException error) {
            fail(Status.PROTOCOL_FAILED);
        }
    }

    private void sendMessage(
            String type,
            String requestId,
            int sequence,
            JSONObject payload,
            ExpectedResponse response,
            Status status) throws JSONException {
        JSONObject message = new JSONObject()
                .put("protocol", "jglass/1")
                .put("type", type)
                .put("sessionId", sessionId)
                .put("requestId", requestId)
                .put("sequence", sequence)
                .put("payload", payload);
        byte[] encoded = message.toString().getBytes(UTF_8);
        pendingWrites = BleControlFraming.fragment(
                encoded, nextStreamId, ATT_PACKET_LENGTH);
        nextStreamId = nextStreamId == 0xffff ? 1 : nextStreamId + 1;
        pendingWriteIndex = 0;
        expectedResponse = response;
        notifyStatus(status);
        schedulePhaseTimeout();
        writeNextFragment();
    }

    private void writeNextFragment() {
        if (!active || gatt == null || uplink == null
                || pendingWriteIndex >= pendingWrites.size()) {
            fail(Status.WRITE_FAILED);
            return;
        }
        uplink.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        uplink.setValue(pendingWrites.get(pendingWriteIndex));
        if (!gatt.writeCharacteristic(uplink)) {
            fail(Status.WRITE_FAILED);
        }
    }

    private void handleResponse(byte[] encoded) {
        if (expectedResponse == ExpectedResponse.NONE) {
            return;
        }
        try {
            JSONObject message = new JSONObject(new String(encoded, UTF_8));
            if (!"jglass/1".equals(message.optString("protocol"))
                    || !sessionId.equals(message.optString("sessionId"))) {
                fail(Status.PROTOCOL_FAILED);
                return;
            }

            if (expectedResponse == ExpectedResponse.HELLO_ACK
                    && "gateway.hello_ack".equals(message.optString("type"))
                    && helloRequestId.equals(message.optString("requestId"))
                    && message.optInt("sequence", -1) == helloSequence) {
                JSONObject payload = message.optJSONObject("payload");
                if (payload == null
                        || !"jglass/1".equals(payload.optString("acceptedProtocol"))
                        || !"iphone-peripheral".equals(payload.optString("role"))) {
                    fail(Status.PROTOCOL_FAILED);
                    return;
                }
                sendPing();
                return;
            }

            if (expectedResponse == ExpectedResponse.PONG
                    && "gateway.pong".equals(message.optString("type"))
                    && pingRequestId.equals(message.optString("requestId"))
                    && message.optInt("sequence", -1) == pingSequence) {
                JSONObject payload = message.optJSONObject("payload");
                if (payload == null || !payload.has("internetReachable")) {
                    fail(Status.PROTOCOL_FAILED);
                    return;
                }
                completeExchange(payload.optBoolean("internetReachable")
                        ? Status.PONG_INTERNET
                        : Status.PONG_OFFLINE);
                return;
            }
            fail(Status.PROTOCOL_FAILED);
        } catch (JSONException error) {
            fail(Status.PROTOCOL_FAILED);
        }
    }

    private void schedulePhaseTimeout() {
        mainHandler.removeCallbacks(phaseTimeout);
        mainHandler.postDelayed(phaseTimeout, PHASE_TIMEOUT_MILLIS);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        mainHandler.removeCallbacks(scanTimeout);
        if (adapter != null) {
            adapter.stopLeScan(scanCallback);
        }
        scanning = false;
    }

    private synchronized void completeExchange(Status status) {
        if (!active || !connectionCycleActive || exchangeTerminalHandled
                || coordinator == null) {
            return;
        }
        exchangeTerminalHandled = true;
        mainHandler.removeCallbacks(phaseTimeout);
        expectedResponse = ExpectedResponse.NONE;
        pendingWrites = Collections.emptyList();
        GatewayProbeCoordinator.Decision decision = coordinator.onExchangeSucceeded();
        notifyStatus(status);
        applyDecision(decision);
    }

    private synchronized void fail(Status status) {
        if (!active || exchangeTerminalHandled || coordinator == null) {
            return;
        }
        exchangeTerminalHandled = true;
        mainHandler.removeCallbacks(scanTimeout);
        mainHandler.removeCallbacks(phaseTimeout);
        if (scanning) {
            stopScan();
        }
        connectionCycleActive = false;
        closeGatt();
        reassembler.reset();
        pendingWrites = Collections.emptyList();
        expectedResponse = ExpectedResponse.NONE;
        GatewayProbeCoordinator.Decision decision = coordinator.onExchangeFailed(
                status.name().toLowerCase(Locale.US));
        notifyStatus(status);
        applyDecision(decision);
    }

    private void applyDecision(GatewayProbeCoordinator.Decision decision) {
        if (decision.action == GatewayProbeCoordinator.Action.COMPLETE) {
            active = false;
            connectionCycleActive = false;
            mainHandler.removeCallbacks(scheduledAction);
            closeGatt();
            return;
        }

        if (decision.action == GatewayProbeCoordinator.Action.START_CONNECTION_CYCLE) {
            connectionCycleActive = false;
            closeGatt();
        }
        notifyStatus(Status.WAITING_RETRY);
        mainHandler.postDelayed(scheduledAction, decision.delayMillis);
    }

    private void notifyStatus(Status status) {
        if (coordinator != null) {
            listener.onGatewayStatus(status, coordinator.snapshot());
        }
    }

    @SuppressLint("MissingPermission")
    private void closeGatt() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        uplink = null;
        downlink = null;
    }
}

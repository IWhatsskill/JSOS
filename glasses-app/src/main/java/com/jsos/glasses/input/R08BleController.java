package com.jsos.glasses.input;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayDeque;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

@SuppressLint("MissingPermission")
public final class R08BleController {
    private static final String TAG = "JSOSR08Ble";

    private static final UUID SERVICE_UUID = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID WRITE_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final byte OPCODE_TOUCH_CONTROL = 0x3B;
    private static final byte APP_TYPE_MUSIC_KEYS = 0x01;
    private static final int PACKET_SIZE = 16;
    private static final long SCAN_TIMEOUT_MS = 25_000L;
    private static final long KEEPALIVE_MS = 18_000L;
    private static final long RECONNECT_MS = 5_000L;
    private static final long SETUP_RETRY_1_MS = 700L;
    private static final long SETUP_RETRY_2_MS = 1_800L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> writes = new ArrayDeque<>();

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothDevice targetDevice;
    private boolean started;
    private boolean scanning;
    private boolean writing;

    private final Runnable scanTimeout = () -> {
        if (scanning) {
            stopScan();
            Log.i(TAG, "scan timeout");
            scheduleReconnect();
        }
    };

    private final Runnable keepAlive = new Runnable() {
        @Override
        public void run() {
            if (!started || writeCharacteristic == null) {
                return;
            }
            enqueue(tpSleepWake(APP_TYPE_MUSIC_KEYS, (byte) 1));
            handler.postDelayed(this, KEEPALIVE_MS);
        }
    };

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) return;
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (!isR08(device)) return;
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            Log.i(TAG, "bond state=" + state);
            if (state == BluetoothDevice.BOND_BONDED) connect(device);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (!isR08(device)) return;
            Log.i(TAG, "found " + safeName(device));
            stopScan();
            targetDevice = device;
            bondOrConnect(device);
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            Log.w(TAG, "scan failed code=" + errorCode);
            scheduleReconnect();
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            Log.i(TAG, "gatt state=" + newState + " status=" + status);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt = bluetoothGatt;
                writeCharacteristic = null;
                writes.clear();
                writing = false;
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt();
                scheduleReconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "service discovery failed status=" + status);
                scheduleReconnect();
                return;
            }
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                Log.w(TAG, "custom service missing");
                scheduleReconnect();
                return;
            }
            writeCharacteristic = service.getCharacteristic(WRITE_CHAR_UUID);
            if (writeCharacteristic == null) {
                Log.w(TAG, "write char missing");
                scheduleReconnect();
                return;
            }
            Log.i(TAG, "gatt ready");
            configureStableMode("initial");
            handler.postDelayed(() -> configureStableMode("retry-1"), SETUP_RETRY_1_MS);
            handler.postDelayed(() -> configureStableMode("retry-2"), SETUP_RETRY_2_MS);
            handler.removeCallbacks(keepAlive);
            handler.postDelayed(keepAlive, KEEPALIVE_MS);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "write status=" + status);
            writing = false;
            handler.postDelayed(R08BleController.this::drainWrites, 90);
        }
    };

    public R08BleController(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        if (started) return;
        started = true;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            Log.w(TAG, "adapter missing");
            return;
        }
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(bondReceiver, filter);
        }
        BluetoothDevice bonded = findBondedR08();
        if (bonded != null) {
            targetDevice = bonded;
            connect(bonded);
        } else {
            startScan();
        }
    }

    public void stop() {
        started = false;
        handler.removeCallbacksAndMessages(null);
        stopScan();
        closeGatt();
        try {
            context.unregisterReceiver(bondReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void restart() {
        stopScan();
        closeGatt();
        writes.clear();
        writing = false;
        if (!started) {
            start();
            return;
        }
        if (targetDevice != null) {
            bondOrConnect(targetDevice);
            return;
        }
        BluetoothDevice bonded = findBondedR08();
        if (bonded != null) {
            targetDevice = bonded;
            connect(bonded);
        } else {
            startScan();
        }
    }

    public boolean hasBondedR08() {
        ensureAdapter();
        return adapter != null && findBondedR08() != null;
    }

    public boolean forgetBondedR08() {
        ensureAdapter();
        stopScan();
        closeGatt();
        targetDevice = null;
        writes.clear();
        writing = false;
        if (adapter == null) {
            return false;
        }
        BluetoothDevice bonded = findBondedR08();
        if (bonded == null) {
            Log.i(TAG, "no bonded R08 to forget");
            return false;
        }
        try {
            Method removeBond = BluetoothDevice.class.getMethod("removeBond");
            boolean submitted = Boolean.TRUE.equals(removeBond.invoke(bonded));
            Log.i(TAG, "forget submitted=" + submitted + " name=" + safeName(bonded));
            return submitted;
        } catch (ReflectiveOperationException error) {
            Log.w(TAG, "forget failed");
            return false;
        }
    }

    private void ensureAdapter() {
        if (adapter != null) return;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    private BluetoothDevice findBondedR08() {
        if (adapter == null) {
            return null;
        }
        if (!hasConnectPermission()) {
            Log.w(TAG, "missing connect permission");
            return null;
        }
        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        if (bondedDevices == null) return null;
        for (BluetoothDevice device : bondedDevices) {
            if (isR08(device)) {
                Log.i(TAG, "using bonded " + safeName(device));
                return device;
            }
        }
        return null;
    }

    private void startScan() {
        if (!started || scanning) return;
        if (adapter == null) {
            Log.w(TAG, "adapter missing");
            return;
        }
        if (!hasScanPermission()) {
            Log.w(TAG, "missing scan permission");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.w(TAG, "scanner missing");
            return;
        }
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanning = true;
        scanner.startScan(null, settings, scanCallback);
        handler.removeCallbacks(scanTimeout);
        handler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS);
        Log.i(TAG, "scanning");
    }

    private void stopScan() {
        if (!scanning || scanner == null || !hasScanPermission()) {
            scanning = false;
            return;
        }
        scanner.stopScan(scanCallback);
        scanning = false;
        handler.removeCallbacks(scanTimeout);
    }

    private void bondOrConnect(BluetoothDevice device) {
        if (!hasConnectPermission()) {
            Log.w(TAG, "missing connect permission");
            return;
        }
        if (device.getBondState() == BluetoothDevice.BOND_NONE) {
            Log.i(TAG, "creating bond");
            if (!device.createBond()) connect(device);
        } else {
            connect(device);
        }
    }

    private void connect(BluetoothDevice device) {
        if (!started || !hasConnectPermission()) return;
        closeGatt();
        targetDevice = device;
        Log.i(TAG, "connecting " + safeName(device));
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private void closeGatt() {
        handler.removeCallbacks(keepAlive);
        writeCharacteristic = null;
        if (gatt != null && hasConnectPermission()) {
            gatt.disconnect();
            gatt.close();
        }
        gatt = null;
    }

    private void scheduleReconnect() {
        if (!started) return;
        handler.postDelayed(() -> {
            if (targetDevice != null) bondOrConnect(targetDevice);
            else startScan();
        }, RECONNECT_MS);
    }

    private void configureStableMode(String reason) {
        Log.i(TAG, "configure media-key mode reason=" + reason);
        enqueue(touchConfig(APP_TYPE_MUSIC_KEYS, 5));
        handler.postDelayed(() -> enqueue(gestureConfig((byte) 0, (byte) 0)), 260);
        handler.postDelayed(() -> enqueue(tpSleepWake(APP_TYPE_MUSIC_KEYS, (byte) 1)), 540);
    }

    private void enqueue(byte[] packet) {
        if (packet == null || writeCharacteristic == null || gatt == null) return;
        writes.add(packet);
        drainWrites();
    }

    private void drainWrites() {
        if (writing || writeCharacteristic == null || gatt == null || writes.isEmpty()) return;
        byte[] packet = writes.poll();
        writing = true;
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int result = gatt.writeCharacteristic(writeCharacteristic, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (result != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "write returned=" + result);
                writing = false;
                handler.postDelayed(this::drainWrites, 160);
            }
        } else {
            writeCharacteristic.setValue(packet);
            if (!gatt.writeCharacteristic(writeCharacteristic)) {
                Log.w(TAG, "write submit failed");
                writing = false;
                handler.postDelayed(this::drainWrites, 160);
            }
        }
    }

    private byte[] touchConfig(byte appType, int sleepMinutes) {
        byte[] packet = new byte[PACKET_SIZE];
        packet[0] = OPCODE_TOUCH_CONTROL;
        packet[1] = 0x02;
        packet[2] = 0x00;
        packet[3] = appType;
        packet[4] = (byte) sleepMinutes;
        addCrc(packet);
        return packet;
    }

    private byte[] gestureConfig(byte appType, byte strength) {
        byte[] packet = new byte[PACKET_SIZE];
        packet[0] = OPCODE_TOUCH_CONTROL;
        packet[1] = 0x02;
        packet[2] = 0x01;
        packet[3] = appType;
        packet[4] = strength;
        addCrc(packet);
        return packet;
    }

    private byte[] tpSleepWake(byte category, byte state) {
        byte[] packet = new byte[PACKET_SIZE];
        packet[0] = OPCODE_TOUCH_CONTROL;
        packet[1] = 0x02;
        packet[2] = 0x02;
        packet[3] = category;
        packet[4] = state;
        addCrc(packet);
        return packet;
    }

    private void addCrc(byte[] packet) {
        int crc = 0;
        for (int i = 0; i < packet.length - 1; i++) crc += packet[i] & 0xFF;
        packet[packet.length - 1] = (byte) (crc & 0xFF);
    }

    private boolean isR08(BluetoothDevice device) {
        if (device == null || !hasConnectPermission()) return false;
        String name = device.getName();
        return name != null && name.toUpperCase(Locale.US).startsWith("R08");
    }

    private String safeName(BluetoothDevice device) {
        if (device == null || !hasConnectPermission()) return "?";
        String name = device.getName();
        return name == null ? "?" : name;
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
}

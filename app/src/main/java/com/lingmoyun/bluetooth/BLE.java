package com.lingmoyun.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import com.lingmoyun.util.HexByteUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BLE
 *
 * @author guoweifeng
 * @date 2024/3/22 17:57
 */
public class BLE {
    private static final String TAG = "BLE";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Context mContext;

    public static void setContext(Context context) {
        BLE.mContext = context;
    }

    public static void scan(Consumer<Device> callback) {
        if (callback == null) return;
        // 权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // api >= 31
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                mainHandler.post(() -> Toast.makeText(mContext, "No Permission: " + Manifest.permission.BLUETOOTH_SCAN, Toast.LENGTH_LONG).show());
                return;
            }
        }

        BluetoothAdapter.LeScanCallback leScanCallback = (device, i, bytes) -> {
            // 权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // api >= 31
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    mainHandler.post(() -> Toast.makeText(mContext, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show());
                    return;
                }
            }
            String name = device.getName();
            if (name != null && !name.isEmpty()) {
                Device d = new Device();
                d.name = name;
                d.address = device.getAddress();
                d.device = device;
                callback.accept(d);
            }
        };

        BluetoothAdapter bluetoothAdapter = ContextCompat.getSystemService(mContext, BluetoothManager.class).getAdapter();
        boolean b = bluetoothAdapter.startLeScan(leScanCallback);
        mainHandler.post(() -> Toast.makeText(mContext, "Scan: " + b, Toast.LENGTH_LONG).show());
        new Handler().postDelayed(() -> bluetoothAdapter.stopLeScan(leScanCallback), 10 * 1000);
    }


    public static class Device {
        private static final Object CHARACTERISTIC_WRITE_LOCK = new Object();
        private static final Object DESCRIPTOR_WRITE_LOCK = new Object();

        private String name;
        private String address;

        private volatile boolean connected;
        private BluetoothDevice device;
        private BluetoothGatt gatt;
        private BluetoothGattService service;
        private BluetoothGattCharacteristic characteristicForRead;
        private BluetoothGattCharacteristic characteristicForWrite;
        private BluetoothGattCharacteristic characteristicForFlowControl;
        private final DataFC dataFC = new DataFC(0, 0);
        public Consumer<Boolean> onConnectChanged;
        public Consumer<byte[]> onValueChanged;

        private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                Log.d(TAG, "=====onConnectionStateChange: status: " + status + ", newState: " + newState);
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    // 权限检查
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // api >= 31
                        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            mainHandler.post(() -> Toast.makeText(mContext, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show());
                            return;
                        }
                    }

                    // 连接成功后发现服务
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    // it is important to close after disconnection, otherwise we will
                    // quickly run out of bluetooth resources, preventing new connections
                    gatt.close();

                    // 设备断开连接
                    connected = false;
                    if (onConnectChanged != null)
                        mainHandler.post(() -> onConnectChanged.accept(connected));
                }
            }


            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // 权限检查
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // api >= 31
                        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            mainHandler.post(() -> Toast.makeText(mContext, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show());
                            return;
                        }
                    }

                    // 服务发现成功，获取服务和特征
                    List<BluetoothGattService> services = gatt.getServices();
                    for (BluetoothGattService service : services) {
                        if (bleIdToInt(service.getUuid().toString()) == 0xff00) {
                            Device.this.service = service;
                            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
                                int characteristicIdInt = bleIdToInt(characteristic.getUuid().toString());
                                if (characteristicIdInt == 0xff01) {
                                    // read
                                    Device.this.characteristicForRead = characteristic;
                                } else if (characteristicIdInt == 0xff02) {
                                    // write
                                    Device.this.characteristicForWrite = characteristic;
                                } else if (characteristicIdInt == 0xff03) {
                                    // flow control
                                    Device.this.characteristicForFlowControl = characteristic;
                                }
                            }

                            break;
                        }
                    }

                    // 设置MTU
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(512);
                    }
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "=====onDescriptorWrite: ok");
                } else {
                    Log.d(TAG, "=====onDescriptorWrite: " + status);
                }
                synchronized (DESCRIPTOR_WRITE_LOCK) {
                    DESCRIPTOR_WRITE_LOCK.notifyAll();
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                synchronized (CHARACTERISTIC_WRITE_LOCK) {
                    CHARACTERISTIC_WRITE_LOCK.notifyAll();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] value = characteristic.getValue();
                onCharacteristicChanged(gatt, characteristic, value);
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
                if (characteristic.equals(characteristicForRead)) {
                    // read
                    Log.d(TAG, "=====read: " + HexByteUtils.byteArrayToHexStr(value));
                    if (onValueChanged != null)
                        mainHandler.post(() -> onValueChanged.accept(value));
                } else if (characteristic.equals(characteristicForFlowControl)) {
                    // flow control
                    Log.d(TAG, "=====flow control: " + HexByteUtils.byteArrayToHexStr(value));
                    dataFC.update(value);
                } else {
                    Log.d(TAG, "=====onCharacteristicChanged: " + HexByteUtils.byteArrayToHexStr(value));
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                Log.d(TAG, "=====onMtuChanged: " + mtu + ", status == SUCCESS is " + (BluetoothGatt.GATT_SUCCESS == status));

                new Thread(() -> {
                    dataFC.reset();

                    boolean enableNotificationRead = enableNotification(gatt, service.getUuid(), characteristicForRead.getUuid());
                    Log.d(TAG, "=====enableNotificationRead: " + enableNotificationRead);
                    boolean enableNotificationFlowControl = enableNotification(gatt, service.getUuid(), characteristicForFlowControl.getUuid());
                    Log.d(TAG, "=====enableNotificationFlowControl: " + enableNotificationFlowControl);

                    Device.this.connected = true;
                    if (onConnectChanged != null)
                        mainHandler.post(() -> onConnectChanged.accept(connected));
                }).start();
            }

            @SuppressLint("MissingPermission")
            public boolean enableNotification(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID) {
                boolean success = false;
                BluetoothGattService service = gatt.getService(serviceUUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = findNotifyCharacteristic(service, characteristicUUID);
                    if (characteristic != null) {
                        success = gatt.setCharacteristicNotification(characteristic, true);
                        if (success) {
                            // 来源：http://stackoverflow.com/questions/38045294/oncharacteristicchanged-not-called-with-ble
                            for (BluetoothGattDescriptor dp : characteristic.getDescriptors()) {
                                if (dp != null) {
                                    synchronized (DESCRIPTOR_WRITE_LOCK) {
                                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                            boolean setValue = dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                            Log.d(TAG, "=====setValue: " + characteristicUUID.toString() + " ENABLE_NOTIFICATION_VALUE ---> " + setValue);
                                        } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                            boolean setValue = dp.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                                            Log.d(TAG, "=====setValue: " + characteristicUUID.toString() + " ENABLE_INDICATION_VALUE ---> " + setValue);
                                        }
                                        success = gatt.writeDescriptor(dp);

                                        try {
                                            DESCRIPTOR_WRITE_LOCK.wait();
                                        } catch (InterruptedException ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return success;
            }

            private BluetoothGattCharacteristic findNotifyCharacteristic(BluetoothGattService service, UUID characteristicUUID) {
                BluetoothGattCharacteristic characteristic = null;
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic c : characteristics) {
                    if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                            && characteristicUUID.equals(c.getUuid())) {
                        characteristic = c;
                        break;
                    }
                }
                if (characteristic != null)
                    return characteristic;
                for (BluetoothGattCharacteristic c : characteristics) {
                    if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                            && characteristicUUID.equals(c.getUuid())) {
                        characteristic = c;
                        break;
                    }
                }
                return characteristic;
            }
        };

        private Device() {
        }

        public void connect() {
            // 权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // api >= 31
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    mainHandler.post(() -> Toast.makeText(mContext, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show());
                    return;
                }
            }

            if (Build.VERSION.SDK_INT >= 23) { // Android 6.0 (October 2015)
                gatt = device.connectGatt(mContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                gatt = device.connectGatt(mContext, false, gattCallback);
            }
        }

        public void disconnect() {
            // 权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // api >= 31
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    mainHandler.post(() -> Toast.makeText(mContext, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show());
                    return;
                }
            }

            gatt.disconnect();
        }

        public void write(byte[] data) {
            // 权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // api >= 31
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    mainHandler.post(() -> Toast.makeText(mContext, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show());
                    return;
                }
            }
            // 已发送的数据
            int count = 0;
            while (connected && count < data.length) {
                synchronized (dataFC) {
                    if (dataFC.getMtu() <= 0 || dataFC.getCredit() <= 0) {
                        // 令牌用尽，等待令牌
                        Log.d(TAG, "=====all credit are used, waiting for new credit...");
                        try {
                            dataFC.wait(500);
                        } catch (InterruptedException ignored) {
                        }

                        continue;
                    }
                }

                byte[] subData = Arrays.copyOfRange(data, count, Math.min(count + dataFC.getMtu() - 3, data.length)); // 取出MTU-3个数据
                if (subData.length == 0) break; // 发送完毕
                count = count + subData.length;

                dataFC.addCredit(-1);
                synchronized (CHARACTERISTIC_WRITE_LOCK) {
                    if (Build.VERSION.SDK_INT >= 33) { // Android 13 (August 2022)
                        gatt.writeCharacteristic(characteristicForWrite, subData, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    } else {
                        // set value
                        characteristicForWrite.setValue(subData);
                        // Write type
                        characteristicForWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        // Write Char
                        gatt.writeCharacteristic(characteristicForWrite);
                    }

                    try {
                        CHARACTERISTIC_WRITE_LOCK.wait(500);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Device device = (Device) o;
            return Objects.equals(address, device.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address);
        }
    }

    public static class DataFC {
        private final AtomicInteger mtu;
        private final AtomicInteger credit;

        public DataFC(int mtu, int credit) {
            this.mtu = new AtomicInteger(mtu);
            this.credit = new AtomicInteger(credit);
        }

        public void reset() {
            setMtu(0);
            setCredit(0);
        }

        public void update(byte[] value) {
            synchronized (DataFC.this) {
                if (value[0] == 2) {
                    setMtu(((value[2] & 0xff) << 8) + (value[1] & 0xff)); // MTU
                } else if (value[0] == 1) {
                    addCredit(value[1] & 0xff); // 令牌数量
                }
                DataFC.this.notifyAll();
            }
        }

        public int getMtu() {
            return mtu.get();
        }

        public void setMtu(int mtu) {
            this.mtu.set(mtu);
        }

        public int getCredit() {
            return credit.get();
        }

        public void setCredit(int credit) {
            this.credit.set(credit);
        }

        public void addCredit(int credit) {
            this.credit.addAndGet(credit);
        }

    }

    public static String bleIdToHexStr(int bleId) {
        return String.format("%08X", bleId);
    }

    public static int bleIdToInt(String bleId) {
        return Integer.parseInt(bleId.split("-")[0], 16);
    }

}

package com.lingmoyun.printerdemo;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lingmoyun.instruction.cpcl.CpclBuilder;
import com.lingmoyun.protocol.PrinterOrder;
import com.lingmoyun.util.BitmapUtils;
import com.lingmoyun.util.HexByteUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bluetooth Demo
 *
 * @author guoweifeng
 * @date 2021/1/22 17:57
 */
public class BluetoothDemoActivity extends AppCompatActivity {
    private static final String TAG = "BluetoothDemoActivity";
    private static final UUID UUID_OTHER_DEVICE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile Receiver mReceiver = new Receiver();
    private final List<Map<String, String>> mList = new ArrayList<>();
    private SimpleAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        findViewById(R.id.btn_bt_search).setOnClickListener(view -> searchBt());
        findViewById(R.id.btn_bt_read_info).setOnClickListener(view -> testReadInfo());
        findViewById(R.id.btn_bt_test).setOnClickListener(view -> testPrint());

        mAdapter = new SimpleAdapter(this, mList, R.layout.list_item, new String[]{"name", "mac"}, new int[]{R.id.tv_bt_name, R.id.tv_bt_mac});
        ((ListView) findViewById(R.id.lv_bt_device)).setAdapter(mAdapter);
        ((ListView) findViewById(R.id.lv_bt_device)).setOnItemClickListener((adapterView, view, position, itemId) -> ((EditText) findViewById(R.id.et_bt_mac)).setText(mList.get(position).get("mac")));

        initBt();
    }

    private void initBt() {
        XXPermissions.with(this)
                // 申请权限
                .permission(
                        Permission.BLUETOOTH_SCAN, // 蓝牙扫描权限
                        Permission.BLUETOOTH_CONNECT // 蓝牙连接权限
                )
                .request(new OnPermissionCallback() {

                    @Override
                    public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                        if (!allGranted) {
                            Toast.makeText(BluetoothDemoActivity.this, "获取权限成功，部分权限未正常授予", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        bluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                        if (!bluetoothAdapter.isEnabled()) {
                            // 蓝牙未开启
                            Toast.makeText(BluetoothDemoActivity.this, "请开启蓝牙", Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        // ACTION_FOUND
                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                        registerReceiver(mReceiver, filter);

                        // ACTION_DISCOVERY_FINISHED
                        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                        registerReceiver(mReceiver, filter);
                    }

                    @Override
                    public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                        if (doNotAskAgain) {
                            Toast.makeText(BluetoothDemoActivity.this, "被永久拒绝授权，请手动授予蓝牙权限", Toast.LENGTH_SHORT).show();
                            // 如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.startPermissionActivity(BluetoothDemoActivity.this, permissions);
                        } else {
                            Toast.makeText(BluetoothDemoActivity.this, "获取蓝牙权限失败", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                });
    }

    private void searchBt() {
        mList.clear();
        mAdapter.notifyDataSetChanged();

        // 权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // api >= 31
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "No Permission: " + Manifest.permission.BLUETOOTH_SCAN, Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (bluetoothAdapter.isDiscovering())
            bluetoothAdapter.cancelDiscovery();
        boolean b = bluetoothAdapter.startDiscovery();
        Toast.makeText(this, "Scan: " + b, Toast.LENGTH_LONG).show();
    }

    private void testReadInfo() {
        //((TextView) findViewById(R.id.tv_bt_log)).setText("正在打印。。。");
        // 打印机蓝牙MAC地址
        final String address = ((EditText) findViewById(R.id.et_bt_mac)).getText().toString().trim();
        new Thread(() -> {
            // 如果有已连接的蓝牙，尝试断开。
            closeBt();
            try {
                // 连接蓝牙
                final BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
                // 权限检查
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // api >= 31
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                bluetoothSocket = remoteDevice.createRfcommSocketToServiceRecord(UUID_OTHER_DEVICE);
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();

                // 监听蓝牙数据
                new Thread(() -> {
                    byte[] buffer = new byte[1024];
                    try {
                        while (inputStream != null && outputStream != null) {

                            if (inputStream.available() > 0) {
                                int len = inputStream.read(buffer);
                                byte[] data = new byte[len];
                                System.arraycopy(buffer, 0, data, 0, len);
                                Log.d(Thread.currentThread().getName(), "testPrint: read data(hex): " + HexByteUtils.byteArrayToHexStr(data));
                                String gbk = new String(data, "GBK");
                                Log.d(Thread.currentThread().getName(), "testPrint: read data: " + gbk);
                                //runOnUiThread(() -> ((TextView) findViewById(R.id.tv_bt_log)).setText(gbk));
                                runOnUiThread(() -> Toast.makeText(BluetoothDemoActivity.this, gbk, Toast.LENGTH_LONG).show());
                                runOnUiThread(() -> copyText(gbk));
                                break;
                            }
                        }

                        // 读到数据，断开蓝牙
                        closeBt();
                    } catch (IOException ignored) {
                    }


                }, "bt-" + address + "-read").start();

                byte[] bytes = PrinterOrder.getReadInfoOrder();
                outputStream.write(bytes);
                outputStream.flush();
                Log.d(Thread.currentThread().getName(), "testPrint: write data: " + bytes.length + " bytes.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "bt-" + address + "-connect").start();
    }

    /**
     * 测试打印
     * <p>
     * 打印机蓝牙支持BLE、SPP两种协议
     * 由于对接打印机核心在指令(CPCL指令)，所以本demo只使用SPP协议示例
     * 流程：0.连接蓝牙
     * 1.下发打印指令
     * 2.读取打印机回复
     * 3.断开蓝牙
     */
    private void testPrint() {
        Toast.makeText(BluetoothDemoActivity.this, "开始打印", Toast.LENGTH_SHORT).show();
        //((TextView) findViewById(R.id.tv_bt_log)).setText("正在打印。。。");
        // 打印机蓝牙MAC地址
        final String address = ((EditText) findViewById(R.id.et_bt_mac)).getText().toString().trim();
        new Thread(() -> {
            try {
                // 连接蓝牙
                final BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
                // 权限检查
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // api >= 31
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                bluetoothSocket = remoteDevice.createRfcommSocketToServiceRecord(UUID_OTHER_DEVICE);
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();

                // 监听蓝牙数据
                new Thread(() -> {
                    byte[] buffer = new byte[1024];
                    try {
                        while (inputStream != null && outputStream != null) {

                            if (inputStream.available() > 0) {
                                int len = inputStream.read(buffer);
                                byte[] data = new byte[len];
                                System.arraycopy(buffer, 0, data, 0, len);
                                Log.d(Thread.currentThread().getName(), "testPrint: read data(hex): " + HexByteUtils.byteArrayToHexStr(data));
                                Log.d(Thread.currentThread().getName(), "testPrint: read data: " + HexByteUtils.toString(data));
                                break;
                            }
                        }

                        // 读到数据后，断开蓝牙
                        closeBt();
                    } catch (IOException ignored) {
                    }


                }, "bt-" + address + "-read").start();

                Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream start===");
                final int dpi = 203; // 打印机DPI
                Bitmap src = BitmapFactory.decodeStream(getAssets().open("test_a4_300dpi.jpg"));
                // 缩放到203DPI A4大小 210mm*297mm
                Bitmap bitmap = BitmapUtils.scale(src, BitmapUtils.mm2px(210, dpi), BitmapUtils.mm2px(297, dpi));
                Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream finish===");
                /* =====构建指令================================================================ */
                Log.d(TAG, "testPrint: ===CpclBuilder start===");
                /** 构建CPCL 更多用法见{@link CPCLExample#example(Context)} */
                byte[] cpcl = CpclBuilder.createArea(0, dpi, bitmap.getHeight(), 1) // 其中高度填写纸张高度即可
                        //.taskId("1") // 任务ID，部分机型支持，这里传什么，打印结果就会携带什么，如果不需要打印结果注释这一行即可
                        // 固定写法，无需修改
                        .pageWidth(dpi == 203 ? 1728 : 2592)
                        // 推荐：图片指令-压缩，部分机型支持
                        .imageGG(0, 0, bitmap)
                        .formPrint()
                        .cut(0) // 切刀指令，切纸，无切刀机器不受影响
                        .build();
                Log.d(TAG, "testPrint: ===CpclBuilder finish===");
                /* =====构建指令================================================================ */
                // 释放图片资源
                src.recycle();
                bitmap.recycle();

                // write
                Log.d(TAG, "testPrint: ===outputStream.write start===");
                outputStream.write(cpcl);
                outputStream.flush();
                Log.d(TAG, "testPrint: ===outputStream.write finish===");
                Log.d(Thread.currentThread().getName(), "testPrint: write data: " + cpcl.length + " bytes.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "bt-" + address + "-connect").start();
    }

    /**
     * 断开蓝牙连接
     */
    private void closeBt() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            inputStream = null;
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            outputStream = null;
        }

        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            bluetoothSocket = null;
        }
    }

    /**
     * 断开蓝牙连接
     */
    private void copyText(String text) {
        //获取剪贴板管理器：
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // 创建普通字符型ClipData
        ClipData mClipData = ClipData.newPlainText("Label", text);
        // 将ClipData内容放到系统剪贴板里。
        cm.setPrimaryClip(mClipData);
    }

    private class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // 权限检查
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // api >= 31
                    if (ActivityCompat.checkSelfPermission(BluetoothDemoActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(BluetoothDemoActivity.this, "No Permission: " + Manifest.permission.BLUETOOTH_CONNECT, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                String name = device.getName();
                if (name != null && !name.isEmpty()) {
                    Map<String, String> map = new HashMap<>();
                    map.put("name", name);
                    map.put("mac", device.getAddress());
                    mList.add(map);
                    mAdapter.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // 扫描完成
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
package com.lingmoyun.printerdemo;

import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lingmoyun.bluetooth.BLE;
import com.lingmoyun.instruction.cpcl.CpclBuilder;
import com.lingmoyun.protocol.PrinterOrder;
import com.lingmoyun.util.BitmapUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * BluetoothLE Demo
 *
 * @author guoweifeng
 * @date 2021/1/22 17:57
 */
public class BluetoothLEDemoActivity extends AppCompatActivity {
    private static final String TAG = "BluetoothLEDemoActivity";
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
    //    private static final Executor EXECUTOR = Executors.newCachedThreadPool();
    private final List<BLE.Device> deviceList = new ArrayList<>();
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
        BLE.setContext(this);
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
                            Toast.makeText(BluetoothLEDemoActivity.this, "获取权限成功，部分权限未正常授予", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        if (!((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().isEnabled()) {
                            // 蓝牙未开启
                            Toast.makeText(BluetoothLEDemoActivity.this, "请开启蓝牙", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }

                    @Override
                    public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                        if (doNotAskAgain) {
                            Toast.makeText(BluetoothLEDemoActivity.this, "被永久拒绝授权，请手动授予蓝牙权限", Toast.LENGTH_SHORT).show();
                            // 如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.startPermissionActivity(BluetoothLEDemoActivity.this, permissions);
                        } else {
                            Toast.makeText(BluetoothLEDemoActivity.this, "获取蓝牙权限失败", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                });
    }

    private void searchBt() {
        deviceList.clear();
        mList.clear();
        mAdapter.notifyDataSetChanged();

        BLE.scan(device -> {
            String name = device.getName();
            if (name != null && !name.isEmpty() && !deviceList.contains(device)) {
                Map<String, String> map = new HashMap<>();
                map.put("name", device.getName());
                map.put("mac", device.getAddress());
                deviceList.add(device);
                mList.add(map);
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    private void testReadInfo() {
        // 打印机蓝牙MAC地址
        final String address = ((EditText) findViewById(R.id.et_bt_mac)).getText().toString().trim();
        final BLE.Device device = getDeviceByAddress(address);
        if (device == null) {
            searchBt();
            return;
        }
        device.onConnectChanged = isConnected -> {
            synchronized (device) {
                device.notifyAll();
            }
        };
        final List<String> resJsons = new ArrayList<>();
        final List<Byte> buf = new ArrayList<>();
        device.onValueChanged = value -> {
            // *****粘包处理开始**************************************************************************
            for (byte b : value) {
                if (!buf.isEmpty()) {
                    buf.add(b);
                }
                if (b == ((byte) 0x7B)) { // '{'
                    buf.clear();
                    buf.add(b);
                }
                if (b == ((byte) 0x7D)) { // '}'
                    try {
                        String jsonStr = new String(convert(buf), "GBK");
                        resJsons.add(jsonStr);
                    } catch (UnsupportedEncodingException ignored) {
                    }
                    buf.clear();
                }
            }
            // *****粘包处理结束**************************************************************************

            for (String resJson : resJsons) {
                Toast.makeText(BluetoothLEDemoActivity.this, resJson, Toast.LENGTH_LONG).show();
            }
            resJsons.clear();

            // 断开连接
            device.onConnectChanged = null;
            device.disconnect();
        };
        EXECUTOR.execute(() -> {
            // wait for connected
            synchronized (device) {
                if (!device.isConnected()) {
                    device.connect();
                    try {
                        device.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            byte[] bytes = PrinterOrder.getReadInfoOrder();
            device.write(bytes);
        });
    }

    /**
     * 测试打印
     */
    private void testPrint() {
        Toast.makeText(BluetoothLEDemoActivity.this, "开始打印", Toast.LENGTH_SHORT).show();
        //((TextView) findViewById(R.id.tv_bt_log)).setText("正在打印。。。");
        // 打印机蓝牙MAC地址
        final String address = ((EditText) findViewById(R.id.et_bt_mac)).getText().toString().trim();
        final BLE.Device device = getDeviceByAddress(address);
        if (device == null) {
            searchBt();
            return;
        }
        device.onConnectChanged = isConnected -> {
            if (isConnected) {
                synchronized (device) {
                    device.notifyAll();
                }
            }
        };
        device.onValueChanged = value -> {
            try {
                String gbk = new String(value, "GBK");
                Toast.makeText(BluetoothLEDemoActivity.this, gbk, Toast.LENGTH_LONG).show();
                Log.d(TAG, "testPrint: read data: " + gbk);
            } catch (UnsupportedEncodingException ignored) {
            }
        };
        EXECUTOR.execute(() -> {
            try {
                Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream start===");
                final int dpi = 203; // 打印机DPI
                //Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open("test_a4_300dpi.jpg"));
                Bitmap src = BitmapFactory.decodeStream(getAssets().open("test_a4_300dpi.jpg"));
                // 缩放到203DPI A4大小 210mm*297mm
                Bitmap bitmap = BitmapUtils.scale(src, BitmapUtils.mm2px(210, dpi), BitmapUtils.mm2px(297, dpi));
                Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream finish===");
                /* =====构建指令================================================================ */
                Log.d(TAG, "testPrint: ===CpclBuilder start===");
                /** 构建CPCL 更多用法见{@link CPCLExample#example(Context)} */
                byte[] cpcl = CpclBuilder.createArea(0, dpi, bitmap.getHeight(), 1) // 其中高度填写纸张高度即可
                         .taskId("1") // 任务ID，部分机型支持，这里传什么，打印结果就会携带什么，如果不需要打印结果注释这一行即可
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
                // src.recycle();
                bitmap.recycle();

                // wait for connected
                synchronized (device) {
                    if (!device.isConnected()) {
                        device.connect();
                        try {
                            device.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }

                // write
                Log.d(TAG, "testPrint: ===outputStream.write start===");
                device.write(cpcl);
                Log.d(TAG, "testPrint: ===outputStream.write finish===");
                Log.d(TAG, "testPrint: write data: " + cpcl.length + " bytes.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private BLE.Device getDeviceByAddress(String address) {
        for (BLE.Device device : deviceList) {
            if (address.equals(device.getAddress())) return device;
        }
        return null;
    }

    public static byte[] convert(List<Byte> byteList) {
        byte[] byteArray = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            byteArray[i] = byteList.get(i);
        }
        return byteArray;
    }

}
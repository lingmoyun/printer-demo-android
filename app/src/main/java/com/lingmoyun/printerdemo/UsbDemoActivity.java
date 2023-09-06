package com.lingmoyun.printerdemo;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.lingmoyun.instruction.cpcl.CPCL;
import com.lingmoyun.instruction.cpcl.CpclBuilder;
import com.lingmoyun.usb.LmyUsbPrinter;
import com.lingmoyun.util.BitmapUtils;

import java.io.IOException;
import java.util.List;

/**
 * USB Demo
 *
 * @author guoweifeng
 * @date 2021/1/22 17:57
 */
public class UsbDemoActivity extends AppCompatActivity {
    private static final String TAG = "UsbDemoActivity";
    private static final Object USB_PERMISSION_LOCK = new Object();
    // USB权限广播接收器
    // 参考官方文档：https://developer.android.google.cn/guide/topics/connectivity/usb/host?hl=zh_cn#permission-d
    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LmyUsbPrinter.ACTION_USB_PERMISSION.equals(action)) {
                synchronized (USB_PERMISSION_LOCK) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    // 部分手机拿到的结果是null

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // 部分手机拿到的结果是false
                        if (device != null) {
                            //call method to set up device communication
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                    USB_PERMISSION_LOCK.notifyAll();
                }
            }
        }
    };
    // USB连接与断开广播接收器
    // 参考官方文档：https://developer.android.google.cn/guide/topics/connectivity/usb/host?hl=zh_cn#discovering-d
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {

                }
                runOnUiThread(() -> Toast.makeText(UsbDemoActivity.this, "监听到USB打印机已连接", Toast.LENGTH_SHORT).show());
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // call your method that cleans up and closes communication with the device
                }
                runOnUiThread(() -> Toast.makeText(UsbDemoActivity.this, "监听到USB打印机已断开", Toast.LENGTH_SHORT).show());
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb);

        findViewById(R.id.btn_usb_test).setOnClickListener(view -> testPrint());

        // 注册USB权限广播接收器
        registerReceiver(usbPermissionReceiver, LmyUsbPrinter.FILTER_USB_PERMISSION);
        // 注册USB连接与断开广播接收器
        registerReceiver(usbReceiver, LmyUsbPrinter.FILTER_USB);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消注册USB权限广播接收器
        unregisterReceiver(usbPermissionReceiver);
        // 取消注册USB连接与断开广播接收器
        unregisterReceiver(usbReceiver);
    }

    /**
     * 测试打印
     * <p>
     * 流程：0.连接USB
     * 1.下发打印指令
     * 2.读取打印机回复
     * 3.断开USB
     */
    private void testPrint() {
        new Thread(() -> {
            try {
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

                // Find all available drivers from attached devices.
                List<UsbSerialDriver> availableDrivers = LmyUsbPrinter.findDrivers(usbManager);
                if (availableDrivers.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(UsbDemoActivity.this, "未发现可用USB打印机", Toast.LENGTH_LONG).show());
                    return;
                }

                // Open a connection to the first available driver.
                UsbSerialDriver driver = availableDrivers.get(0);
                if (!usbManager.hasPermission(driver.getDevice())) {
                    // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
                    // 参考官方文档：https://developer.android.google.cn/guide/topics/connectivity/usb/host?hl=zh_cn#permission-d
                    PendingIntent pendingIntent;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        pendingIntent = PendingIntent.getBroadcast(this, 0, LmyUsbPrinter.INTENT_USB_PERMISSION, PendingIntent.FLAG_IMMUTABLE);
                    } else {
                        pendingIntent = PendingIntent.getBroadcast(this, 0, LmyUsbPrinter.INTENT_USB_PERMISSION, 0);
                    }
                    usbManager.requestPermission(driver.getDevice(), pendingIntent);

                    // 等待授权结果
                    synchronized (USB_PERMISSION_LOCK) {
                        try {
                            USB_PERMISSION_LOCK.wait(10 * 1000);// 根据实际情况自行调整超时时间
                        } catch (InterruptedException ignored) {
                        }
                        // 授权广播结果在部分手机上返回false，这里使用UsbManager.hasPermission再次校验权限即可
                        if (!usbManager.hasPermission(driver.getDevice())) {
                            runOnUiThread(() -> Toast.makeText(UsbDemoActivity.this, "USB未授权", Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                }
                UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());

                UsbSerialPort usbSerialPort = driver.getPorts().get(0); // // Most devices have just one usbSerialPort (usbSerialPort 0)

                if (usbSerialPort.isOpen()) {
                    usbSerialPort.close();
                }
                // 连接USB
                usbSerialPort.open(connection);

                /*
                SerialInputOutputManager usbIoManager = new SerialInputOutputManager(usbSerialPort, new SerialInputOutputManager.Listener() {
                    @Override
                    public void onNewData(byte[] data) {
                        Log.d(TAG, "onNewData: " + new String(data));
                    }

                    @Override
                    public void onRunError(Exception e) {
                        Log.e(TAG, "onRunError", e);
                    }
                });
                 */

                // 切换指令集，USB默认TSPL指令集，开机后发送一次即可，重启后失效
                // 切换TSPL模式
                //printerClass.write(new byte[]{0x1d, 0x49, 0x60, 0x00});
                // 切换CPCL模式
                usbSerialPort.write(new byte[]{0x1d, 0x49, 0x60, 0x01}, Integer.MAX_VALUE);
                byte[] buf = new byte[10];
                int read = usbSerialPort.read(buf, 5 * 1000);
                byte[] changeMode = new byte[read];
                System.arraycopy(buf, 0, changeMode, 0, read);
                Log.d(TAG, "change mode: " + new String(changeMode));// 输出：dbg=1
                //usbIoManager.writeAsync(new byte[]{0x1d, 0x49, 0x60, 0x01});

                Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream===");
                Bitmap imageBB = BitmapFactory.decodeStream(getAssets().open("bb.jpeg"));
                Log.d(TAG, "testPrint: ===imageBB===");
                Bitmap imageTest = BitmapFactory.decodeStream(getAssets().open("test.jpg"));
                Log.d(TAG, "testPrint: ===imageTest===");
                Bitmap imageTestFloydSteinberg = BitmapUtils.floydSteinberg(imageTest);
                Log.d(TAG, "testPrint: ===imageTestFloydSteinberg===");
                /* =====构建指令================================================================ */
                Log.d(TAG, "testPrint: ===CpclBuilder start===");
                //byte[] cpcl = CpclBuilder.createAreaSize(0, 1680, 2374, 1) // Deprecated
                // 灵活构建
                //byte[] cpcl = CpclBuilder.newBuilder()
                //        .area(0, 203, 2374, 1)
                //        .pageWidth(1680) // 可选
                // 快捷构建
                //byte[] cpcl = CpclBuilder.createArea(0, 300, 2480, 3508, 1) // 300DPI
                byte[] cpcl = CpclBuilder.createArea(0, 203, 1680, 2374, 1) // 203DPI
                        // .taskId("1") // 这里传什么，打印结果就会携带什么，如果不需要打印结果注释这一行即可
                        .text(0, 0, 500, 100, "Hello World!")
                        .text(0, 0, 520, 150, "Hello World!")
                        .text(0, 0, 540, 200, "Hello World!")
                        .text(0, 0, 560, 250, "Hello World!")
                        .text(0, 0, 580, 300, "Hello World!")
                        .line(100, 100, 300, 100, 1)
                        .line(100, 100, 300, 300, 2)
                        .line(new Point(100, 100), new Point(100, 300), 3)
                        .barCode(1, 1, 100, 100, 400, "A43009200005")
                        .text(0, 0, 160, 510, "A43009200005")
                        .qrCode(100, 600, "http://open.lingmoyun.com")
                        .qrCode(CPCL.QR_CODE_ECC_Q, 500, 600, "http://open.lingmoyun.com")
                        // 图片指令，通用
                        .imageCG(100, 800, imageBB) // 默认 128
                        .imageCG(400, 800, imageBB, 200) // 颜色转换阈值200，取值范围0-255，值越大黑色越多
                        // 图片指令-压缩，部分机型支持
                        .imageGG(700, 800, imageBB, 200) // 颜色转换阈值200，取值范围0-255，值越小白色越多
                        .imageGG(0, 1100, imageTest) // 两种效果，根据实际打印效果自行选择
                        .imageGG(820, 1100, imageTestFloydSteinberg) // 两种效果，根据实际打印效果自行选择，使用floydSteinberg算法对图片二值化
                        // 手动拼接指令，下面三种方式等同
                        .appendln("BARCODE QR " + 500 + " " + 600 + " M 2 U 6" + "\n" + "Q" + "A," + "http://open.lingmoyun.com" + "\n" + "ENDQR")
                        // .append("BARCODE QR " + 500 + " " + 600 + " M 2 U 6" + "\n" + "Q" + "A," + "http://open.lingmoyun.com" + "\n" + "ENDQR" + "\n")
                        // .append(("BARCODE QR " + 500 + " " + 600 + " M 2 U 6" + "\n" + "Q" + "A," + "http://open.lingmoyun.com" + "\n" + "ENDQR" + "\n").getBytes("GBK"))
                        .form()
                        .print()
                        .cut(0) // 切刀指令，切纸，无切刀机器不受影响
                        .build();
                Log.d(TAG, "testPrint: ===CpclBuilder finish===");
                /* =====构建指令================================================================ */
                // 释放图片资源
                imageBB.recycle();
                imageTest.recycle();
                imageTestFloydSteinberg.recycle();
                // if (cpcl == null) return; // 不会发生

                runOnUiThread(() -> Toast.makeText(UsbDemoActivity.this, "开始打印", Toast.LENGTH_SHORT).show());
                // write
                Log.d(TAG, "testPrint: ===outputStream.write start===");
                usbSerialPort.write(cpcl, Integer.MAX_VALUE);
                //usbIoManager.writeAsync(cpcl);
                Log.d(TAG, "testPrint: ===outputStream.write finish===");
                Log.d(Thread.currentThread().getName(), "testPrint: write data: " + cpcl.length + " bytes.");

                // read 读取打印结果
                //byte[] printResult = printerClass.read(128, 5 * 1000);
                //Log.d(TAG, "testPrintResult: " + new String(printResult));

                // 关闭USB
                usbSerialPort.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "usb-connect").start();
    }

}
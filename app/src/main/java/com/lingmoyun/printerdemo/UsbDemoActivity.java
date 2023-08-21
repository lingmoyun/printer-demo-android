package com.lingmoyun.printerdemo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lingmoyun.instruction.cpcl.CPCL;
import com.lingmoyun.instruction.cpcl.CpclBuilder;
import com.lingmoyun.usb.LmyPrinter;
import com.lingmoyun.usb.PrinterClass;
import com.lingmoyun.usb.USBService;
import com.lingmoyun.util.BitmapUtils;

import java.io.IOException;

/**
 * USB Demo
 *
 * @author guoweifeng
 * @date 2021/1/22 17:57
 */
public class UsbDemoActivity extends AppCompatActivity {
    private static final String TAG = "UsbDemoActivity";

    private PrinterClass printerClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb);

        findViewById(R.id.btn_usb_test).setOnClickListener(view -> testPrint());

        init();
    }

    private void init() {
        // 选择打印机型号
        LmyPrinter lmyPrinter = LmyPrinter.A4G;
        printerClass = new USBService(this, lmyPrinter.USB_PID, lmyPrinter.USB_VID);
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
//        UsbDevice device;
//        UsbDeviceConnection usbConnection;
//
//        UsbSerialDevice serial = UsbSerialDevice.createUsbSerialDevice(device, usbConnection);

        Toast.makeText(UsbDemoActivity.this, "开始打印", Toast.LENGTH_SHORT).show();
        //((TextView) findViewById(R.id.tv_bt_log)).setText("正在打印。。。");
        new Thread(() -> {
            try {
                // 连接USB
                boolean openResult = printerClass.open();
                if (!openResult) {
                    Toast.makeText(UsbDemoActivity.this, "Failed to open usb", Toast.LENGTH_LONG).show();
                    return;
                }

                // 切换指令集，USB默认TSPL指令集，开机后发送一次即可，重启后失效
                // 切换TSPL模式
                //printerClass.write(new byte[]{0x1d, 0x49, 0x60, 0x00});
                // 切换CPCL模式
                printerClass.write(new byte[]{0x1d, 0x49, 0x60, 0x01});
                byte[] changeMode = printerClass.read(128, 5 * 1000);
                Log.d(TAG, "change mode: " + new String(changeMode));

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

                // write
                Log.d(TAG, "testPrint: ===outputStream.write start===");
                printerClass.write(cpcl);
                Log.d(TAG, "testPrint: ===outputStream.write finish===");
                Log.d(Thread.currentThread().getName(), "testPrint: write data: " + cpcl.length + " bytes.");

                // read 读取打印结果
                //byte[] printResult = printerClass.read(128, 5 * 1000);
                //Log.d(TAG, "testPrintResult: " + new String(printResult));

                // 关闭USB
                printerClass.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "usb-connect").start();
    }

}
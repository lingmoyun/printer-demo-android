package com.lingmoyun.printerdemo;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.util.Log;

import com.lingmoyun.instruction.cpcl.CPCL;
import com.lingmoyun.instruction.cpcl.CpclBuilder;
import com.lingmoyun.util.BitmapUtils;

import java.io.IOException;

/**
 * CPCL Example
 *
 * @author guoweifeng
 * @date 2021/1/22 17:57
 */
public class CPCLExample {
    private static final String TAG = "CPCLExample";

    /**
     * CPCL SDK 使用示例
     *
     * @param context context
     * @return CPCL
     * @throws IOException read image err
     */
    public static byte[] example(Context context) throws IOException {
        Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream===");
        Bitmap imageBB = BitmapFactory.decodeStream(context.getAssets().open("bb.jpeg"));
        Log.d(TAG, "testPrint: ===imageBB===");
        Bitmap imageTest = BitmapFactory.decodeStream(context.getAssets().open("test.jpg"));
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
                // .taskId("1") // 任务ID，部分机型支持，这里传什么，打印结果就会携带什么，如果不需要打印结果注释这一行即可
                // 文本指令
                .text(0, 0, 500, 100, "Hello World!")
                .text(0, 0, 520, 150, "Hello World!")
                .text(0, 0, 540, 200, "Hello World!")
                .text(0, 0, 560, 250, "Hello World!")
                .text(0, 0, 580, 300, "Hello World!")
                // 直线指令
                .line(100, 100, 300, 100, 1)
                .line(100, 100, 300, 300, 2)
                .line(new Point(100, 100), new Point(100, 300), 3)
                // 条形码128指令
                .barCode(1, 1, 100, 100, 400, "A43009200005")
                .text(0, 0, 160, 510, "A43009200005")
                // 二维码指令
                .qrCode(100, 600, "http://open.lingmoyun.com")
                .qrCode(CPCL.QR_CODE_ECC_Q, 500, 600, "http://open.lingmoyun.com")
                // 图片指令，通用
                .imageCG(100, 800, imageBB) // 默认 128
                .imageCG(400, 800, imageBB, 200) // 颜色转换阈值200，取值范围0-255，值越大黑色越多
                // 推荐：图片指令-压缩，部分机型支持
                .imageGG(700, 800, imageBB, 200) // 颜色转换阈值200，取值范围0-255，值越小白色越多
                .imageGG(0, 1100, imageTest) // 两种效果，根据实际打印效果自行选择
                .imageGG(820, 1100, imageTestFloydSteinberg) // 两种效果，根据实际打印效果自行选择，使用floydSteinberg算法对图片二值化
                // 手动拼接指令，下面三种方式等同
                .appendln("BARCODE QR " + 500 + " " + 600 + " M 2 U 6" + "\n" + "Q" + "A," + "http://open.lingmoyun.com" + "\n" + "ENDQR")
                // .append("BARCODE QR " + 500 + " " + 600 + " M 2 U 6" + "\n" + "Q" + "A," + "http://open.lingmoyun.com" + "\n" + "ENDQR" + "\n")
                // .append(("BARCODE QR " + 500 + " " + 600 + " M 2 U 6" + "\n" + "Q" + "A," + "http://open.lingmoyun.com" + "\n" + "ENDQR" + "\n").getBytes("GBK"))
                .form()
                .print()
                //.formPrint() // 等价于 form().print()
                .cut(0) // 切刀指令，切纸，无切刀机器不受影响
                .build();
        Log.d(TAG, "testPrint: ===CpclBuilder finish===");
        /* =====构建指令================================================================ */

        // 释放图片资源
        imageBB.recycle();
        imageTest.recycle();
        imageTestFloydSteinberg.recycle();

        return cpcl;
    }

}

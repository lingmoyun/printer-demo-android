package com.lingmoyun.protocol;

import java.io.ByteArrayOutputStream;

public class PrinterOrder {
    public static final int COMPRESS_MODE_MINILZO = 0x00;
    public static final int COMPRESS_MODE_ORIGIN = 0x01;

    /**
     * 压缩指令：
     * 12 4C mode adrH adrM adrL nH nM nL dat[1]....dat[n]
     * 增加了一个mode， 目前你填0，方便后续扩展用途。
     * addr为数据偏移地址， 从0开始， 共3字节（高位在前）。
     * n为数据长度， 其他同上。
     * 蓝牙lzo压缩数据结束指令：
     * 12 4C  00  00 00 00  00 00 00
     * 这个指令返回：
     * 12 4C mode sta  adrH adrM adrL.
     * sta为状态： 0=正常， 1=忙（数据会丢弃）
     */
    public static byte[][] compress(byte[] origin, int mode, Function<byte[], byte[]> compressFunction) {
        byte[] compressed = compressFunction.apply(origin);
        int n = compressed.length;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x12);
        baos.write(0x4C);
        baos.write(mode);//mode
        baos.write(0x00);//adrH
        baos.write(0x00);//adrM
        baos.write(0x00);//adrL
        baos.write((n & 0xff0000) >> 16);//nH
        baos.write((n & 0x00ff00) >> 8);//nM
        baos.write(n & 0x0000ff);//nL

        baos.write(compressed, 0, compressed.length);

        byte[][] cmd = new byte[2][];
        cmd[0] = baos.toByteArray();// 数据包
        cmd[1] = new byte[]{0x12, 0x4C, (byte) mode, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};// 结束指令
        return cmd;
    }

    /**
     * 设置Wi-Fi指令
     *
     * @param ssid     Wi-Fi SSID
     * @param password Wi-Fi 密码
     * @return 指令
     */
    public static byte[] getSetWiFiOrder(String ssid, String password) {
        byte[] ssidOrder = getOrder(ConfigKey.WIFI_SSID, ssid);
        byte[] passwordOrder = getOrder(ConfigKey.WIFI_PSW, password);
        byte[] saveOrder = getSaveOrder();
        byte[] powerResetOrder = getPowerResetOrder();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ssidOrder, 0, ssidOrder.length);
        baos.write(passwordOrder, 0, passwordOrder.length);
        baos.write(saveOrder, 0, saveOrder.length);
        baos.write(powerResetOrder, 0, powerResetOrder.length);
        return baos.toByteArray();
    }

    // 保存设置指令
    public static byte[] getSaveOrder() {
        // 固定值，直接返回
        //1F57150030020F0000007265675F76616C75655F7361766500
        return new byte[]{31, 87, 21, 0, 48, 2, 15, 0, 0, 0, 114, 101, 103, 95, 118, 97, 108, 117, 101, 95, 115, 97, 118, 101, 0};
    }

    // 重启指令
    public static byte[] getPowerResetOrder() {
        // 固定值，直接返回
        //1F57120030020C000000706F7765725F726573657400
        return new byte[]{31, 87, 18, 0, 48, 2, 12, 0, 0, 0, 112, 111, 119, 101, 114, 95, 114, 101, 115, 101, 116, 0};
    }

    // 读取信息指令
    public static byte[] getReadInfoOrder() {
        // 固定值，直接返回
        //1F57100030020A000000524541445F494E464F00
        return new byte[]{0x1F, 0x57, 0x10, 0x00, 0x30, 0x02, 0x0A, 0x00, 0x00, 0x00, 0x52, 0x45, 0x41, 0x44, 0x5F, 0x49, 0x4E, 0x46, 0x4F, 0x00};
    }

    //1F57 + 总长度(2字节) + 3001 + key长度(2字节) + value长度(2字节) + key + 00 + value + 00
    public static byte[] getOrder(ConfigKey key, String value) {
        if (key == null || value == null) {
            throw new NullPointerException("key: " + key + "; value: " + value);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] keyBytes = key.name().getBytes();
        int keyLen = keyBytes.length + 1;
        byte[] valueBytes = value.getBytes();
        int valueLen = valueBytes.length + 1;
        int totalLen = keyLen + valueLen + 6;

        baos.write(0x1f);
        baos.write(0x57);
        baos.write(totalLen & 0x00ff); // 总长度(2字节)，低位
        baos.write((totalLen & 0xff00) >> 8); // 总长度(2字节)，高位
        baos.write(0x30);
        baos.write(0x01);
        baos.write(keyLen & 0x00ff); // key长度(2字节)，低位
        baos.write((keyLen & 0xff00) >> 8); // key长度(2字节)，高位
        baos.write(valueLen & 0x00ff); // value长度(2字节)，低位
        baos.write((valueLen & 0xff00) >> 8); // value长度(2字节)，高位
        baos.write(keyBytes, 0, keyBytes.length); // key
        baos.write(0x00); // key end
        baos.write(valueBytes, 0, valueBytes.length); // value
        baos.write(0x00); // value end
        return baos.toByteArray();
    }

    /// Key of the config.
    public enum ConfigKey {
        WIFI_SSID, WIFI_PSW,
    }

}

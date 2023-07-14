package com.lingmoyun.protocol;


import com.lingmoyun.util.HexByteUtils;

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
        cmd[1] = new byte[]{0x12, 0x4C, (byte) mode, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};// 结束指令，开始升级
        return cmd;
    }

    /**
     * 质检页，等价于打印机上的测试页
     */
    public static byte[] testPageCmd() {
        return new byte[]{0x12, 0x54};
    }

    //设置Wi-Fi SSID指令
    public static byte[] getSetWifiSsidOrder(String ssid) {
        return getOrderBytes(ConfigKey.WIFI_SSID, ssid);
    }

    //设置Wi-Fi密码指令
    public static byte[] getSetWifiPwdOrder(String pwd) {
        return getOrderBytes(ConfigKey.WIFI_PSW, pwd);
    }

    //保存设置指令
    public static byte[] getSaveOrder() {
        //return _getOrderBytes(ConfigAdminKey.reg_value_save);
        // 固定值，直接返回
        //1F57150030020F0000007265675F76616C75655F7361766500
        return new byte[]{31, 87, 21, 0, 48, 2, 15, 0, 0, 0, 114, 101, 103, 95, 118, 97, 108, 117, 101, 95, 115, 97, 118, 101, 0};
    }

    //保存设置指令(hex)
    public static String getSaveOrderHex() {
        //return getOrder(ConfigAdminKey.reg_value_save);
        // 固定值，直接返回
        return "1F57150030020F0000007265675F76616C75655F7361766500";
    }

    //重启指令
    public static byte[] getPowerResetOrder() {
        //return _getOrderBytes(ConfigAdminKey.power_reset);
        // 固定值，直接返回
        //1F57120030020C000000706F7765725F726573657400
        return new byte[]{31, 87, 18, 0, 48, 2, 12, 0, 0, 0, 112, 111, 119, 101, 114, 95, 114, 101, 115, 101, 116, 0};
    }

    //重启指令(hex)
    public static String getPowerResetOrderHex() {
        //return getOrder(ConfigAdminKey.power_reset);
        // 固定值，直接返回
        return "1F57120030020C000000706F7765725F726573657400";
    }

    static byte[] getOrderBytes(Enum<?> key) {
        return HexByteUtils.hexStrToByteArray(getOrder(key));
    }

    static byte[] getOrderBytes(Enum<?> key, String value) {
        return HexByteUtils.hexStrToByteArray(getOrder(key, value));
    }

    //1F57 + 总长度(2字节) + 3001/3002 + key长度(2字节) + value长度(2字节) + key + value
    public static String getOrder(Enum<?> key) {
        return getOrder(key, null);
    }

    //1F57 + 总长度(2字节) + 3001/3002 + key长度(2字节) + value长度(2字节) + key + value
    public static String getOrder(Enum<?> key, String value) {
        String type;
        if (key instanceof ConfigKey) type = "3001";
        else if (key instanceof ConfigAdminKey) type = "3002";
        else return null;
        return getOrder(type, key.name(), value);
    }

    //1F57 + 总长度(2字节) + 3001 + key长度(2字节) + value长度(2字节) + key + value
    static String getOrder(String type, String key, String value) {
        int keyLen = key == null ? 0 : key.getBytes().length + 1;
        int valueLen = value == null ? 0 : value.getBytes().length + 1;
        int totalLen = keyLen + valueLen + 6;
        final String totalLenHex = _toTwoByteHexLH(totalLen);
        String keyLenHex = _toTwoByteHexLH(keyLen);
        String valueLenHex = _toTwoByteHexLH(valueLen);
        String keyHex = key == null ? "" : (HexByteUtils.byteArrayToHexStr(key.getBytes()) + "00");
        String valueHex = value == null ? "" : (HexByteUtils.byteArrayToHexStr(value.getBytes()) + "00");
        return "1F57" + totalLenHex + type + keyLenHex + valueLenHex + keyHex + valueHex;
    }

    //数字转成2字节的16进制，低位在前，高位在后
    static String _toTwoByteHexLH(int i) {
        return String.format("%02x", i & 0x00ff) + String.format("%02x", (i & 0xff00) >> 8);
    }

    /// Key of the config.
    public enum ConfigKey {
        WIFI_SSID, WIFI_PSW,
        DENSITY, // 浓度 0 1 2 3 4 5 6 7 8 9
    }

    public enum ConfigAdminKey {reg_value_save, power_reset, READ_INFO, PAPER_STUDY}
}

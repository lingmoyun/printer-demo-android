package com.lingmoyun.util;

/**
 * Hex Byte相互转换工具.
 *
 * @author winfordguo@gmail.com
 * @date 2019/10/29 15:29
 */
public class HexByteUtils {
    private static final int HEX = 16;
    public static final String CHARSET_NAME = "gbk";

    /**
     * 二进制数组转换成十六进制字符串
     *
     * @param byteArray 二进制数组
     * @return 十六进制
     */
    public static String byteArrayToHexStr(byte[] byteArray) {
        if (byteArray == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (byte b : byteArray)
            builder.append(String.format("%02X", (b & 0xFF)));
        return builder.toString().toUpperCase();
    }

    /**
     * 十六进制字符串还原成二进制数组
     *
     * @param hexStr 十六进制字符串
     * @return 二进制数组
     */
    public static byte[] hexStrToByteArray(String hexStr) {
        if (hexStr == null) {
            return null;
        }
        if (hexStr.length() == 0) {
            return new byte[0];
        }
        byte[] byteArray = new byte[hexStr.length() / 2];
        for (int i = 0; i < byteArray.length; i++) {
            String subStr = hexStr.substring(2 * i, 2 * i + 2);
            byteArray[i] = ((byte) Integer.parseInt(subStr, HEX));
        }
        return byteArray;
    }


}

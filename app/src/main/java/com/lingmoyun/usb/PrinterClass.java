package com.lingmoyun.usb;

/**
 * 打印机操作类
 *
 * @author guoweifeng
 * @date 2022/6/24 18:22
 */
public interface PrinterClass {

    boolean open();

    boolean close();

    boolean isOpen();

    boolean write(byte[] data);

    byte[] read(int maxLength, int timeout);

}

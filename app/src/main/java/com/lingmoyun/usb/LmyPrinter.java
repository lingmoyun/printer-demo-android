package com.lingmoyun.usb;

/**
 * 零墨云旗下打印机
 *
 * @author guoweifeng
 * @date 2023/8/21 10:57
 */
public enum LmyPrinter {
    A4(0xA408, 0xA48C),
    A4P(0xA408, 0xA48C),
    A4Y(0xA408, 0xA48C),
    A4S(0xA408, 0xA48C),
    M4S(0xA408, 0xA48C),
    /* ===== 分割线 ==================== */
    A4B(0x5840, 0x0483),
    A4H(0x5840, 0x0483),
    A4G(0x5840, 0x0483),
    A4X(0x5840, 0x0483),
    M4(0x5840, 0x0483),
    A41(0x5840, 0x0483),
    M41(0x5840, 0x0483),
    ;

    public final int USB_PID;
    public final int USB_VID;

    LmyPrinter(int USB_PID, int USB_VID) {
        this.USB_PID = USB_PID;
        this.USB_VID = USB_VID;
    }
}

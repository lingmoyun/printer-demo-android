package com.lingmoyun.usb;

import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

/**
 * 零墨云旗下打印机
 *
 * @author guoweifeng
 * @date 2023/8/21 10:57
 */
public enum LmyUsbPrinter {
    A4(0xA48C, 0xA408),
    A4P(0xA48C, 0xA408),
    A4Y(0xA48C, 0xA408),
    A4S(0xA48C, 0xA408),
    M4S(0xA48C, 0xA408),
    /* ===== 分割线 ==================== */
    A4B(0x0483, 0x5840),
    A4H(0x0483, 0x5840),
    A4G(0x0483, 0x5840),
    A4X(0x0483, 0x5840),
    M4(0x0483, 0x5840),
    A41(0x0483, 0x5840),
    M41(0x0483, 0x5840),
    ;

    public final int VID;
    public final int PID;

    LmyUsbPrinter(int VID, int PID) {
        this.VID = VID;
        this.PID = PID;
    }

    public static final String ACTION_USB_PERMISSION = "com.lingmoyun.USB_PERMISSION";
    public static final Intent INTENT_USB_PERMISSION = new Intent(ACTION_USB_PERMISSION);
    public static final IntentFilter FILTER_USB_PERMISSION = new IntentFilter(ACTION_USB_PERMISSION);

    /**
     * Finds and builds all possible {@link UsbSerialDriver UsbSerialDrivers}
     * from the currently-attached {@link UsbDevice} hierarchy. This method does
     * not require permission from the Android USB system, since it does not
     * open any of the devices.
     *
     * @param usbManager usb manager
     * @return a list, possibly empty, of all compatible drivers
     */
    public static List<UsbSerialDriver> findDrivers(final UsbManager usbManager) {
        return getDefaultProber().findAllDrivers(usbManager);
    }

    public static UsbSerialProber getDefaultProber() {
        return new UsbSerialProber(getDefaultProbeTable());
    }

    public static ProbeTable getDefaultProbeTable() {
        final ProbeTable probeTable = new ProbeTable();
        probeTable.addProduct(A4P.VID, A4P.PID, LmyUsbPrinterDriver.class);
        probeTable.addProduct(A4B.VID, A4B.PID, LmyUsbPrinterDriver.class);
        return probeTable;
    }

}

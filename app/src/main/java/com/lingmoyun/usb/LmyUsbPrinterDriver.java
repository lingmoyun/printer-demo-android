package com.lingmoyun.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import com.hoho.android.usbserial.driver.CommonUsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Lingmoyun USB Printer Driver
 *
 * @author guoweifeng
 * @date 2023/8/30 17:57
 */
public class LmyUsbPrinterDriver implements UsbSerialDriver {

    private final UsbDevice mDevice;
    private final UsbSerialPort mPort;

    public LmyUsbPrinterDriver(UsbDevice device) {
        mDevice = device;
        mPort = new LmyPrinterSerialPort(mDevice, 0);
    }

    public static boolean probe(UsbDevice device) {
        return countPorts(device) == 1;
    }

    private static int countPorts(UsbDevice device) {
        int controlInterfaceCount = 0;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER)
                controlInterfaceCount++;
        }
        return controlInterfaceCount;
    }

    @Override
    public UsbDevice getDevice() {
        return mDevice;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return Collections.singletonList(mPort);
    }


    public class LmyPrinterSerialPort extends CommonUsbSerialPort {

        public LmyPrinterSerialPort(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        @Override
        protected void openInt() throws IOException {
            UsbInterface usbIface = mDevice.getInterface(mPortNumber);
            if (!mConnection.claimInterface(usbIface, true)) {
                throw new IOException("Could not claim data interface");
            }
            for (int i = 0; i < usbIface.getEndpointCount(); i++) {
                UsbEndpoint ep = usbIface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        mReadEndpoint = ep;
                    } else {
                        mWriteEndpoint = ep;
                    }
                }
            }
        }

        @Override
        protected void closeInt() {
            try {
                for (int i = 0; i < mDevice.getInterfaceCount(); i++)
                    mConnection.releaseInterface(mDevice.getInterface(i));
            } catch(Exception ignored) {}
        }

        @Override
        public UsbSerialDriver getDriver() {
            return LmyUsbPrinterDriver.this;
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public EnumSet<ControlLine> getControlLines() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public EnumSet<ControlLine> getSupportedControlLines() throws IOException {
            throw new UnsupportedOperationException();
        }
    }

}

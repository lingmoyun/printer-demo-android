package com.lingmoyun.usb;


import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * USB打印机操作类
 *
 * @author guoweifeng
 * @date 2022/6/24 18:22
 */
public class USBService implements PrinterClass {
    private static final String TAG = "UsbAdmin";
    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;
    private static PendingIntent mPermissionIntent = null;
    public final ReentrantLock mMainLocker = new ReentrantLock();
    private static final String ACTION_USB_PERMISSION = "com.lingmoyun.USB_PERMISSION";

    //private final int USB_PID = 0xA408; // 41992
    //private final int USB_VID = 0xA48C; // 42124
//    private final int USB_PID = 0x5840; // 22592
//    private final int USB_VID = 0x0483; // 1155
    private final int usbPid;
    private final int usbVid;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            setDevice(device);
                        } else {
                            close();
                            mDevice = device;
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    public USBService(Context context, int usbPid, int usbVid) {
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(mUsbReceiver, filter);
        this.usbPid = usbPid;
        this.usbVid = usbVid;
    }

    @SuppressLint("NewApi")
    private void setDevice(UsbDevice device) {
        if (device == null) {
            return;
        }
        UsbInterface intf = null;

        int interfaceCount = device.getInterfaceCount();

        mDevice = device;
        for (int j = 0; j < interfaceCount; j++) {
            intf = device.getInterface(j);
            Log.i(TAG, "interface:" + j + "class:" + intf.getInterfaceClass());
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                int UsbEndpointCount = intf.getEndpointCount();
                UsbEndpoint endpoint;
                for (int i = 0; i < UsbEndpointCount; i++) {
                    endpoint = intf.getEndpoint(i);
                    if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                            endpointIn = endpoint;
                            Log.i(TAG, "interface:" + j + "endPoint:" + i);
                        }
                        if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                            endpointOut = endpoint;
                            Log.i(TAG, "interface:" + j + "endPoint:" + i);
                        }
                        if (endpointIn != null && endpointOut != null) {
                            break;
                        }
                    }

                }
            }

            if (endpointIn != null && endpointOut != null) {
                break;
            }
        }
        if (endpointIn == null || endpointOut == null) {
            Log.i(TAG, "Not printer class interface");
            return;
        }

        UsbDeviceConnection connection = mUsbManager.openDevice(device);

        if (connection != null && connection.claimInterface(intf, true)) {
            Log.i(TAG, "open successed.");
            mConnection = connection;

        } else {
            Log.i(TAG, "open failed");
            mConnection = null;
        }
    }

    public int send(byte[] buffer) {
        int i = 0;
        if (endpointOut != null && mConnection != null) {
            mMainLocker.lock();
            try {
                int pktSize = endpointOut.getMaxPacketSize();
                while (i < buffer.length) {
                    int size = Math.min(pktSize, buffer.length - i);
                    byte[] data = new byte[size];
                    System.arraycopy(buffer, i, data, 0, size);
                    int nSent = mConnection.bulkTransfer(endpointOut, data, data.length, 2147483647);
                    if (nSent < 0) {
                        break;
                    }
                    i += nSent;
                }
            } catch (Exception e) {
                close();
            } finally {
                mMainLocker.unlock();
            }
        }
        return i;
    }

    public int read(byte[] buffer, int timeout) {
        int i = 0;
        if (endpointIn != null && mConnection != null) {
            mMainLocker.lock();
            try {
                int pktSize = Math.min(buffer.length, endpointIn.getMaxPacketSize());
                i = mConnection.bulkTransfer(endpointIn, buffer, pktSize, timeout);
            } catch (Exception e) {
                close();
            } finally {
                mMainLocker.unlock();
            }
        }
        return i;
    }

    @SuppressLint("NewApi")
    public boolean sendCommand(byte[] content) {
        boolean result = true;
        synchronized (this) {
            if (send(content) < content.length) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public boolean open() {
        //close();
        if (mDevice != null) {
            setDevice(mDevice);
            if (mConnection == null) {
                HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

                for (UsbDevice device : deviceList.values()) {
                    mUsbManager.requestPermission(device, mPermissionIntent);
                }
            }
        } else {
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

            for (UsbDevice device : deviceList.values()) {
                Log.i(TAG, device.getDeviceName() + "'s VID=" + device.getVendorId() + ", PID=" + device.getProductId());
                if (device.getVendorId() == usbVid && device.getProductId() == usbPid) {
                    mUsbManager.requestPermission(device, mPermissionIntent);
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public boolean close() {
        mDevice = null;
        if (mConnection != null) {
            mConnection.close();
            mConnection = null;
            return true;
        } else {
            return false;
        }

    }

    @Override
    public boolean isOpen() {
        return mConnection != null;
    }

    @Override
    public boolean write(byte[] bt) {
        return sendCommand(bt);
    }

    @Override
    public byte[] read(int maxLength, int timeout) {
        byte[] buffer = new byte[maxLength];
        int size = read(buffer, timeout);
        if (size <= 0) return new byte[0];
        byte[] data = new byte[size];
        System.arraycopy(buffer, 0, data, 0, size);
        return data;
    }
}

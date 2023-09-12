package com.lingmoyun.printerdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lingmoyun.instruction.cpcl.CpclBuilder;
import com.lingmoyun.mdns.MdnsPlugin;
import com.lingmoyun.mdns.handlers.ServiceDiscoveredHandler;
import com.lingmoyun.mdns.handlers.ServiceResolvedHandler;
import com.lingmoyun.util.BitmapUtils;
import com.lingmoyun.util.HexByteUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TCP/IP Demo
 *
 * @author guoweifeng
 * @date 2021/1/22 17:57
 */
public class TcpDemoActivity extends AppCompatActivity {
    private static final String TAG = "TcpDemoActivity";

    private Socket client;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile Receiver mReceiver = new Receiver();
    private final List<Map<String, String>> mList = new ArrayList<>();
    private SimpleAdapter mAdapter;
    private MdnsPlugin mdnsPlugin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tcp);

        findViewById(R.id.btn_tcp_search).setOnClickListener(view -> searchPrinter());
        findViewById(R.id.btn_tcp_test).setOnClickListener(view -> testPrint());

        mAdapter = new SimpleAdapter(this, mList, R.layout.list_item, new String[]{"name", "address"}, new int[]{R.id.tv_bt_name, R.id.tv_bt_mac});
        ((ListView) findViewById(R.id.lv_tcp_device)).setAdapter(mAdapter);
        ((ListView) findViewById(R.id.lv_tcp_device)).setOnItemClickListener((adapterView, view, position, itemId) -> {
            String address = mList.get(position).get("address");
            String[] split = address.split(":");
            ((EditText) findViewById(R.id.et_tcp_ip)).setText(split[0]);
            ((EditText) findViewById(R.id.et_tcp_port)).setText(split[1]);
        });

        init();
    }

    private void init() {
        mdnsPlugin = new MdnsPlugin(TcpDemoActivity.this);
        mdnsPlugin.setDiscoveredHandler(mReceiver);
        mdnsPlugin.setResolvedHandler(mReceiver);
    }

    private void searchPrinter() {
        mList.clear();
        mAdapter.notifyDataSetChanged();

        mdnsPlugin.stopDiscovery();
        mdnsPlugin.startDiscovery("_a4print._tcp.");
        //mdnsPlugin.startDiscovery("_a4print._tcp.local");
    }

    /**
     * 测试打印
     * <p>
     * 流程：0.连接TCP
     * 1.下发打印指令
     * 2.读取打印机回复
     * 3.断开TCP
     */
    private void testPrint() {
        Toast.makeText(TcpDemoActivity.this, "开始打印", Toast.LENGTH_SHORT).show();
        //((TextView) findViewById(R.id.tv_bt_log)).setText("正在打印。。。");
        // 打印机TCP IP/PORT
        final String ip = ((EditText) findViewById(R.id.et_tcp_ip)).getText().toString().trim();
        final int port = Integer.parseInt(((EditText) findViewById(R.id.et_tcp_port)).getText().toString().trim());
        final String address = ip + ":" + port;
        new Thread(() -> {
            try {
                // 连接TCP
                client = new Socket(ip, port);
                inputStream = client.getInputStream();
                outputStream = client.getOutputStream();

                // 监听TCP数据
                new Thread(() -> {
                    byte[] buffer = new byte[1024];
                    try {
                        while (inputStream != null && outputStream != null) {

                            if (inputStream.available() > 0) {
                                int len = inputStream.read(buffer);
                                byte[] data = new byte[len];
                                System.arraycopy(buffer, 0, data, 0, len);
                                Log.d(Thread.currentThread().getName(), "testPrint: read data(hex): " + HexByteUtils.byteArrayToHexStr(data));
                                Log.d(Thread.currentThread().getName(), "testPrint: read data: " + HexByteUtils.toString(data));
                                break;
                            }
                        }

                        // 读到数据后，断开TCP
                        closeTcp();
                    } catch (IOException ignored) {
                    }


                }, "tcp-" + address + "-read").start();

                Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream start===");
                Bitmap src = BitmapFactory.decodeStream(getAssets().open("test_a4_300dpi.jpg"));
                // 缩放到203DPI A4大小 1678*2376
                Bitmap bitmap = BitmapUtils.scale(src, 1678, 2374);
                Log.d(TAG, "testPrint: ===BitmapFactory.decodeStream finish===");
                /* =====构建指令================================================================ */
                Log.d(TAG, "testPrint: ===CpclBuilder start===");
                /** 构建CPCL 更多用法见{@link CPCLExample#example(Context)} */
                byte[] cpcl = CpclBuilder.createArea(0, 203, bitmap.getWidth(), bitmap.getHeight(), 1) // 203DPI
                        //.taskId("1") // 任务ID，部分机型支持，这里传什么，打印结果就会携带什么，如果不需要打印结果注释这一行即可
                        // 推荐：图片指令-压缩，部分机型支持
                        .imageGG(0, 0, bitmap)
                        .formPrint()
                        .cut(0) // 切刀指令，切纸，无切刀机器不受影响
                        .build();
                Log.d(TAG, "testPrint: ===CpclBuilder finish===");
                /* =====构建指令================================================================ */
                // 释放图片资源
                src.recycle();
                bitmap.recycle();

                // write
                Log.d(TAG, "testPrint: ===outputStream.write start===");
                outputStream.write(cpcl);
                outputStream.flush();
                Log.d(TAG, "testPrint: ===outputStream.write finish===");
                Log.d(Thread.currentThread().getName(), "testPrint: write data: " + cpcl.length + " bytes.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "tcp-" + address + "-connect").start();
    }

    /**
     * 断开连接
     */
    private void closeTcp() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            inputStream = null;
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            outputStream = null;
        }

        try {
            if (client != null) {
                client.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client = null;
        }
    }

    private class Receiver implements ServiceDiscoveredHandler, ServiceResolvedHandler {

        @Override
        public void onServiceResolved(Map<String, Object> serviceInfoMap) {
            Map<String, String> map = new HashMap<>();
            map.put("name", (String) serviceInfoMap.get("name"));
            map.put("address", (serviceInfoMap.get("host") + ":" + serviceInfoMap.get("port")).replace("/", ""));
            //map.put("address", serviceInfoMap.toString());
            mList.add(map);
            runOnUiThread(() -> mAdapter.notifyDataSetChanged());
        }

        @Override
        public void onServiceDiscovered(Map<String, Object> serviceInfoMap) {
        }
    }


}
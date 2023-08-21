package com.lingmoyun.printerdemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_test_bt).setOnClickListener(view -> openActivity(BluetoothDemoActivity.class));
        findViewById(R.id.btn_test_tcp).setOnClickListener(view -> openActivity(TcpDemoActivity.class));
        findViewById(R.id.btn_test_usb).setOnClickListener(view -> openActivity(UsbDemoActivity.class));
        findViewById(R.id.btn_test_usb).setVisibility(View.GONE); // USB未经测试，暂时隐藏

    }

    private void openActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }


}
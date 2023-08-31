package com.lingmoyun.printerdemo;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_test_bt).setOnClickListener(view -> openActivity(BluetoothDemoActivity.class));
        findViewById(R.id.btn_test_tcp).setOnClickListener(view -> openActivity(TcpDemoActivity.class));
        findViewById(R.id.btn_test_usb).setOnClickListener(view -> openActivity(UsbDemoActivity.class));

    }

    private void openActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }


}
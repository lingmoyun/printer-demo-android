# Printer Demo

[![License](https://img.shields.io/badge/license-MIT-4EB1BA.svg)](https://www.opensource.org/licenses/mit-license.php)

零墨云旗下打印机Demo（Android版本）

### 示例

| 通讯方式      | 发现打印机 | 打印示例 | 备注        |
|-----------|:-----:|:----:|-----------|
| Bluetooth |   ✓   |  ✓   | SPP & BLE |
| TCP/IP    |   ✓   |  ✓   |           |
| USB       |   ✓   |  ✓   |           |

#### 集成步骤

* 复制 [app/libs/cpcl-sdk-android-release.aar](./app/libs/cpcl-sdk-android-release.aar) 到你项目中的 `app/libs/` 目录下

* 在项目 app 模块下的 `build.gradle` 文件中加入依赖

```groovy
dependencies {
    // CPCL SDK
    implementation(fileTree("libs"))
}
```

* **蓝牙**Demo使用了 [XXPermissions](https://github.com/getActivity/XXPermissions) 权限框架，如需拷贝示例到自己项目中，请参考它的 [集成步骤](https://github.com/getActivity/XXPermissions/tree/18.2#%E9%9B%86%E6%88%90%E6%AD%A5%E9%AA%A4) ，如果不需要，那么这一步骤请忽略。

* **USB**Demo使用了 [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) USB插件，如需拷贝示例到自己项目中，请参考它的 [Quick Start 第1部分](https://github.com/mik3y/usb-serial-for-android#quick-start) ，然后拷贝[`com.lingmoyun.usb.*`](./app/src/main/java/com/lingmoyun/usb)到你项目中，如果不需要，那么这一步骤请忽略。

## 零墨云旗下打印机Demo汇总

- [printer-demo][1]

[1]: https://github.com/lingmoyun/printer-demo

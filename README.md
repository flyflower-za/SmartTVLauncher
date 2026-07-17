# Whaley TV Minimalist Launcher (微镜极简电视桌面)

一个专为 **微鲸智能电视 (Android 5.0.2 / Mstar 芯片平台)** 及其他网络电视盒子打造的**极简直达桌面**。能够开机自启、倒计时直达默认信号源，并完美替换原厂臃肿、带有广告的系统桌面。

---

## 🌟 核心功能特性

* **信号源智能直达**：完美适配微鲸内置播放器切换逻辑。开机自动倒计时 5 秒，直达连接 HDMI 1、HDMI 2 或 AV 信号源。
* **默认开机源自由设定**：在信号源卡片上**长按遥控器 OK/确认键**，即可将其设为默认自启信号源（显示黄色 ⭐ 默认标）。
* **智能自启拦截**：遥控器方向键介入操作后，倒计时条会自动隐蔽退场，避免干扰您的日常使用。
* **自定义常用 APP 固定**：
  - 首页底部精简至仅有 5 个应用插槽 + 1 个固定的“全部应用”网格入口。
  - 在常用应用卡片上**长按遥控器 OK/确认键**，即可呼出系统已安装应用列表弹窗，自行选择并替换固定在此处的 APP，状态自动存盘持久化。
* **全屏网格应用抽屉**：点击“全部应用”会开启清爽的**全屏平铺式网格卡片浮层**，方便大屏电视用遥控器快速选定与启动第三方应用。
* **返回键无缝屏蔽**：在桌面误触返回键时自动消费，阻止桌面 Activity 异常闪烁或退出重载。
* **视觉美化**：配备极简无缝正方形应用图标与高清艺术流体壁纸背景，格调优雅。

---

## 🛠️ 本地手动构建指南

如果您想自行修改源码并打包，可以在终端直接运行以下 Android 命令行工具链组合进行编译（要求本地配置有 Java 8）：

```bash
# 清理旧编译临时文件并一键生成、签名 APK
rm -rf .build_tools/classes .build_tools/classes_dex .build_tools/compiled_resources.zip .build_tools/linked_resources.apk && \
.build_tools/aapt2 compile --dir LauncherProject/res -o .build_tools/compiled_resources.zip && \
.build_tools/aapt2 link -I .build_tools/android.jar --manifest LauncherProject/AndroidManifest.xml --java LauncherProject/src -o .build_tools/linked_resources.apk .build_tools/compiled_resources.zip && \
mkdir -p .build_tools/classes && \
javac -source 1.8 -target 1.8 -bootclasspath .build_tools/android.jar -d .build_tools/classes LauncherProject/src/com/whaley/launcher/*.java && \
mkdir -p .build_tools/classes_dex && \
java -cp .build_tools/r8.jar com.android.tools.r8.D8 --lib .build_tools/android.jar --output .build_tools/classes_dex .build_tools/classes/com/whaley/launcher/*.class && \
cd .build_tools/classes_dex && zip -ur ../linked_resources.apk classes.dex && cd ../.. && \
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore .build_tools/debug.keystore -storepass android .build_tools/linked_resources.apk androiddebugkey && \
cp .build_tools/linked_resources.apk SmartInputLauncher.apk
```

---

## 📥 安装运行

您可以直接在 Release 中下载最新版编译好的 [SmartInputLauncher.apk](./SmartInputLauncher.apk)，或者在局域网内通过 adb 命令行强行推送：

```bash
adb connect <你的电视IP>:5555
adb install -r -d SmartInputLauncher.apk
```

项目代码基于 Android Open Source Project 规范编写，欢迎大家提交 PR 或者 issue 共同打磨！

# Whaley TV Minimalist Launcher (微镜极简电视桌面)

一个专为 **微鲸智能电视 (Android 5.0.2 / Mstar 芯片平台)** 打造的**极简直达桌面**。能够开机自启、倒计时直达默认信号源，并完美替换原厂臃肿、带有广告的系统桌面。

> [!IMPORTANT]
> **关于设备兼容性与移植说明**：
> * **微鲸电视/Helios系统**：完全开箱可用，信号源切换逻辑已完美免 Root 适配。
> * **其他品牌电视（如小米、索尼、TCL等）**：由于信号源直达（HDMI1/2/AV）重度依赖微鲸底层的 `com.whaley.tv.tvplayer` 播放器与 Mstar 底层驱动接口，因此**本桌面的信号源直达卡片在其他品牌上无法直接跳转**。但底部的 **自定义常用APP固定**、**全屏平铺应用抽屉**、**防退出闪烁拦截** 等桌面核心功能是标准的 Android API，在其他设备上仍可通用。

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

---

## ⚡ 电视系统瘦身与桌面接管指南 (ADB 实战)

为了让“微镜极简桌面”完美替代原厂桌面，并彻底消除开机广告、解决老旧电视卡顿，强烈建议在连接 ADB 后，运行以下命令禁用微鲸系统底层的臃肿组件与广告模块：

### 1. 彻底禁用原厂桌面与开机广告
```bash
# 禁用原厂广告与桌面组件，执行后按 Home 键将直接永久启动“微镜极简桌面”
adb shell pm disable-user com.helios.launcher                # 禁用原厂 Helios 桌面
adb shell pm disable-user com.whaley.tv.guide                # 禁用原厂开机引导/广告
adb shell pm disable-user com.whaley.tv.startupad            # 禁用开机广告服务
```

### 2. 禁用无用及臃肿系统后台（可选精简）
```bash
# 禁用系统多余的后台统计、云服务和升级程序，能省出约 200MB+ 内存运行空间
adb shell pm disable-user com.whaley.tv.recommend            # 禁用原厂推荐服务
adb shell pm disable-user com.whaley.tv.member               # 禁用会员中心组件
adb shell pm disable-user com.whaley.tv.update               # 禁用系统强制自动升级
adb shell pm disable-user com.whaley.market.assist          # 禁用当贝辅助应用
```

### 3. 如何恢复原厂状态（防砖回滚命令）
如果以后想恢复微鲸原厂桌面，随时运行以下命令即可：
```bash
adb shell pm enable com.helios.launcher
adb shell pm enable com.whaley.tv.guide
adb shell pm enable com.whaley.tv.startupad
```

---

项目代码基于 Android Open Source Project 规范编写，欢迎大家提交 PR 或者 issue 共同打磨！

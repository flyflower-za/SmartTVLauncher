# Android TV 模拟器使用指南

> 适用于 Apple Silicon (M1/M2/M3/M4) Mac，命令行工具链，无需 Android Studio

---

## 📋 环境信息

| 项目 | 值 |
|------|-----|
| SDK 路径 | `~/Library/Android/sdk` |
| 磁盘占用 | ~2.7GB |
| 模拟器版本 | 36.6.11.0 |
| 宿主机架构 | ARM64 (Apple Silicon) |

### 已安装的 AVD

| 名称 | Android 版本 | API | 架构 | 用途 |
|------|-------------|-----|------|------|
| `tv_arm64` | 5.0 Lollipop | 21 | arm64-v8a | 与微鲸真机系统版本一致 |
| `tv_23_arm64` | 6.0 Marshmallow | 23 | arm64-v8a | 推荐日常开发，性能更好 |

---

## 🚀 启动与关闭

### 启动模拟器

```bash
# 推荐：API 23，速度快
emulator -avd tv_23_arm64 -no-boot-anim &

# 或 API 21（与微鲸真机版本一致）
emulator -avd tv_arm64 -no-boot-anim &

# 常用启动参数组合
emulator -avd tv_23_arm64 \
  -no-boot-anim \        # 跳过开机动画，加快启动
  -netdelay none \       # 无网络延迟
  -netspeed full \       # 全速网络
  -writable-system \     # 可写系统分区（需要时加）
  &
```

启动后约 15-30 秒内完成开机，终端检查就绪状态：

```bash
# 等待模拟器就绪
adb wait-for-device
echo "✅ 模拟器已就绪"
```

### 关闭模拟器

```bash
# 方式一：通过 adb 关机
adb emu kill

# 方式二：直接杀进程
kill $(pgrep -f "emulator.*tv_")

# 方式三：点击模拟器窗口的 × 按钮
```

---

## 📦 安装与调试 APP

### 安装 APK

```bash
# 安装（保留数据和权限）
adb install -r SmartInputLauncher.apk

# 降级安装（允许覆盖更高版本）
adb install -r -d SmartInputLauncher.apk

# 安装到指定设备（多模拟器时）
adb -s emulator-5554 install -r SmartInputLauncher.apk
```

### 查看已连接设备

```bash
adb devices
# 输出示例:
# List of devices attached
# emulator-5554   device
```

### 卸载

```bash
adb uninstall com.whaley.launcher
```

---

## 🖥️ 桌面 Launcher 测试

### 触发 Home 键（弹出桌面选择器）

```bash
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

### 将你的 Launcher 设为默认桌面

首次按 Home 键会弹出"选择桌面"对话框，选择你的桌面并点"始终"即可。之后按 Home 键直接进入。

### 清除默认桌面设置（重新选择）

```bash
# 清除默认桌面偏好
adb shell pm clear com.android.settings

# 或者手动操作：设置 → 应用 → 找到当前默认桌面 → 清除默认值
```

---

## 🎮 遥控器操作模拟

模拟器窗口右侧工具栏提供遥控器按键：

| 功能 | 模拟方式 |
|------|---------|
| 方向键 (↑↓←→) | 键盘方向键 / 点击右侧 D-Pad |
| OK/确认 | 键盘 `Enter` / 点击 D-Pad 中心 |
| 返回 | 键盘 `Esc` / 点击 Back 按钮 |
| Home | 键盘 `Cmd+H` / 点击 Home 按钮 |
| 菜单 | 键盘 `F2` / 点击 Menu 按钮 |

### 通过 adb 模拟按键

```bash
# 方向键
adb shell input keyevent KEYCODE_DPAD_UP       # 上
adb shell input keyevent KEYCODE_DPAD_DOWN     # 下
adb shell input keyevent KEYCODE_DPAD_LEFT     # 左
adb shell input keyevent KEYCODE_DPAD_RIGHT    # 右
adb shell input keyevent KEYCODE_DPAD_CENTER   # 确认(OK)

# 功能键
adb shell input keyevent KEYCODE_HOME          # Home 键
adb shell input keyevent KEYCODE_BACK          # 返回键
adb shell input keyevent KEYCODE_MENU          # 菜单键
adb shell input keyevent KEYCODE_ENTER         # 回车
```

### 长按模拟

```bash
# 模拟长按（通过 swipe 同一点持续按住实现）
adb shell input swipe 500 500 500 500 1000    # 在坐标(500,500)处长按1秒
```

---

## 🐛 调试命令速查

### 查看日志

```bash
# 实时日志（过滤你的应用）
adb logcat | grep -E "whaley|launcher"

# 清空旧日志后重新开始
adb logcat -c && adb logcat | grep "whaley"

# 只看 Error 级别
adb logcat *:E
```

### 查看当前 Activity

```bash
# 查看当前前台 Activity
adb shell dumpsys activity activities | grep mResumedActivity
```

### 查看已安装应用列表

```bash
# 列出所有包名
adb shell pm list packages

# 搜索特定包
adb shell pm list packages | grep launcher
```

### 启用/禁用应用组件

```bash
# 启用应用
adb shell pm enable com.whaley.launcher

# 禁用应用
adb shell pm disable-user com.whaley.launcher

# 查看组件状态
adb shell dumpsys package com.whaley.launcher | grep -A5 "enabled"
```

---

## 🔧 高级用法

### 多模拟器同时运行

```bash
# 启动第二个模拟器（端口自动递增）
emulator -avd tv_23_arm64 -no-boot-anim &

# 对指定设备操作
adb -s emulator-5554 install app.apk
adb -s emulator-5556 install app.apk
```

### 快照管理

```bash
# 保存快照
adb emu avd snapshot save clean_install

# 恢复快照
adb emu avd snapshot load clean_install

# 列出快照
adb emu avd snapshot list
```

### 屏幕截图

```bash
# 截图保存到电脑
adb exec-out screencap -p > screenshot.png

# 录制屏幕
adb shell screenrecord /sdcard/demo.mp4
# 按 Ctrl+C 停止录制
adb pull /sdcard/demo.mp4 .
```

### 文件传输

```bash
# 推送到模拟器
adb push local_file.txt /sdcard/

# 从模拟器拉取
adb pull /sdcard/remote_file.txt .
```

### 修改分辨率（临时）

```bash
# 查看当前分辨率
adb shell wm size

# 修改为 1080p
adb shell wm size 1080x1920

# 恢复默认
adb shell wm size reset
```

---

## ❓ 常见问题

### Q: 报错 "CPU Architecture 'arm' is not supported"

Apple Silicon 只支持 **arm64-v8a** 镜像，`armeabi-v7a` 和 `x86` 镜像无法使用。使用 `tv_arm64` 或 `tv_23_arm64`。

### Q: 模拟器启动后黑屏

```bash
# 冷启动（清除数据）
emulator -avd tv_23_arm64 -no-boot-anim -wipe-data &
```

### Q: adb 找不到模拟器

```bash
# 检查 adb 服务
adb kill-server && adb start-server

# 手动连接
adb connect localhost:5554
```

### Q: 模拟器卡顿

```bash
# 分配更多内存（默认 2GB）
emulator -avd tv_23_arm64 -no-boot-anim -memory 4096 &
```

### Q: 模拟器窗口太大

```bash
# 缩放为 50%
emulator -avd tv_23_arm64 -no-boot-anim -scale 0.5 &
```

---

## 📝 日常开发工作流

```bash
# 1. 启动模拟器
emulator -avd tv_23_arm64 -no-boot-anim &

# 2. 等待就绪
adb wait-for-device

# 3. 构建 APK（你的项目用这个命令）
cd ~/path/to/SmartTVLauncher
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

# 4. 装到模拟器
adb install -r -d SmartInputLauncher.apk

# 5. 启动测试
adb shell am start -n com.whaley.launcher/.MainActivity

# 6. 设为默认桌面
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

# 7. 查看日志
adb logcat | grep -E "whaley|launcher|Error"
```

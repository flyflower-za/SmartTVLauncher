# 📺 Android TV 远程屏幕投影与调试控制指南

为了方便在电脑前进行远程调试，无需频繁走动操作电视真机，推荐使用 `adb` 配合 `scrcpy` 投屏工具实现实时画面预览与电脑键盘/鼠标远程控制。

---

## 🔌 1. 建立 ADB 远程连接

在终端中执行以下命令连接到电视：

```bash
# 连接到电视（确保电视与电脑在同一局域网下）
adb connect 10.0.0.10:5555

# 查看连接状态（状态应显示为 device）
adb devices
```

---

## 🖥️ 2. 安装与配置 `scrcpy`

`scrcpy` 是一款极低延迟、画质极高且免费开源的安卓投屏工具。

### macOS 安装命令：
```bash
brew install scrcpy
```

---

## 🚀 3. 画面投影与控制启动方案

针对老款电视（如微鲸 Android 5.0 MStar 芯片），如果使用默认参数启动 `scrcpy`，可能会导致电视硬件编码器崩溃报错。请根据实际需求使用以下优化方案启动：

### 🌟 方案一：全高清 1080p 极佳画质（推荐）
如果电视硬件编码器良好，可以使用全高清模式：
```bash
scrcpy -m 1920 -b 4M --max-fps 30
```
* `-m 1920`：最大分辨率边长为 1920px。
* `-b 4M`：码率限制为 4Mbps，保证画面细腻度。
* `--max-fps 30`：限制最高帧率为 30fps，降低传输开销。

### 📡 方案二：流畅 720p 清晰度（折中首选）
若 1080p 出现轻微卡顿或画面延迟，建议使用 720p 调试：
```bash
scrcpy -m 1280 -b 2M --max-fps 25
```

### 💡 方案三：软解兼容模式（极力推荐，如果前两者闪退/断开）
强行绕过电视芯片不稳定的硬件编码器，直接调用 Google 系统级软件 H.264 编码器：
```bash
scrcpy --video-encoder OMX.google.h264.encoder -m 1280 -b 3M
```

---

## ⌨️ 4. 远程按键与控制映射表

当 `scrcpy` 投屏窗口获取焦点后，你可在电脑上直接使用键鼠模拟遥控器操作：

| 电脑输入 | 模拟遥控器按键 |
|:---|:---|
| 键盘 **方向键 (↑ ↓ ← →)** | 遥控器方向键 D-Pad |
| 键盘 **Enter (回车键)** | 遥控器确认键 (OK) |
| 键盘 **Esc (退出键)** | 遥控器返回键 (Back) |
| 键盘 **Home 键** / **鼠标右键** | 遥控器主页键 (Home) |
| 键盘 **PageUp 键** / **F1 键** | 遥控器菜单键 (Menu) |
| **鼠标左键点击** | 模拟屏幕触摸 |

---

## ⌨️ 5. 纯命令行模拟遥控操作（非投屏模式）

如果仅希望在终端中发送单个指令使电视跳转，可使用以下 `adb` 按键事件：

```bash
# 向上 / 向下 / 向左 / 向右
adb shell input keyevent 19   # UP
adb shell input keyevent 20   # DOWN
adb shell input keyevent 21   # LEFT
adb shell input keyevent 22   # RIGHT

# 确认键 / 返回键 / 主页键
adb shell input keyevent 23   # OK
adb shell input keyevent 4    # BACK
adb shell input keyevent 3    # HOME
```

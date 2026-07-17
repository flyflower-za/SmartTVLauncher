# 电视品牌移植与信号源指令反编译嗅探指南 (Porting Guide)

如果你用的是小米、索尼、TCL 等其他品牌的电视，想要将本桌面的**“信号源卡片一键直达 (HDMI 1/2 / AV)”**功能完美适配到你自己的设备上，你可以根据本指南的“反编译与抓包”思路，自行分析原厂系统的跳转指令。

---

## 🛠️ 第一步：拉取原厂系统的桌面与信号源组件

电视切换信号源，要么是通过原厂桌面（Launcher）跳转，要么是通过一个专门的“电视/信号源播放器”（通常包名含有 `tvplayer` 或 `guide`）来切换。

1. **连接电视 ADB**：
   ```bash
   adb connect <电视IP>:5555
   ```
2. **列出系统所有的应用包**，找到原厂桌面或播放器：
   ```bash
   adb shell pm list packages | grep -E "launcher|tvplayer|guide|control"
   ```
3. **获取原厂 APK 的安装路径**：
   ```bash
   adb shell pm path com.xxx.xxx (上一步找到的包名)
   # 返回示例: package:/system/priv-app/HeliosGuide/HeliosGuide.apk
   ```
4. **拉取 APK 到本地电脑**：
   ```bash
   adb pull /system/priv-app/HeliosGuide/HeliosGuide.apk ./
   ```

---

## 🔍 第二步：静态反编译分析 (寻找跳转 Intent)

将拉出来的 APK 使用反编译工具（推荐使用 **`jadx-gui`** 或 **`Apktool`**）打开：

1. **查找关键字**：
   在源码全局搜索以下关键字：
   * `"HDMI"` / `"HDMI_1"` / `"HDMI1"`
   * `"input"` / `"source"`
   * `"TvPlayer"` / `"START_TV_PLAYER"`
   * `"E_INPUT_SOURCE"`
2. **寻找跳转 Intent 启动点**：
   原厂系统一般都是通过向系统发送特定的 Action 来调起播放器的。在代码中寻找类似如下的 `Intent` 实例化逻辑：
   ```java
   Intent intent = new Intent("com.xxx.action.START_PLAYER");
   intent.putExtra("inputSrc", 4); // 或者其它参数
   startActivity(intent);
   ```
   **微鲸的实例**：我们在反编译微鲸引导应用后，在 `LauncherAutoJump.java` 中找到了：
   * Action: `"com.whaley.tv.tvplayer.ui.START_TV_PLAYER"`
   * Extra 键名: `"inputSrc"`，对应 Ordinal 数字：HDMI1 对应 `4`，HDMI2 对应 `5`，AV 对应 `7`。
3. **寻找底层芯片驱动反射点**：
   如果是电视厂商深度定制的底层芯片级切换，会在源码里导入底层芯片服务，如 **Mstar 平台** 的 `TvCommonManager`：
   ```java
   import com.mstar.android.tv.TvCommonManager;
   TvCommonManager.getInstance().setInputSource(TvOsType.EnumInputSource.E_INPUT_SOURCE_HDMI1);
   ```
   如果你在原厂包里反编译看到了这些类，你也可以在 `MainActivity.java` 中使用**反射 (Reflection)** 来动态调用这些驱动方法。

---

## 📡 第三步：动态抓包分析 (如果代码被混淆)

如果原厂 APK 被加固或代码混淆严重，导致无法直接看清跳转，我们可以通过**动态抓取系统日志**的方式，在点击原厂信号源的瞬间捕捉 Intent：

1. **启动日志监视**：
   ```bash
   # 仅过滤系统 ActivityManager 启动组件的日志
   adb logcat | grep -i "ActivityManager"
   ```
2. **操作遥控器**：在原厂电视桌面上，使用遥控器点击切换到 **HDMI 1**。
3. **查看终端输出**，寻找含有 `cmp=` 或者 `Act=` 的启动日志：
   ```text
   ... START u0 {act=com.whaley.tv.tvplayer.ui.START_TV_PLAYER cmp=com.whaley.tv.tvplayer.ui/.TvPlayerActivity (has extras)} ...
   ```
   * **`act`** 就是你需要的 `Intent Action`。
   * **`cmp`** 就是目标 Activity 组件。
   * **`has extras`** 说明包含额外参数。

---

## 🏗️ 第四步：获取 Extra 参数的真实数值 (Ordinal 测试法)

如果跳转接口需要传参（例如 `inputSrc` 对应哪个数字），而数字在底层驱动中是硬编码的，可以采用**数值碰撞测试法**：

1. 编写测试脚本（或者像本项目中使用的 `TestSwitch.java` 测试类）。
2. 在终端使用 `adb shell am start` 模拟传参并观察电视画面是否切换：
   ```bash
   # 测试 1 到 10，看看电视在哪个数字时会正确切换到对应的 HDMI 通道
   adb shell am start -a "com.whaley.tv.tvplayer.ui.START_TV_PLAYER" --ei "inputSrc" 4
   adb shell am start -a "com.whaley.tv.tvplayer.ui.START_TV_PLAYER" --ei "inputSrc" 5
   ```
3. 记录电视发生切换的对应传参，然后修改本项目的 `MainActivity.java` 的映射关系即可。

---

希望这份指南能帮助大家成功将极简桌面移植到更多品牌的电视上，彻底摆脱臃肿和开机广告的折磨！

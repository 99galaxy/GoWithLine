# GoWithLine 路线模拟

画一条路线，让手机定位按设定速度走。免 root、无账号、无广告的开源 Android 定位测试工具。[English](README.en.md)

[![release](https://img.shields.io/badge/release-1.0-blue)](https://github.com/99galaxy/GoWithLine/releases/latest)
[![license](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84)

![GoWithLine 演示](docs/screenshots/demo.gif)

**[下载最新 APK](https://github.com/99galaxy/GoWithLine/releases/latest)** · 约 5 MB · Android 8.0+ · SHA-256 见 Release 页

| 适合 | 不适合 |
|---|---|
| 开发调试、教学演示、地图与轨迹类 App 的定位测试 | 考勤打卡、校园跑/体测、游戏定位，或任何绕过检测的用途 |

本应用通过 Android 调试 API 注入位置，部分 App 能识别模拟位置（`isFromMockProvider`、FusedLocation、厂商 SDK），无 root 条件下无法绕过，这也不是本项目的目标。

<img src="docs/screenshots/main.png" width="240" alt="手绘路线"> <img src="docs/screenshots/joystick.png" width="240" alt="摇杆模式"> <img src="docs/screenshots/selfcheck.png" width="240" alt="自检">

## 功能

- 手绘路线：单指按住拖动，可多段绘制，自动连接；支持撤销、清空。**绘制模式下双指仍可缩放/拖动地图**，抬手后继续单指画线。
- 恒定速度：速度以 km/h 输入，内置步行/跑步/骑行/驾车快捷值，运行中可实时调整。
- 平滑移动：按真实时间推进距离，沿折线插值经纬度与方位角；同时向 GPS / NETWORK / FUSED 三个测试 provider 注入（不同机型能成功的组合不同）。
- 暂停/继续/停止：暂停和完成时仍持续发布最后位置，避免定位闪回；停止后移除测试 provider，恢复真实定位。
- 循环路线、地图跟随、前台通知（暂停/继续/停止）、WakeLock。
- 悬浮速度窗：在其他应用上方显示当前模拟速度，支持拖动、`−/+` 微调和 5/10/20/40 km/h 快捷切换，关闭按钮可直接隐藏。
- 摇杆模式：选择后悬浮窗中显示摇杆；只控制方向，速度用设定值，拖动移动、松开停止。适合不方便提前画路线的场景。
- 摇杆锁定：点摇杆头切换锁定/解锁；锁定当前方向后松手仍按设定速度继续移动，再点一次摇杆头停止。
- 摇杆模式的悬浮窗不再使用面板，只保留半透明摇杆和 `−/+` 速度按钮；速度、单位和锁定状态都显示在摇杆头上。
- 设置页：模拟模式、悬浮窗、地图图层、自检、关于集中管理。
- 关于：当前版本、开发者 GitHub、项目开源地址、检查更新（GitHub Releases）。
- 状态自动保存：路线、速度、循环、图层、模拟模式、地图中心。
- 地图图层：
  - OpenStreetMap（WGS-84，内置；HOT 瓦片镜像，由 OpenStreetMap France 提供）
  - 自定义 XYZ 瓦片模板（可选择 WGS-84 / GCJ-02；填写你有权使用的服务）

> 本项目只内置 OpenStreetMap（HOT 瓦片镜像，数据 © OpenStreetMap contributors，ODbL）。其他底图请通过自定义 XYZ 指向你有权使用的瓦片服务。内置瓦片可能因网络或服务策略失效。

## 使用步骤

1. 安装 APK，打开应用，勾选并同意使用声明（不用于考勤打卡、校园跑/体测或游戏定位），授予定位与通知权限。
2. 开启开发者选项：
   - 设置 → 关于手机 → 连续点击“版本号”7 次；
   - 设置 → 开发者选项 → **选择模拟位置信息应用** → 选择“GoWithLine”。
3. 路线模式：点击“绘制路线”，单指按住拖动即可画线；绘制过程中可以用双指缩放或拖动地图，松开双指后继续单指画线。可多次绘制，点“结束绘制”退出绘制模式。
4. 输入速度（km/h），可选循环，点击“开始模拟”。
5. 模拟中可暂停/继续/停止；通知栏也可控制。停止后真实定位会自动恢复。
6. 摇杆模式：点标题栏齿轮 → 设置 → 模拟模式 → **摇杆模式**（需要悬浮窗权限）。返回主界面点“开始模拟”，悬浮窗中只出现一个半透明摇杆和 `−/+` 速度按钮；拖动控制方向，速度用设定值，松开摇杆即停。摇杆模式不要求先画路线。
   - 摇杆头显示当前速度（km/h）和微弱的“锁”提示；点摇杆头切换锁定/解锁。
   - 锁定后松手仍沿当前方向移动，摇杆保持偏转；再点摇杆头停止。
   - 锁定后再拖动摇杆可直接改变方向：摇杆从当前位置跟随手指平滑移动，不会跳到手指落点；松手后摇杆平滑固定在新方向并继续移动。
   - `−/+` 每次调整 1 km/h；悬浮窗顶部的小横条用于拖动整个摇杆控件。
7. 悬浮速度窗：设置 → 悬浮速度窗（首次会跳到系统“显示在其他应用上层”授权页）。路线模式下模拟运行时会显示速度与快捷切换；摇杆模式下强制开启并显示摇杆。
   - 应用重新安装后系统可能重置悬浮窗权限，此时开关会提示授权；按提示重新授权即可。
8. 地图图层：设置 → 地图图层，选择 OpenStreetMap / 自定义 XYZ；返回地图后自动应用并换算已绘制的路线。
9. 自检：设置 → 自检，检查开发者选项、模拟位置应用、定位服务、provider 注入和系统回读。
10. 关于：设置 → 关于，可查看当前版本（1.0）、开发者 GitHub、项目开源地址，以及检查更新。
    - 检查更新请求 GitHub Releases；国内外网络差异可能导致失败，失败时会给出发布页入口。

首次启动若提示没有权限，按应用内对话框提示打开开发者设置即可。


## 实现原理

### 1. 无 ROOT 模拟定位

`MockLocationService` 是前台服务（`foregroundServiceType="location"`），启动后：

1. `LocationManager.addTestProvider(GPS / NETWORK / FUSED, ...)` 注册测试 provider；
2. 每 200 ms 调用 `setTestProviderLocation()` 写入当前模拟位置；
3. `Location` 中填充经纬度、海拔、精度、方向、速度、时间、`elapsedRealtimeNanos`，GPS 额外带 `satellites` extras；
4. 停止时 `setTestProviderEnabled(false)` + `removeTestProvider()`。

这与 GoGoGo 的 `ServiceGo` 做法一致；API 31+ 使用 `ProviderProperties` 的功率/精度常量，旧版本使用 `Criteria` 常量。

### 2. 坐标系

- 地图上的绘制点、路线、标记与地图中心统一使用当前图层的坐标系。
- 内置 OpenStreetMap 是 WGS-84；自定义 GCJ-02 图层需要转换。
- 只有与系统定位交互时才转换：启动模拟时把 GCJ-02 路线转换为 WGS-84；显示模拟位置时把 WGS-84 转回 GCJ-02。
- 切换图层且坐标系不同时，自动换算已有路线和地图中心，避免偏移。
- 这与 GoGoGo 把百度 BD-09 坐标转成 WGS-84 再喂给定位 API 的处理是同一类问题。

### 3. 路线推进

纯 Java 几何层不依赖 Android：

- `GeoMath`：Haversine 距离与方位角；
- `RoutePath`：累计距离、按行进距离插值、前后几米差分计算朝向；
- `RouteSimulator`：`traveled += speed × Δt`，支持暂停、完成、循环取模；
- `RouteSimplifier`：Douglas-Peucker 简化手绘点。

服务用 `SystemClock.elapsedRealtime()` 计算真实 Δt，避免 Handler 延迟累积误差。

### 4. 摇杆模式

- 设置里保存 `simulation_mode`，启动时由主界面写入服务 Intent。
- 摇杆模式不构造路线；服务从地图中心（WGS-84）开始，按 `speed × Δt` 沿摇杆方向调用 `GeoMath.destinationPoint()` 推进。
- 摇杆只输出方向与 active 状态，速度固定为界面/悬浮窗设定的 km/h；松手或暂停即停止移动。
- 摇杆绘制在现有的 `TYPE_APPLICATION_OVERLAY` 悬浮窗内，因此切到其他应用也能继续控制。
- 锁定状态由摇杆视图维护：点摇杆头切换；解锁时抬手归零并停止；锁定后抬手保留摇杆偏转并继续上报 active，服务继续按固定速度移动。

## 常见问题

### 开发者选项列表里没有“GoWithLine”

“选择模拟位置信息应用”的列表只会列出在清单里声明了 `android.permission.ACCESS_MOCK_LOCATION` 的应用。本项目已在清单中声明该权限（它不会被授予，仅用于让应用进入列表），请确认安装的是最新构建的 APK：

- 从 [Releases](https://github.com/99galaxy/GoWithLine/releases/latest) 下载最新的 `GoWithLine-1.0.apk`；
- 部分 ROM 会缓存列表，重新打开设置或重启手机后再看；
- 如果仍然没有，可以通过 ADB 直接授权：
  `adb shell appops set com.gowithline.mock android:mock_location allow`
  （需要先安装应用并授予定位权限）。

### 模拟已启动，但地图/其他应用的位置不变

先打开应用右上角的“帮助 → 自检”，点“复制”把结果发出来。自检会显示：

- 开发者选项、模拟位置应用是否选中；
- 系统定位服务、Wi-Fi、精确位置权限；
- GPS / NETWORK / FUSED 三个测试 provider 是否添加成功；
- 已注入次数；
- **系统回读**：服务注册了一个位置监听器，如果系统真的接受了我们的测试位置，这里会显示“成功 N 次”。如果回读一直是 0，说明系统层面没有应用注入，问题在权限/ROM，而不是 UI。

常见原因与处理：

1. **没有在开发者选项里选中“GoWithLine”**：自检会显示“未选中”。到开发者选项重新选择。
2. **系统定位关掉了**：自检显示“已关闭”。打开定位后重试。
3. **只有近似位置权限**：自检显示“未授予精确位置”。到应用设置改为精确位置。
4. **Wi-Fi 开启**：部分机型会被 Wi-Fi/融合定位覆盖，出现闪回。关闭 Wi-Fi（或关闭 Wi-Fi 扫描）后重试。
5. **provider 添加失败**：自检会带上异常信息。常见于厂商 ROM 对 `addTestProvider` 的限制。
6. **系统回读成功，但某个 App 仍不变**：该 App 可能使用 Google Play Services 的 FusedLocationProvider、自带定位 SDK 或主动检测 `isFromMockProvider()`。这类应用在无 ROOT 条件下通常无法模拟。

可进一步用 ADB 确认：

```bash
adb shell appops get com.gowithline.mock android:mock_location
adb logcat -d -s GoWithLineService | tail -100
adb shell dumpsys location | grep -A4 -i "last location"
```

如果自检里 GPS/NETWORK 成功、已注入次数在增长，但“系统回读”始终为 0，请把自检文本和 `adb logcat -s GoWithLineService` 的输出发出来，这能直接定位是权限被 ROM 拦截还是 provider 参数被拒绝。

### 绘制路线时如何缩放/拖动地图

绘制模式下：

- **单指**：画线；
- **双指**：缩放和拖动地图。开始双指操作时，当前未抬手的那一段笔画会被丢弃，避免误画；松开双指后重新单指画线即可。

### 地图一片空白 / 瓦片加载失败

内置只提供 OpenStreetMap，瓦片可能被网络、地区或服务方策略影响。若无法加载，请到设置 → 地图图层填写自己的 XYZ 瓦片模板（使用你有权使用的服务）。本项目不内置离线地图，也不分发瓦片数据。

## 更新日志

### 1.0

首个正式版。

- 手绘多段路线，绘制中可双指缩放/拖动；按 km/h 恒定速度沿折线平滑移动。
- 向 GPS / NETWORK / FUSED 三个测试 provider 注入；暂停/继续/停止/循环。
- 摇杆模式与方向锁定；悬浮速度窗；OpenStreetMap + 自定义 XYZ 图层。
- 内置自检；停止后移除测试 provider，恢复真实定位。
- GPL-3.0 开源，无账号、无广告、无自建后端，Android 8.0+。

## 一点个人的碎碎念

老实拖摇杆，很枯燥吧？.jpg

之前一直在用影梭模拟移动，但自己拖摇杆在有些时候还是不太方便，想复现一条固定路线和速度更麻烦。于是拉着 AI 一起搞了这个可以自定义路线的小工具。

况且影梭已经转闭源了~

测试 provider 的思路来自影梭，地图与路线推进是独立实现。

欢迎 issue 和 PR~

## 构建

环境：JDK 17+、Android SDK Platform 36、Build-Tools 36.0.0。

```bash
cd GoWithLine
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（debug 签名，仅供本机调试）。

签名发布：

1. 在仓库外生成并保管 keystore（丢失后无法再发布覆盖升级）。
2. 在 Gradle 用户目录的 `gradle.properties` 配置（默认 `~/.gradle`；如果设置了 `GRADLE_USER_HOME`，用该目录。不要把密钥写进仓库）：

```properties
GOWITHLINE_STORE_FILE=<path>/gowithline-release.jks
GOWITHLINE_STORE_PASSWORD=...
GOWITHLINE_KEY_ALIAS=gowithline
GOWITHLINE_KEY_PASSWORD=...
```

3. 构建：

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

## 目录结构

```
app/src/main/java/com/gowithline/mock/
├─ MainActivity.java               地图、绘制、速度、状态与权限
├─ geo/                            纯 Java 几何、坐标转换、路径、模拟器
├─ map/                            osmdroid 图层与 XYZ 瓦片源
├─ service/MockLocationService.java 前台服务、测试 provider、通知
├─ ui/DrawOverlayView.java         手绘覆盖层
└─ util/                           模拟位置检测、格式化
```

## 隐私

- 没有账号、没有自建后端、没有广告或统计 SDK；路线、速度、图层和地图中心只保存在本机的 SharedPreferences。
- 内置 OpenStreetMap 瓦片由 OpenStreetMap France 的 HOT 服务提供；使用自定义 XYZ 时，请求发往你填写的服务器。两者都会向对应服务暴露你的 IP 和请求的坐标。

详见 [PRIVACY.md](PRIVACY.md)。

## 免责声明

本应用通过 Android 调试 API 模拟位置，仅用于开发测试、教学演示与定位功能调试。请勿用于游戏作弊、考勤/校园跑或其他违规用途。部分应用可以检测模拟位置，不同厂商系统也可能存在限制；使用即表示你了解并自行承担相关责任。

## 许可证

Copyright (C) 2026 99galaxy

本项目采用 [GNU General Public License v3.0](LICENSE)（GPL-3.0）开源。SPDX 标识：`GPL-3.0-only`。

```
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

“GoWithLine”“路线模拟”名称与图标不随代码授权；这是 GPLv3 §7(e) 允许的商标/标识保留，不构成对 GPL 许可的额外限制。第三方库、地图数据和瓦片的许可/条款见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [NOTICE](NOTICE)。

## 致谢
- [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo)：无 ROOT 模拟定位思路与坐标系处理参考。
- [osmdroid](https://github.com/osmdroid/osmdroid)：地图渲染（Apache-2.0）。
- 地图数据：© OpenStreetMap contributors（ODbL）。内置瓦片为 HOT 样式，由 OpenStreetMap France 提供。

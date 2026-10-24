# 隐私说明

GoWithLine 是一个本地运行的 Android 定位测试工具。它没有账号系统、没有自建后端，也没有广告或统计 SDK。

## 应用保存了什么

以下内容只保存在本机的 SharedPreferences（`gowithline`）中，不会自动上传：

- 绘制的路线与坐标系
- 速度、循环、跟随、悬浮窗开关与位置
- 地图图层、自定义 XYZ 模板与坐标系
- 地图中心与缩放级别
- 模拟模式（路线 / 摇杆）
- 使用声明是否已同意

卸载应用会删除这些数据。

## 网络请求

应用会在以下情况访问网络：

1. **地图瓦片**：内置图层只有 OpenStreetMap（HOT 镜像，由 OpenStreetMap France 提供）；选择自定义 XYZ 时，请求发往你填写的服务器。请求会暴露你的 IP 地址和所请求的瓦片坐标（可据此推断大致位置）。自定义 XYZ 建议使用 HTTPS；填写 HTTP 地址时，瓦片请求会以明文传输。
2. **检查更新**：在“设置 → 关于 → 检查更新”时访问 GitHub Releases API。

除此之外，应用不发送任何数据。

## 权限

- **定位**：读取系统定位状态、注册测试 provider，并在地图上显示当前位置。
- **悬浮窗**：在其他应用上方显示速度窗或摇杆。
- **通知**：显示前台服务状态和暂停/继续/停止按钮。
- **WakeLock**：模拟运行期间保持 CPU 唤醒。

## 第三方

OpenStreetMap 数据 © OpenStreetMap contributors（ODbL）；内置瓦片为 HOT 样式，由 OpenStreetMap France 提供；自定义 XYZ 的条款由你选择的服务商决定。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本项目与这些提供商没有合作或授权关系。

## 联系

通过 [GitHub Issues](https://github.com/99galaxy/GoWithLine/issues) 联系。

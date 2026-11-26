# AnyGo

AnyGo 是一款基于 Android Mock Location API + 高德地图 SDK 的开源虚拟定位工具，支持地图选点、地址搜索、坐标输入、历史记录和悬浮摇杆连续移动。

> 本项目 Fork 自 [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo)，遵循其 **GPL-3.0** 许可证开源。感谢原作者 [ZCShou](https://github.com/ZCShou) 的优秀工作。
> 本仓库在其基础上做了较大改动（详见下文），并同样以 **GPL-3.0** 协议开源发布。

## 免责声明

1. 本软件专为学习 Android 开发使用，不会收集任何用户数据。
2. 严禁利用本软件侵犯他人隐私权或从事任何违法违规活动，由此产生的一切后果由使用者自行承担。
3. 使用本软件前请阅读并同意[高德地图开放平台服务协议与隐私政策](https://lbs.amap.com/pages/privacy/)。

## 功能

- **地图选点**：点击 / 长按地图选择位置，支持普通图与卫星图切换
- **地址搜索**：基于高德输入提示（Inputtips）的实时搜索建议
- **坐标输入**：支持高德（GCJ-02）与 GPS（WGS-84）两种坐标系直接输入
- **模拟定位**：向 GPS / 网络定位 Provider 注入模拟位置（系统 Mock Location API）
- **悬浮摇杆**：圆形 / 按钮两种样式，支持步行 / 跑步 / 骑行三档速度连续移动
- **历史记录**：位置与搜索历史保存、回放（保留天数可配置）
- **参数设置**：海拔、经纬度随机偏移、历史记录有效期等

## 与上游（GoGoGo）的主要差异

| 项目 | 上游 GoGoGo | 本仓库 AnyGo |
|---|---|---|
| 地图 SDK | 百度地图 | **高德地图**（3dmap-location-search 10.1.300） |
| 包名 | `com.zcshou.gogogo` | `com.sunyun.gogogo` |
| 地图 Key | 自备百度 AK | 自备高德 Key |
| 应用内更新 | 检查上游 Releases | 预留（待本仓库发布稳定版后接入） |
| 授权 / 会员体系 | 无 | 无（永久免费，Key 与额度完全归属使用者自己） |

## 构建

### 准备高德 Key

1. 到[高德开放平台控制台](https://console.amap.com)创建「**Android 平台**」类型的 Key；
2. 绑定信息：
   - PackageName：`com.sunyun.gogogo`
   - 发布版 SHA1：`32:2F:E6:05:6C:2F:11:6D:CD:ED:AC:FF:23:02:5A:29:68:FD:45:D2`（仓库自带 `keystore/GoGoGo.jks`，密码见 `app/build.gradle`）
3. 在 `local.properties` 中填写：

```properties
sdk.dir=<你的 Android SDK 路径>
MAPS_API_KEY=<你的高德 Key>
```

### 编译

```bash
./gradlew assembleRelease
# 产物: app/build/outputs/apk/release/AnyGo_1.0.0_arm64-v8a_release.apk
```

要求：JDK 17+、Android SDK（compileSdk 34）、Android 8.1+ 真机（arm64-v8a）。

使用前需在系统「开发者选项 → 模拟位置信息应用」中选中本应用。

## CI / 自动发版

仓库内置 GitHub Actions（`build-release.yml`）：

- 推送任意标签（如 `v1.0.0`）→ 自动构建 Release APK（使用仓库内 keystore 签名，`MAPS_API_KEY` 从仓库 Secret 注入）并创建 GitHub Release；
- 手动触发（workflow_dispatch）→ 仅构建验证，不发布。

## 问题反馈

请到 [Issues](https://github.com/fejxc/GoGoGo/issues) 提交问题。

## 许可证

本项目基于 [GPL-3.0](LICENSE) 协议发布。

- 原项目版权归 [ZCShou](https://github.com/ZCShou) 所有；
- 本仓库的修改与新增代码同样以 GPL-3.0 协议开源，使用本仓库代码请遵守同一协议并保留相应声明。

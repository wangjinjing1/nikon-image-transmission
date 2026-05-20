# 尼康图传

尼康图传是一款用于手机端连接尼康相机并传输照片的 Android APP。当前目标机型：

- Nikon Z30：支持 Wi-Fi AP 模式。
- Nikon Z5II：支持 Wi-Fi AP 模式和 Wi-Fi STA 模式。

## 功能

- 连接相机后预览照片缩略图。
- 支持单选、多选、全选和批量下载。
- 下载尺寸支持 `2MP`、`8MP`、`原始格式`。
- 照片列表很多时，下载尺寸选择和下载按钮会固定在顶部，方便随时操作。
- 支持后台线程下载，下载过程中可以继续浏览照片或切换到其他应用。
- 支持在设置中修改下载目录。

## 下载目录

默认保存目录：

```text
Pictures/尼康图传
```

在 APP 左侧设置按钮中可以修改目录名。修改后会保存到：

```text
Pictures/<你设置的目录名>
```

说明：

- Android 10 及以上通过 `MediaStore` 保存到公共 `Pictures` 目录，通常不需要弹出文件写入权限。
- Android 9 及以下会请求 `WRITE_EXTERNAL_STORAGE` 权限。

## 环境要求

- Node.js
- npm
- Android Studio / Android SDK
- JDK

当前项目本机 Android SDK 配置在：

```text
D:\Job Tools\Android Studio\Sdk
```

该路径写在本地文件 `android/local.properties` 中，此文件不会提交到 Git。

## 一键打包

根目录提供了 Windows 打包脚本：

```text
打包APK.bat
```

双击脚本，或在项目根目录运行：

```powershell
.\打包APK.bat
```

脚本会中文提示输入版本号：

- 默认从 `1.0.0` 开始。
- 直接回车会使用默认版本号。
- 打包成功后自动递增补丁版本，例如 `1.0.0` 的下一次默认值为 `1.0.1`。
- 如果用户输入 `1.2.3`，本次 APK 名称为 `尼康图传-1.2.3.apk`，下次默认版本变成 `1.2.4`。

APK 输出目录：

```text
release/
```

示例输出：

```text
release/尼康图传-1.0.0.apk
```

## npm 打包命令

也可以不用 BAT，直接运行：

```powershell
npm install
npm run package:android
```

Gradle 默认 debug APK 输出路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 本地预览

```powershell
npm install
npm run dev
```

浏览器预览会进入演示模式，用于检查 UI、选择照片、下载进度和设置面板。真机安装后会通过 Android 原生 `NikonCamera` 插件连接相机。

## 相机连接提示

AP 模式：

```text
先在相机开启 Wi-Fi 连接，再让手机连接相机热点。
默认相机地址：192.168.1.1
```

STA 模式：

```text
相机和手机连接到同一个路由器。
在 APP 中填写相机获得的局域网 IP。
```

原始格式会保留 JPG/NEF 原文件；`2MP` 和 `8MP` 会优先对 JPG 进行本地缩放后保存。

## 主要代码位置

- 前端界面：`src/App.tsx`
- 相机前端接口：`src/nikonCamera.ts`
- Android 原生插件：`android/app/src/main/java/com/nikon/transfer/NikonCameraPlugin.java`
- PTP/IP 客户端：`android/app/src/main/java/com/nikon/transfer/NikonPtpIpClient.java`
- 打包脚本入口：`打包APK.bat`
- 打包脚本逻辑：`scripts/package-apk.ps1`

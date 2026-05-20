# 尼康图传

尼康图传是一个面向手机端的相机照片传输 APP 原型，目标支持 Nikon Z30 与 Nikon Z5II：

- Z30：Wi-Fi AP 模式直连相机热点。
- Z5II：Wi-Fi AP 模式与 Wi-Fi STA 模式。
- 连接后预览相机照片缩略图。
- 支持多选与批量下载。
- 下载尺寸支持 2MP、8MP、原始格式。

## 一键打包

```powershell
npm install
npm run package:android
```

生成路径：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

如果当前电脑未安装 Android SDK，请先安装 Android Studio，并配置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`。

## 本地预览

```powershell
npm install
npm run dev
```

浏览器预览会进入演示模式，方便检查 UI、批量选择和下载进度。真机安装后会通过 Android 原生 `NikonCamera` 插件连接相机。

## 相机连接提示

- AP 模式：先在相机开启 Wi-Fi 连接，再让手机连接相机热点，默认相机地址为 `192.168.1.1`。
- STA 模式：相机和手机连接到同一个路由器，在 APP 中填写相机获得的局域网 IP。
- 原始格式会保留 JPG/NEF 原文件；2MP 与 8MP 会优先对 JPG 进行本地缩放后保存。

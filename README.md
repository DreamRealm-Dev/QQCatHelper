# QQCatHelper（开源重置版）

一个不依赖原版 `QQ喵喵助手` 代码的 Android 无障碍辅助小工具。

> 功能：在 QQ 输入框中自动把文字替换成“喵喵风”，例如：
> - “我” → “本喵”
> - “你” → “主人”
> - 句尾加“喵”
> - 末尾追加随机颜文字

## 功能

- 标点触发 / 实时处理两种模式
- 开关：断句加喵、我→本喵、你→主人、随机颜文字
- 支持自定义颜文字，每行一个
- 只监听 QQ / QQ 国际版
- 通过 Android AccessibilityService 实现，不需要 root

## 项目结构

```text
QQCatHelper/
├── app/src/main/java/io/github/qqcathelper/
│   ├── MainActivity.java          # 设置界面
│   ├── CatConfig.java             # 配置读写
│   ├── TextProcessor.java         # 文本转换算法
│   └── CatAccessibilityService.java # 无障碍服务核心
├── app/src/main/res/
│   ├── layout/activity_main.xml      # Material 3 界面
│   ├── xml/cat_accessibility_service.xml
│   └── values/strings.xml
├── build.gradle
└── settings.gradle
```

## 构建

### 环境要求

- JDK 21（AGP 8.6 需要；GraalVM / JDK 25 会因 `jlink` 不兼容而失败）
- Android SDK，并已安装 `platforms;android-34` 和 `build-tools;34.0.0`
- 本项目自带 Gradle 8.14.5 Wrapper

如果本机 `JAVA_HOME` 不是 JDK 21，命令行构建时请显式指定，例如：

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

SDK 位置写在本机 `local.properties`（已 gitignore），例：

```properties
sdk.dir=C\:\\Android\\Sdk
```

用 Android Studio 打开本目录，等待 Gradle 同步后直接运行到手机即可。

命令行构建：

```bash
./gradlew assembleDebug
```

生成调试 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

生成非调试 release APK（已用 debug 签名，可在本机覆盖安装）：

```bash
./gradlew assembleRelease
```

```text
app/build/outputs/apk/release/app-release.apk
```

## 使用

1. 安装并打开 App
2. 点击“前往开启无障碍服务”
3. 在系统设置中开启 `QQCatHelper`
4. 返回 App，按需调整开关并保存
5. 打开 QQ 输入文字测试

## 权限说明

应用只申请无障碍服务权限，不申请网络权限，不上传任何数据。

> 无障碍服务是高危权限，请仅在你信任的源码构建下使用。

## License

[MIT](./LICENSE)

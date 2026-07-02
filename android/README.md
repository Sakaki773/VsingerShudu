# V家数独 Android

这是 `V家数独` 的原生 Android 版，使用 Java + 自定义 View 实现，不依赖 Gradle、Compose 或 Python 运行时。

## 目录

```text
android/
├── src/main/AndroidManifest.xml
├── src/main/java/com/vsinger/sudoku/MainActivity.java
├── src/main/res/drawable-nodpi/
├── src/main/res/mipmap-*/
├── src/main/res/values/
└── build/
```

`build/` 是构建产物目录，不建议提交到 Git。

## 构建

从项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\build_android_apk.ps1
```

脚本会依次调用：

- `aapt2`
- `javac`
- `d8`
- `zipalign`
- `apksigner`

输出：

```text
android/build/outputs/VsingerSudoku-debug.apk
```

## 安装

查看设备：

```powershell
& "G:\Android\Sdk\platform-tools\adb.exe" devices -l
```

安装到指定设备：

```powershell
& "G:\Android\Sdk\platform-tools\adb.exe" -s <deviceSerial> install -r -d --no-incremental .\android\build\outputs\VsingerSudoku-debug.apk
```

启动：

```powershell
& "G:\Android\Sdk\platform-tools\adb.exe" -s <deviceSerial> shell am start -n "com.vsinger.sudoku/.MainActivity"
```

## 资源

背景图片来自根目录 `assets/android/`，同步到：

```text
android/src/main/res/drawable-nodpi/
```

App 图标生成到：

```text
android/src/main/res/mipmap-mdpi/
android/src/main/res/mipmap-hdpi/
android/src/main/res/mipmap-xhdpi/
android/src/main/res/mipmap-xxhdpi/
android/src/main/res/mipmap-xxxhdpi/
```

更换图片后，先运行根目录 `prepare_assets.py`，再复制 Android 规格图片到资源目录并重新构建 APK。

## 真机验收重点

- 状态栏不能遮挡标题、计时器和按钮。
- 顶部布局尽量中心对称。
- 欢迎页和游玩页都能切换壁纸。
- 游玩页背景可见但不影响棋盘可读性。
- 返回键：游玩页先确认返回主界面，主界面再确认退出。
- 数字剩余数量为 0 时按钮置灰，但状态变化后要能恢复原色。


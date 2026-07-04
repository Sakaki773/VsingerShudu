# V家数独

洛天依主题 9x9 数独游戏，包含 PC Tkinter 原型和原生 Android APK 版本。主题色以洛天依应援色 `#66CCFF` 为主，界面使用淡化/虚化背景图，尽量保证主题感和可读性平衡。

## 技术实现

- PC 桌面版：使用 Python 编写，`vsinger_sudoku.py` 基于 Tkinter 实现窗口、欢迎页、棋盘绘制、数字输入、辅助数字和常用操作。
- 图片处理：使用 Pillow 加载、裁切和淡化背景图；`prepare_assets.py` 用于生成 PC/Android 两套规格素材。
- 数独逻辑：题目和答案在 Python 端随机生成，并通过求解计数检查唯一解。
- Android 版：使用 Java 原生 Android 实现，`MainActivity.java` 通过自定义 View 绘制界面和处理触控。
- Android 构建：不依赖 Gradle，`build_android_apk.ps1` 直接调用 Android SDK 工具链中的 `aapt2`、`javac`、`d8`、`zipalign` 和 `apksigner` 生成 Debug APK。
- 当前仓库未提供 PC EXE 打包脚本；PC 端主要用于本地原型预览，移动端交付物为 APK。

## 当前状态

- PC 端：`vsinger_sudoku.py`，用于快速预览和交互调整。
- Android 端：原生 Java 自绘 View，不依赖 Python/Tkinter，也不依赖 Gradle。
- APK 输出：`android/build/outputs/VsingerSudoku-debug.apk`。
- 已实现欢迎页、游玩页背景、切换壁纸、App 图标、返回确认、三档难度和常用辅助按钮。

## 玩法规则

- 难度：简单、标准、大师。
- 题目随机生成，并检查唯一解。
- 简单更容易，标准兼顾难度，大师会倾向生成更需要推理的题面。
- 题面尽量中心对称。
- 错误只标红提示，不做惩罚。

## 操作方式

- 先点击数字按钮，再点击九宫格。
- `结果数字` 模式：点击空格填入当前数字；同数字再次点击会清除；不同数字会覆盖。
- `辅助数字` 模式：小数字按格内 3x3 固定位置显示，`1` 在左上角，`5` 在正中间。
- 有结果数字时，该格辅助数字被冻结并隐藏；必须先清除结果数字，辅助数字才会重新显示和可编辑。
- 选中某个结果数字后，棋盘内已有相同结果数字会高亮。
- 按下棋盘格时临时高亮所在行、列、宫，松开后取消。
- 结果数字按钮右上角显示该数字剩余可填数量；为 0 时置灰提示，但仍可点击使用。

## 辅助功能

- 撤销
- 提示
- 检查
- 重开
- 新题
- 填充全部辅助数字
- 清空全部辅助数字
- 清空结果数字
- 全部清空
- 欢迎页和游玩页切换壁纸

## PC 运行

使用 `envs2` 环境：

```powershell
.\run_game.ps1
```

或直接运行：

```powershell
& "E:\anaconda3\envs\envs2\python.exe" .\vsinger_sudoku.py
```

## 打包命令速查

重新构建 Android Debug APK：

```powershell
powershell -ExecutionPolicy Bypass -File .\build_android_apk.ps1
```

重新生成 PC/Android 两套规格素材：

```powershell
& "E:\anaconda3\envs\envs2\python.exe" .\prepare_assets.py
```

## Android 构建

当前 Android 构建使用本机工具：

- Android SDK：`G:\Android\Sdk`
- Build-Tools：`36.0.0`
- AVD：`G:\Android\avd\VsingerSudoku_API36.avd`

重新构建 Debug APK：

```powershell
powershell -ExecutionPolicy Bypass -File .\build_android_apk.ps1
```

输出文件：

```text
android/build/outputs/VsingerSudoku-debug.apk
```

## 安装到手机

先查看设备序列号：

```powershell
& "G:\Android\Sdk\platform-tools\adb.exe" devices -l
```

安装到指定设备：

```powershell
& "G:\Android\Sdk\platform-tools\adb.exe" -s <deviceSerial> install -r -d --no-incremental .\android\build\outputs\VsingerSudoku-debug.apk
```

启动 App：

```powershell
& "G:\Android\Sdk\platform-tools\adb.exe" -s <deviceSerial> shell am start -n "com.vsinger.sudoku/.MainActivity"
```

如果同时连接真机和模拟器，务必使用 `-s <deviceSerial>` 指定目标设备。

## 电脑模拟器预览

```powershell
powershell -ExecutionPolicy Bypass -File .\view_apk_on_emulator.ps1
```

该脚本只会操作 `emulator-5554`，用于启动 `VsingerSudoku_API36` 模拟器、安装 APK 并打开 App。

## 素材

素材目录：

- `assets/selected/`：当前游戏随机背景池。
- `assets/iqoo/`：用户提供的候选洛天依图片。
- `assets/candidates/`：网络候选图，不会自动进入游戏随机池。
- `assets/originals/`：旧版候选或备用原图。
- `assets/pc/`：PC 规格生成图。
- `assets/android/`：Android 规格生成图。

重新生成 PC/Android 两套规格素材：

```powershell
& "E:\anaconda3\envs\envs2\python.exe" .\prepare_assets.py
```

生成结果：

- `assets/pc/welcome_*.jpg`
- `assets/pc/game_background_*.jpg`
- `assets/android/welcome_*.jpg`
- `assets/android/game_background_*.jpg`

Android 使用的图片需复制到：

```text
android/src/main/res/drawable-nodpi/
```

App 图标来自：

```text
assets/iqoo/a03dfd70b64132d3b6e596feff5e35096cbedd05.jpg
```

并生成到：

```text
android/src/main/res/mipmap-*/
```

当前图片仅用于本地原型预览；公开发布 APK/EXE 前，建议统一替换为授权明确的素材。

## 仓库建议

建议提交源码、脚本、README、Android 资源和必要素材；不要提交构建缓存、APK、DEX、class、debug keystore、`__pycache__` 等产物。

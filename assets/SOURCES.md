# 素材来源记录

当前素材用于本地原型预览。公开发布 EXE/APK 前，请替换为授权明确、可商用或你自己拥有使用权的图片。

## 仓库内素材

当前仓库保留：

- `assets/selected/`：已筛选进入游戏随机背景池的图片。
- `assets/android/`：Android 规格生成图。
- `assets/pc/`：PC 规格生成图。
- `assets/iqoo/a03dfd70b64132d3b6e596feff5e35096cbedd05.jpg`：当前 App 图标来源图。
- `assets/luotianyi_hero.jpg`、`assets/luotianyi_soft.jpg`：早期备用主题图。

## 本地候选池

以下目录默认被 `.gitignore` 排除，用于本地筛选，不随仓库提交：

- `assets/candidates/`
- `assets/originals/`
- `assets/iqoo/` 中除当前图标来源图以外的图片

如果后续确认某张图片要进入游戏随机池，请复制到 `assets/selected/`，然后运行：

```powershell
& "E:\anaconda3\envs\envs2\python.exe" .\prepare_assets.py
```

## 素材处理顺序

`prepare_assets.py` 会优先读取：

1. `assets/selected/`
2. `assets/originals/`
3. `assets/luotianyi_hero.jpg` 和 `assets/luotianyi_soft.jpg`

它不会自动使用 `assets/candidates/`。


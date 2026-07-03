# Vtt2LrcApp

一个安卓本地工具：把 `.vtt` 字幕/歌词文件转换成网易云音乐更容易识别的 `.lrc` 歌词文件。

## 功能

- 选择文件夹，批量递归转换所有 `.vtt`
- 同目录生成同名 `.lrc`
- 默认覆盖已有 `.lrc`
- 默认写入 UTF-8 BOM
- 使用 LF 换行
- 转换规则按用户提供的 Python 脚本实现

## 在 GitHub 自动打包 APK

1. 把本项目所有文件上传到 GitHub 仓库根目录。
2. 打开仓库顶部的 `Actions`。
3. 选择 `Build Android APK`。
4. 点 `Run workflow`。
5. 等待完成后，在页面底部 `Artifacts` 下载 `Vtt2Lrc-debug-apk`。
6. 解压后得到 `app-debug.apk`，传到安卓手机安装。

## 使用方式

1. 打开 App。
2. 点“选择文件夹批量转换”。
3. 选择包含 `.vtt` 的音乐目录。
4. App 会在每个 `.vtt` 同目录生成同名 `.lrc`。

> 安卓权限限制下，单文件模式通常不能稳定写回原目录，所以推荐使用文件夹模式。

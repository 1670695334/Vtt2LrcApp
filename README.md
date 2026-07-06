# Vtt2LrcApp

一个 Android 8+ 本地工具，用来把 WebVTT（`.vtt`）歌词/字幕文件转换成 LRC（`.lrc`）歌词文件，方便网易云音乐本地歌词滚动识别。

## 功能

- 使用 Android SAF（`ACTION_OPEN_DOCUMENT_TREE`）选择并授权文件夹。
- 首次授权后会持久化保存权限，下次打开 App 会自动进入上次授权的目录。
- App 内置目录浏览界面，支持进入子目录和返回上级目录。
- 默认详细单列展示，也支持切换为紧凑、网格布局。
- 支持按名称、大小、日期、类型排序，并可切换升序/降序。
- 单击 `.vtt` 文件会弹出确认转换窗口，可修改输出 `.lrc` 文件名。
- 长按任意文件进入多选模式。
- 多选转换时只转换选中的 `.vtt` 文件，默认生成同名 `.lrc`。
- 多选删除时不限制文件类型，选中什么就删除什么，会先弹窗确认。
- 默认覆盖已有 `.lrc` 文件。
- 输出 `.lrc` 使用 UTF-8 with BOM 编码，LF 换行。
- 生成头部：

```text
[ti:文件名]
[ar:未知歌手]
```

## 转换规则

示例 VTT：

```text
00:00:01.500 --> 00:00:05.000
歌词
```

转换为 LRC：

```text
[0:01.50] 歌词
```

## 权限说明

本项目不申请 `MANAGE_EXTERNAL_STORAGE`。普通 Android App 无法静默获取整个文件系统权限，必须通过系统文件夹选择器由用户点一次“使用此文件夹”授权。

授权成功后，App 会保存这个目录的持久化权限；只要系统没有撤销权限，下次打开 App 会直接进入上次目录，不需要重复授权。

## GitHub Actions 打包 APK

1. 推送代码到 GitHub。
2. 打开仓库的 `Actions` 页面。
3. 等待 `Android APK` 工作流执行完成。
4. 如果构建结果是绿色对勾，点进最新运行记录。
5. 在页面底部 `Artifacts` 下载 `app-debug`。
6. 解压后得到 `app-debug.apk`，安装到安卓手机即可。

## 本地构建

如果本机已安装 Gradle：

```bash
gradle assembleDebug
```

生成位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 技术栈

- Kotlin
- AndroidX
- DocumentFile / SAF
- GitHub Actions

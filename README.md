Vtt2LrcApp
一款适配 Android 8 及以上系统的安卓应用，用于将 WebVTT（.vtt）歌词 / 字幕文件转换为 LRC（.lrc）歌词文件，适配网易云音乐本地滚动歌词功能。
功能特性
基于安卓存储访问框架 SAF（ACTION_OPEN_DOCUMENT_TREE）选择目标文件夹
支持开启递归扫描，检索文件夹内全部 .vtt 文件
在每一个 vtt 文件所在目录，生成同名 lrc 文件
默认覆盖已存在的同名 lrc 文件
输出文件采用带 BOM 的 UTF-8 编码，换行符为 LF
自动写入歌词头部标签：[ti:文件名]、[ar:未知歌手]；歌手栏中文会按照转换规则转为 Unicode 转义字符
时间戳转换：将 00:00:01.500 --> 00:00:05.000 格式时间轴，转换为标准 [0:01.50] 歌词内容 LRC 格式
使用 GitHub Actions 自动打包
将项目推送至 GitHub 仓库
打开仓库的 Actions 标签页
手动执行 Android APK 工作流，或提交代码自动触发打包流程
下载打包产物 app-debug
解压产物，使用其中的 app-debug.apk 安装包
本地编译打包
本地已安装配置 Gradle 时，执行如下命令：
bash
运行
gradle assembleDebug
编译生成的 APK 文件路径：
text
app/build/outputs/apk/debug/app-debug.apk
注意事项
本应用不会申请 MANAGE_EXTERNAL_STORAGE 全部文件管理权限，完全依靠 SAF 文件夹授权实现文件读写，兼容 Android 8 及以上系统的新版存储权限机制。

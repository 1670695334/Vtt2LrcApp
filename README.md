# Vtt2LrcApp

An Android 8+ app that converts WebVTT (`.vtt`) lyric/subtitle files to LRC (`.lrc`) files for local lyric scrolling in NetEase Cloud Music.

## Features

- Pick a folder with Android SAF (`ACTION_OPEN_DOCUMENT_TREE`).
- Recursively find `.vtt` files when enabled.
- Write same-name `.lrc` files in each `.vtt` file's own folder.
- Overwrite existing `.lrc` files by default.
- Output LRC as UTF-8 with BOM and LF line endings.
- Add `[ti:file name]` and `[ar:unknown singer]` headers. The app writes the artist value as Unicode escapes for the Chinese text required by the conversion rule.
- Convert timestamps like `00:00:01.500 --> 00:00:05.000` to `[0:01.50] lyric`.

## Build With GitHub Actions

1. Push this project to GitHub.
2. Open the repository's `Actions` tab.
3. Run the `Android APK` workflow, or push a commit to trigger it.
4. Download the `app-debug` artifact.
5. Inside the artifact, use `app-debug.apk`.

## Local Build

If Gradle is installed locally:

```bash
gradle assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

This app does not request `MANAGE_EXTERNAL_STORAGE`. It relies on SAF folder permissions, which is the compatible approach for Android 8+ and modern Android storage behavior.
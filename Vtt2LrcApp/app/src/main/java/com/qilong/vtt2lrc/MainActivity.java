package com.qilong.vtt2lrc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_PICK_FOLDER = 1001;
    private TextView logText;
    private int converted = 0;
    private int skipped = 0;
    private int failed = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("VTT转LRC");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("VTT转LRC");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("选择包含 .vtt 的文件夹，App会递归转换所有字幕，并在原目录生成同名 .lrc。输出为 UTF-8 BOM + LF 换行，适合网易云识别滚动歌词。");
        desc.setTextSize(15);
        desc.setPadding(0, 0, 0, dp(14));
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        Button pick = new Button(this);
        pick.setText("选择文件夹并转换");
        pick.setAllCaps(false);
        pick.setOnClickListener(v -> openFolderPicker());
        root.addView(pick, new LinearLayout.LayoutParams(-1, dp(52)));

        logText = new TextView(this);
        logText.setText("等待选择文件夹…");
        logText.setTextSize(14);
        logText.setPadding(0, dp(16), 0, 0);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logText);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
            }
            DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
            converted = 0;
            skipped = 0;
            failed = 0;
            logText.setText("开始转换…\n");
            if (folder == null || !folder.isDirectory()) {
                appendLog("选择的不是有效文件夹。");
                return;
            }
            processFolder(folder);
            appendLog(String.format(Locale.CHINA, "\n完成：成功 %d，跳过 %d，失败 %d", converted, skipped, failed));
        }
    }

    private void processFolder(DocumentFile folder) {
        DocumentFile[] files = folder.listFiles();
        for (DocumentFile file : files) {
            if (file.isDirectory()) {
                processFolder(file);
            } else if (file.isFile() && file.getName() != null && file.getName().toLowerCase(Locale.ROOT).endsWith(".vtt")) {
                convertOne(file, folder);
            }
        }
    }

    private void convertOne(DocumentFile vttFile, DocumentFile parentFolder) {
        String fileName = vttFile.getName();
        if (fileName == null) return;
        try {
            String title = stripVttSuffix(fileName);
            String lrcName = title + ".lrc";
            String vttContent = readText(vttFile);
            String cleaned = cleanVtt(vttContent);
            String lrcBody = vttToLrc(cleaned);

            if (!Pattern.compile("\\[\\d+:\\d{2}\\.\\d{2}]").matcher(lrcBody).find()) {
                skipped++;
                appendLog("跳过：" + fileName + "，无有效时间轴");
                return;
            }

            String finalContent = "[ti:" + title + "]\n[ar:未知歌手]\n" + lrcBody + "\n";
            DocumentFile old = parentFolder.findFile(lrcName);
            if (old != null) old.delete();
            DocumentFile lrcFile = parentFolder.createFile("text/plain", lrcName);
            if (lrcFile == null) throw new IllegalStateException("无法创建 " + lrcName);

            try (OutputStream out = getContentResolver().openOutputStream(lrcFile.getUri(), "wt")) {
                if (out == null) throw new IllegalStateException("无法写入 " + lrcName);
                out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                out.write(finalContent.replace("\r\n", "\n").replace("\r", "\n").getBytes(StandardCharsets.UTF_8));
            }
            converted++;
            appendLog("完成：" + fileName + " → " + lrcName);
        } catch (Exception e) {
            failed++;
            appendLog("失败：" + fileName + "，" + e.getMessage());
        }
    }

    private String readText(DocumentFile file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(file.getUri())) {
            if (in == null) throw new IllegalStateException("无法读取文件");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String cleanVtt(String s) {
        String cleaned = s.replaceFirst("^\\uFEFF", "");
        cleaned = cleaned.replaceFirst("(?s)^WEBVTT.*?\\n\\s*\\n", "");
        cleaned = cleaned.replaceAll("(?m)^\\s*#.*?\\n", "");
        return cleaned;
    }

    private String vttToLrc(String vttContent) {
        StringBuilder lrc = new StringBuilder();
        Pattern pattern = Pattern.compile(
                "(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) --> (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*(.*?)(?=\\n*\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> |$)",
                Pattern.DOTALL | Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(vttContent);
        while (matcher.find()) {
            String startTime = matcher.group(1);
            String lyric = matcher.group(3);
            String lrcStart = convertTime(startTime);
            String lyricClean = cleanLyric(lyric);
            if (!lyricClean.isEmpty()) {
                lrc.append(lrcStart).append(' ').append(lyricClean).append('\n');
            }
        }
        return lrc.toString().trim();
    }

    private String cleanLyric(String lyric) {
        String[] lines = lyric.split("\\R");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(t);
            }
        }
        return sb.toString();
    }

    private String convertTime(String vttTime) {
        String[] parts = vttTime.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        String[] secMs = parts[2].split("\\.");
        int totalMinutes = hours * 60 + minutes;
        String sec = leftPad2(secMs[0]);
        String ms = (secMs[1] + "000").substring(0, 2);
        return "[" + totalMinutes + ":" + sec + "." + ms + "]";
    }

    private String leftPad2(String s) {
        return s.length() >= 2 ? s : "0" + s;
    }

    private String stripVttSuffix(String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".vtt")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private void appendLog(String msg) {
        runOnUiThread(() -> logText.append(msg + "\n"));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

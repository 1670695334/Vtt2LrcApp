package com.example.vtt2lrc

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var overwriteBox: CheckBox
    private lateinit var recursiveBox: CheckBox
    private lateinit var bomBox: CheckBox

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            convertSingleFile(uri)
        }
    }

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            val folder = DocumentFile.fromTreeUri(this, uri)
            if (folder == null || !folder.isDirectory) {
                appendLog("选择的不是有效文件夹")
            } else {
                clearLog()
                appendLog("开始批量转换：${folder.name ?: "所选文件夹"}")
                val count = processFolder(folder)
                appendLog("")
                appendLog("批量转换完成，共处理 $count 个 VTT 文件")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleSharedFile(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedFile(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val title = TextView(this).apply {
            text = "VTT → LRC 转换器"
            textSize = 24f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFF111111.toInt())
        }

        val desc = TextView(this).apply {
            text = "按你的脚本规则转换：保留时间轴、生成同名 .lrc，默认 UTF-8 BOM + LF，适合网易云本地歌词滚动。"
            textSize = 14f
            setTextColor(0xFF555555.toInt())
            setPadding(0, dp(8), 0, dp(12))
        }

        val fileBtn = Button(this).apply {
            text = "选择单个 VTT 文件"
            setOnClickListener { openFilePicker() }
        }

        val folderBtn = Button(this).apply {
            text = "选择文件夹批量转换"
            setOnClickListener { openFolderPicker() }
        }

        overwriteBox = CheckBox(this).apply {
            text = "覆盖已有 LRC"
            isChecked = true
        }

        recursiveBox = CheckBox(this).apply {
            text = "递归子目录"
            isChecked = true
        }

        bomBox = CheckBox(this).apply {
            text = "写入 UTF-8 BOM（推荐，网易云更容易识别）"
            isChecked = true
        }

        logView = TextView(this).apply {
            text = "请选择 .vtt 文件或包含 .vtt 的文件夹"
            textSize = 14f
            setTextColor(0xFF222222.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        val scroll = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(title)
        root.addView(desc)
        root.addView(fileBtn, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(folderBtn, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(overwriteBox)
        root.addView(recursiveBox)
        root.addView(bomBox)

        val logTitle = TextView(this).apply {
            text = "转换日志"
            textSize = 17f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(logTitle)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/vtt", "text/plain", "application/octet-stream"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pickFileLauncher.launch(intent)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        pickFolderLauncher.launch(intent)
    }

    private fun handleSharedFile(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                convertSingleFile(uri)
            }
        }
    }

    private fun convertSingleFile(uri: Uri) {
        clearLog()
        val doc = DocumentFile.fromSingleUri(this, uri)
        val name = doc?.name ?: "selected.vtt"
        if (!name.lowercase().endsWith(".vtt")) {
            appendLog("请选择 .vtt 文件：$name")
            return
        }

        val parentMessage = "单文件模式会尝试在系统允许的位置创建 LRC；如果创建失败，请用“选择文件夹批量转换”。"
        appendLog(parentMessage)

        try {
            val content = readText(uri)
            val lrc = buildLrcContent(name, content)
            if (lrc == null) {
                appendLog("跳过：$name，无有效时间轴")
                return
            }

            // Android SAF 单文件 Uri 通常拿不到父目录写权限，所以提示使用文件夹模式。
            appendLog("已读取并成功转换：$name")
            appendLog("由于安卓权限限制，单文件模式不一定能写回原目录。推荐使用“选择文件夹批量转换”，可稳定同目录生成 .lrc。")
            appendLog("")
            appendLog(lrc.take(1200))
        } catch (e: Exception) {
            appendLog("转换失败：${e.message}")
        }
    }

    private fun processFolder(folder: DocumentFile): Int {
        var count = 0
        folder.listFiles().forEach { file ->
            if (file.isDirectory && recursiveBox.isChecked) {
                count += processFolder(file)
            } else if (file.isFile && file.name?.lowercase()?.endsWith(".vtt") == true) {
                count++
                convertFileInFolder(file, folder)
            }
        }
        return count
    }

    private fun convertFileInFolder(vttFile: DocumentFile, parentFolder: DocumentFile) {
        val name = vttFile.name ?: return
        try {
            val content = readText(vttFile.uri)
            val lrcContent = buildLrcContent(name, content)
            if (lrcContent == null) {
                appendLog("跳过：$name，无有效时间轴")
                return
            }

            val lrcName = name.replace(Regex("(?i)\\.vtt$"), ".lrc")
            val old = parentFolder.findFile(lrcName)

            if (old != null) {
                if (overwriteBox.isChecked) {
                    old.delete()
                } else {
                    appendLog("跳过：$lrcName 已存在")
                    return
                }
            }

            val lrcFile = parentFolder.createFile("application/octet-stream", lrcName)
            if (lrcFile == null) {
                appendLog("失败：无法创建 $lrcName")
                return
            }

            contentResolver.openOutputStream(lrcFile.uri, "wt")?.use { out ->
                if (bomBox.isChecked) {
                    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                }
                out.write(lrcContent.replace("\r\n", "\n").replace("\r", "\n").toByteArray(Charsets.UTF_8))
            }

            appendLog("完成：$name → $lrcName")
        } catch (e: Exception) {
            appendLog("出错：$name，${e.message}")
        }
    }

    private fun readText(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { input ->
            return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
        throw IllegalStateException("无法读取文件")
    }

    private fun buildLrcContent(fileName: String, raw: String): String? {
        val cleaned = raw
            .replace(Regex("^\\uFEFF"), "")
            .replace(Regex("^WEBVTT.*?\\n\\n", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("^\\s*#.*?\\n", RegexOption.MULTILINE), "")

        val lrcBody = vttToLrc(cleaned)

        if (!Regex("\\[\\d+:\\d{2}\\.\\d{2}]").containsMatchIn(lrcBody)) {
            return null
        }

        val title = fileName.replace(Regex("(?i)\\.vtt$"), "")
        return "[ti:$title]\n[ar:未知歌手]\n$lrcBody"
    }

    private fun convertTime(vttTime: String): String {
        val parts = vttTime.split(":")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val secMs = parts[2].split(".")
        val totalMinutes = hours * 60 + minutes
        val sec = secMs[0].padStart(2, '0')
        val ms = secMs[1].padEnd(3, '0').substring(0, 2)
        return "[$totalMinutes:$sec.$ms]"
    }

    private fun vttToLrc(vttContent: String): String {
        val result = StringBuilder()

        val pattern = Pattern.compile(
            "(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) --> (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*(.*?)(?=\\n*\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> |$)",
            Pattern.DOTALL or Pattern.MULTILINE
        )

        val matcher = pattern.matcher(vttContent)

        while (matcher.find()) {
            val startTime = matcher.group(1) ?: continue
            val lyric = matcher.group(3) ?: ""

            val lrcStart = convertTime(startTime)
            val lyricClean = lyric.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")

            if (lyricClean.isNotEmpty()) {
                result.append(lrcStart)
                    .append(" ")
                    .append(lyricClean)
                    .append("\n")
            }
        }

        return result.toString().trim()
    }

    private fun appendLog(msg: String) {
        logView.append(if (logView.text.isNullOrBlank()) msg else "\n$msg")
    }

    private fun clearLog() {
        logView.text = ""
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

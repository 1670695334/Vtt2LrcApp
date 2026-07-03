package com.example.vtt2lrc

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {

    private lateinit var overwriteBox: CheckBox
    private lateinit var recursiveBox: CheckBox
    private lateinit var logView: TextView

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            appendLog("Selection cancelled.")
            return@registerForActivityResult
        }

        val uri = result.data?.data
        if (uri == null) {
            appendLog("No folder URI returned.")
            return@registerForActivityResult
        }

        persistTreePermission(uri, result.data?.flags ?: 0)
        val folder = DocumentFile.fromTreeUri(this, uri)
        if (folder == null || !folder.isDirectory) {
            appendLog("Selected item is not a valid folder.")
            return@registerForActivityResult
        }

        clearLog()
        appendLog("Start: ${folder.name ?: "selected folder"}")
        val stats = ProcessStats()
        processFolder(folder, stats)
        appendLog("")
        appendLog("Done: found ${stats.found} VTT, wrote ${stats.written} LRC, skipped ${stats.skipped}, failed ${stats.failed}.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val title = TextView(this).apply {
            text = "VTT to LRC"
            textSize = 24f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFF111111.toInt())
        }

        val summary = TextView(this).apply {
            text = "Pick a folder, find .vtt files, and write same-name .lrc files beside them. Output is UTF-8 with BOM and LF line endings."
            textSize = 14f
            setTextColor(0xFF555555.toInt())
            setPadding(0, dp(8), 0, dp(12))
        }

        val pickButton = Button(this).apply {
            text = "Choose Folder"
            setOnClickListener { openFolderPicker() }
        }

        overwriteBox = CheckBox(this).apply {
            text = "Overwrite existing LRC"
            isChecked = true
        }

        recursiveBox = CheckBox(this).apply {
            text = "Include subfolders"
            isChecked = true
        }

        val logTitle = TextView(this).apply {
            text = "Log"
            textSize = 17f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, dp(14), 0, dp(8))
        }

        logView = TextView(this).apply {
            text = "Choose a folder that contains .vtt files."
            textSize = 14f
            setTextColor(0xFF222222.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        val scroll = ScrollView(this).apply {
            addView(
                logView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(title)
        root.addView(summary)
        root.addView(pickButton, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(overwriteBox)
        root.addView(recursiveBox)
        root.addView(logTitle)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        folderPicker.launch(intent)
    }

    private fun persistTreePermission(uri: Uri, resultFlags: Int) {
        val requestedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val takeFlags = resultFlags and requestedFlags
        try {
            contentResolver.takePersistableUriPermission(uri, if (takeFlags == 0) requestedFlags else takeFlags)
        } catch (e: SecurityException) {
            appendLog("Warning: could not persist folder permission: ${e.message}")
        }
    }

    private fun processFolder(folder: DocumentFile, stats: ProcessStats) {
        folder.listFiles().forEach { child ->
            when {
                child.isDirectory && recursiveBox.isChecked -> processFolder(child, stats)
                child.isFile && child.name?.lowercase()?.endsWith(".vtt") == true -> {
                    stats.found++
                    convertFile(child, folder, stats)
                }
            }
        }
    }

    private fun convertFile(vttFile: DocumentFile, parentFolder: DocumentFile, stats: ProcessStats) {
        val vttName = vttFile.name ?: return
        val lrcName = vttName.replace(Regex("(?i)\\.vtt$"), ".lrc")

        try {
            val lrcContent = buildLrcContent(vttName, readText(vttFile.uri))
            if (lrcContent == null) {
                stats.skipped++
                appendLog("Skip: $vttName has no valid timestamp.")
                return
            }

            val existing = parentFolder.findFile(lrcName)
            if (existing != null) {
                if (!overwriteBox.isChecked) {
                    stats.skipped++
                    appendLog("Skip: $lrcName already exists.")
                    return
                }
                if (!existing.delete()) {
                    stats.failed++
                    appendLog("Failed: cannot overwrite $lrcName.")
                    return
                }
            }

            val lrcFile = parentFolder.createFile("application/octet-stream", lrcName)
            if (lrcFile == null) {
                stats.failed++
                appendLog("Failed: cannot create $lrcName.")
                return
            }

            writeUtf8BomLf(lrcFile.uri, lrcContent)
            stats.written++
            appendLog("OK: $vttName -> $lrcName")
        } catch (e: Exception) {
            stats.failed++
            appendLog("Error: $vttName, ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun readText(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { input ->
            return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
        throw IllegalStateException("Cannot read file")
    }

    private fun writeUtf8BomLf(uri: Uri, text: String) {
        val lfText = text.replace("\r\n", "\n").replace("\r", "\n")
        contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(lfText.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot write file")
    }

    private fun buildLrcContent(fileName: String, raw: String): String? {
        val normalized = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("^\\uFEFF"), "")
            .replace(Regex("^WEBVTT.*?\\n\\n", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("^\\s*#.*?\\n", RegexOption.MULTILINE), "")

        val body = vttToLrc(normalized)
        if (!Regex("\\[\\d+:\\d{2}\\.\\d{2}]").containsMatchIn(body)) {
            return null
        }

        val title = fileName.replace(Regex("(?i)\\.vtt$"), "")
        return "[ti:$title]\n[ar:\u672a\u77e5\u6b4c\u624b]\n$body"
    }

    private fun vttToLrc(vttContent: String): String {
        val lines = mutableListOf<String>()
        val pattern = Pattern.compile(
            "(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) --> (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*(.*?)(?=\\n*\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> |$)",
            Pattern.DOTALL or Pattern.MULTILINE
        )
        val matcher = pattern.matcher(vttContent)
        var previousTime = "0:00.00"

        while (matcher.find()) {
            val startTime = matcher.group(1) ?: continue
            val lyric = matcher.group(3) ?: ""
            val lrcStart = convertTime(startTime)
            if (lrcStart < previousTime) {
                appendLog("Warning: timestamp order changed, $previousTime -> $lrcStart")
            }
            previousTime = lrcStart

            val lyricClean = lyric
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")

            if (lyricClean.isNotEmpty()) {
                lines.add("$lrcStart $lyricClean")
            }
        }

        return lines.joinToString("\n")
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

    private fun appendLog(message: String) {
        logView.append(if (logView.text.isNullOrBlank()) message else "\n$message")
    }

    private fun clearLog() {
        logView.text = ""
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class ProcessStats(
        var found: Int = 0,
        var written: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0
    )
}
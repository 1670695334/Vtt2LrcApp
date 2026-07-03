package com.example.vtt2lrc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {

    private lateinit var titleView: TextView
    private lateinit var leftButton: Button
    private lateinit var chooseButton: Button
    private lateinit var optionButton: Button
    private lateinit var overwriteBox: CheckBox
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var bottomText: TextView
    private lateinit var convertSelectedButton: Button
    private lateinit var cancelSelectedButton: Button

    private var rootFolder: DocumentFile? = null
    private var currentFolder: DocumentFile? = null
    private val backStack = mutableListOf<DocumentFile>()
    private val entries = mutableListOf<FileEntry>()
    private val selectedKeys = linkedSetOf<String>()

    private var viewMode = ViewMode.Detail
    private var sortMode = SortMode.Name
    private var descending = false
    private var selectionMode = false

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            toast("\u5df2\u53d6\u6d88\u9009\u62e9")
            return@registerForActivityResult
        }

        val uri = result.data?.data
        if (uri == null) {
            toast("\u6ca1\u6709\u83b7\u53d6\u5230\u6587\u4ef6\u5939\u6743\u9650")
            return@registerForActivityResult
        }

        persistTreePermission(uri, result.data?.flags ?: 0)
        val folder = DocumentFile.fromTreeUri(this, uri)
        if (folder == null || !folder.isDirectory) {
            toast("\u9009\u62e9\u7684\u4e0d\u662f\u6709\u6548\u6587\u4ef6\u5939")
            return@registerForActivityResult
        }

        rootFolder = folder
        currentFolder = folder
        backStack.clear()
        exitSelectionMode()
        renderDirectory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        renderDirectory()
    }

    override fun onBackPressed() {
        when {
            selectionMode -> exitSelectionMode()
            backStack.isNotEmpty() -> {
                currentFolder = backStack.removeAt(backStack.lastIndex)
                renderDirectory()
            }
            else -> super.onBackPressed()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(Color.rgb(248, 248, 252))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        leftButton = Button(this).apply {
            text = "\u2190"
            textSize = 22f
            setAllCaps(false)
            setOnClickListener { handleLeftAction() }
        }

        titleView = TextView(this).apply {
            textSize = 24f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.rgb(32, 34, 42))
            maxLines = 2
            setPadding(dp(8), 0, dp(8), 0)
        }

        optionButton = Button(this).apply {
            text = "\u2630"
            textSize = 22f
            setAllCaps(false)
            setOnClickListener { showOptionsDialog() }
        }

        topBar.addView(leftButton, LinearLayout.LayoutParams(dp(52), dp(48)))
        topBar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(optionButton, LinearLayout.LayoutParams(dp(52), dp(48)))

        chooseButton = Button(this).apply {
            text = "\u9009\u62e9\u6587\u4ef6\u5939"
            setAllCaps(false)
            textSize = 16f
            setOnClickListener { openFolderPicker() }
        }

        overwriteBox = CheckBox(this).apply {
            text = "\u8986\u76d6\u5df2\u6709 LRC"
            isChecked = true
            textSize = 14f
            setPadding(0, dp(4), 0, dp(4))
        }

        statusView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(102, 105, 115))
            setPadding(0, dp(6), 0, dp(8))
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val listScroll = ScrollView(this).apply {
            addView(listContainer)
        }

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.WHITE, Color.rgb(226, 226, 232), 1, 18)
            visibility = View.GONE
        }

        bottomText = TextView(this).apply {
            textSize = 15f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.rgb(46, 49, 60))
        }

        convertSelectedButton = Button(this).apply {
            text = "\u8f6c\u6362"
            setAllCaps(false)
            setOnClickListener { convertSelectedFiles() }
        }

        cancelSelectedButton = Button(this).apply {
            text = "\u53d6\u6d88"
            setAllCaps(false)
            setOnClickListener { exitSelectionMode() }
        }

        bottomBar.addView(bottomText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(convertSelectedButton, LinearLayout.LayoutParams(dp(92), dp(46)))
        bottomBar.addView(cancelSelectedButton, LinearLayout.LayoutParams(dp(92), dp(46)))

        root.addView(topBar)
        root.addView(chooseButton, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(overwriteBox)
        root.addView(statusView)
        root.addView(listScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(root)
    }

    private fun handleLeftAction() {
        when {
            selectionMode -> exitSelectionMode()
            backStack.isNotEmpty() -> {
                currentFolder = backStack.removeAt(backStack.lastIndex)
                renderDirectory()
            }
            else -> openFolderPicker()
        }
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
        } catch (_: SecurityException) {
            toast("\u65e0\u6cd5\u6301\u4e45\u5316\u6743\u9650\uff0c\u672c\u6b21\u4ecd\u53ef\u5c1d\u8bd5\u64cd\u4f5c")
        }
    }

    private fun renderDirectory() {
        val folder = currentFolder
        entries.clear()
        listContainer.removeAllViews()

        titleView.text = if (selectionMode) "\u5df2\u9009\u62e9 0 \u9879" else (folder?.name ?: "VTT \u8f6c LRC")
        leftButton.text = if (selectionMode || backStack.isNotEmpty()) "\u2190" else "+"
        optionButton.isEnabled = folder != null
        chooseButton.text = if (folder == null) "\u9009\u62e9\u6587\u4ef6\u5939" else "\u66f4\u6362\u6587\u4ef6\u5939"

        if (folder == null) {
            statusView.text = "\u8bf7\u9009\u62e9\u4e00\u4e2a\u6587\u4ef6\u5939\uff0c\u7136\u540e\u5728\u76ee\u5f55\u91cc\u70b9\u9009 VTT \u6587\u4ef6\u8fdb\u884c\u8f6c\u6362\u3002"
            listContainer.addView(emptyHint("\u8fd8\u6ca1\u6709\u9009\u62e9\u6587\u4ef6\u5939"))
            bottomBar.visibility = View.GONE
            return
        }

        entries.addAll(folder.listFiles().mapNotNull { doc -> FileEntry.from(doc) })
        sortEntries()

        statusView.text = "${entries.size} \u9879 \u00b7 ${viewMode.label} \u00b7 \u6309${sortMode.label}${if (descending) "\u964d\u5e8f" else "\u5347\u5e8f"}"
        if (entries.isEmpty()) {
            listContainer.addView(emptyHint("\u6b64\u76ee\u5f55\u4e3a\u7a7a"))
        } else if (viewMode == ViewMode.Grid) {
            listContainer.addView(renderGrid())
        } else {
            entries.forEach { entry ->
                listContainer.addView(renderRow(entry, viewMode == ViewMode.Compact))
            }
        }
        refreshSelectionBar()
    }

    private fun sortEntries() {
        val comparator = compareBy<FileEntry> { !it.isDirectory }.then(
            when (sortMode) {
                SortMode.Name -> compareBy<FileEntry> { it.name.lowercase(Locale.ROOT) }
                SortMode.Size -> compareBy<FileEntry> { it.size }
                SortMode.Date -> compareBy<FileEntry> { it.modified }
                SortMode.Type -> compareBy<FileEntry> { it.extension }
            }
        )
        entries.sortWith(if (descending) comparator.reversed() else comparator)
    }

    private fun renderRow(entry: FileEntry, compact: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), if (compact) dp(6) else dp(10), dp(8), if (compact) dp(6) else dp(10))
            background = rounded(Color.WHITE, Color.rgb(229, 230, 236), 1, 8)
            setOnClickListener { handleEntryClick(entry) }
            setOnLongClickListener {
                handleEntryLongPress(entry)
                true
            }
        }

        if (selectionMode) {
            val box = CheckBox(this).apply {
                isEnabled = entry.isVtt
                isChecked = selectedKeys.contains(entry.key)
                setOnCheckedChangeListener { _, checked -> setEntrySelected(entry, checked) }
            }
            row.addView(box, LinearLayout.LayoutParams(dp(44), dp(48)))
        }

        val icon = TextView(this).apply {
            text = entry.icon
            textSize = if (entry.isDirectory) 28f else 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(entry.iconColor, Color.TRANSPARENT, 0, 8)
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        val nameView = TextView(this).apply {
            text = entry.name
            textSize = if (compact) 15f else 17f
            setTextColor(Color.rgb(22, 24, 30))
            maxLines = if (compact) 1 else 2
        }

        val metaView = TextView(this).apply {
            text = entry.meta
            textSize = 12f
            setTextColor(Color.rgb(112, 115, 125))
            visibility = if (compact) View.GONE else View.VISIBLE
        }

        textColumn.addView(nameView)
        textColumn.addView(metaView)
        row.addView(icon, LinearLayout.LayoutParams(dp(54), dp(54)))
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return withMargins(row, 0, 0, 0, dp(8))
    }

    private fun renderGrid(): View {
        val grid = GridLayout(this).apply {
            columnCount = 3
        }
        entries.forEach { entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(Color.WHITE, Color.rgb(229, 230, 236), 1, 8)
                setOnClickListener { handleEntryClick(entry) }
                setOnLongClickListener {
                    handleEntryLongPress(entry)
                    true
                }
            }

            if (selectionMode) {
                card.addView(CheckBox(this).apply {
                    isEnabled = entry.isVtt
                    isChecked = selectedKeys.contains(entry.key)
                    setOnCheckedChangeListener { _, checked -> setEntrySelected(entry, checked) }
                }, LinearLayout.LayoutParams(dp(46), dp(36)))
            }

            card.addView(TextView(this).apply {
                text = entry.icon
                textSize = if (entry.isDirectory) 28f else 18f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(entry.iconColor, Color.TRANSPARENT, 0, 8)
            }, LinearLayout.LayoutParams(dp(64), dp(64)))

            card.addView(TextView(this).apply {
                text = entry.name
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 3
                setTextColor(Color.rgb(30, 32, 38))
                setPadding(0, dp(8), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(8))
            }
            grid.addView(card, params)
        }
        return grid
    }

    private fun handleEntryClick(entry: FileEntry) {
        if (selectionMode) {
            if (entry.isVtt) {
                setEntrySelected(entry, !selectedKeys.contains(entry.key))
                renderDirectory()
            }
            return
        }

        when {
            entry.isDirectory -> {
                currentFolder?.let { backStack.add(it) }
                currentFolder = entry.file
                renderDirectory()
            }
            entry.isVtt -> showSingleConvertDialog(entry)
            else -> toast("\u53ea\u652f\u6301\u8f6c\u6362 .vtt \u6587\u4ef6")
        }
    }

    private fun handleEntryLongPress(entry: FileEntry) {
        if (!entry.isVtt) {
            toast("\u957f\u6309\u53ef\u9009\u62e9 VTT \u6587\u4ef6")
            return
        }
        selectionMode = true
        selectedKeys.clear()
        selectedKeys.add(entry.key)
        renderDirectory()
    }

    private fun setEntrySelected(entry: FileEntry, selected: Boolean) {
        if (!entry.isVtt) return
        if (selected) selectedKeys.add(entry.key) else selectedKeys.remove(entry.key)
        if (selectionMode && selectedKeys.isEmpty()) {
            exitSelectionMode()
        } else {
            refreshSelectionBar()
        }
    }

    private fun refreshSelectionBar() {
        if (!selectionMode) {
            bottomBar.visibility = View.GONE
            return
        }
        titleView.text = "\u5df2\u9009\u62e9 ${selectedKeys.size} \u9879"
        bottomText.text = "\u540c\u540d\u8f6c\u6362 ${selectedKeys.size} \u4e2a VTT"
        convertSelectedButton.isEnabled = selectedKeys.isNotEmpty()
        bottomBar.visibility = View.VISIBLE
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedKeys.clear()
        renderDirectory()
    }

    private fun showSingleConvertDialog(entry: FileEntry) {
        val defaultName = entry.name.replace(Regex("(?i)\\.vtt$"), ".lrc")
        val input = EditText(this).apply {
            setText(defaultName)
            setSelection(0, text.length)
            hint = "\u8f93\u51fa\u6587\u4ef6\u540d"
        }

        AlertDialog.Builder(this)
            .setTitle("\u8f6c\u6362\u4e3a LRC")
            .setMessage("\u53ef\u4fee\u6539\u8f93\u51fa\u6587\u4ef6\u540d\uff0c\u6587\u4ef6\u4f1a\u751f\u6210\u5728\u5f53\u524d\u76ee\u5f55\u3002")
            .setView(input)
            .setNegativeButton("\u53d6\u6d88", null)
            .setPositiveButton("\u8f6c\u6362") { _, _ ->
                val outputName = normalizeLrcName(input.text?.toString().orEmpty(), defaultName)
                val stats = ProcessStats(found = 1)
                convertFile(entry, outputName, stats)
                toast(if (stats.written == 1) "\u8f6c\u6362\u5b8c\u6210" else "\u8f6c\u6362\u5931\u8d25\u6216\u5df2\u8df3\u8fc7")
                renderDirectory()
            }
            .show()
    }

    private fun convertSelectedFiles() {
        val selectedEntries = entries.filter { selectedKeys.contains(it.key) && it.isVtt }
        val stats = ProcessStats(found = selectedEntries.size)
        selectedEntries.forEach { entry ->
            val outputName = entry.name.replace(Regex("(?i)\\.vtt$"), ".lrc")
            convertFile(entry, outputName, stats)
        }
        toast("\u5b8c\u6210\uff1a\u5199\u5165 ${stats.written}\uff0c\u8df3\u8fc7 ${stats.skipped}\uff0c\u5931\u8d25 ${stats.failed}")
        selectionMode = false
        selectedKeys.clear()
        renderDirectory()
    }

    private fun convertFile(entry: FileEntry, outputName: String, stats: ProcessStats) {
        try {
            val lrcContent = buildLrcContent(entry.name, readText(entry.file.uri))
            if (lrcContent == null) {
                stats.skipped++
                return
            }

            val existing = currentFolder?.findFile(outputName)
            if (existing != null) {
                if (!overwriteBox.isChecked) {
                    stats.skipped++
                    return
                }
                if (!existing.delete()) {
                    stats.failed++
                    return
                }
            }

            val lrcFile = currentFolder?.createFile("application/octet-stream", outputName)
            if (lrcFile == null) {
                stats.failed++
                return
            }

            writeUtf8BomLf(lrcFile.uri, lrcContent)
            stats.written++
        } catch (_: Exception) {
            stats.failed++
        }
    }

    private fun showOptionsDialog() {
        val labels = arrayOf(
            "\u67e5\u770b\uff1a\u8be6\u7ec6",
            "\u67e5\u770b\uff1a\u7d27\u51d1",
            "\u67e5\u770b\uff1a\u7f51\u683c",
            "\u6392\u5e8f\uff1a\u540d\u79f0",
            "\u6392\u5e8f\uff1a\u5927\u5c0f",
            "\u6392\u5e8f\uff1a\u65e5\u671f",
            "\u6392\u5e8f\uff1a\u7c7b\u578b",
            if (descending) "\u987a\u5e8f\uff1a\u5347\u5e8f" else "\u987a\u5e8f\uff1a\u964d\u5e8f"
        )
        AlertDialog.Builder(this)
            .setTitle("\u67e5\u770b\u548c\u6392\u5e8f")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> viewMode = ViewMode.Detail
                    1 -> viewMode = ViewMode.Compact
                    2 -> viewMode = ViewMode.Grid
                    3 -> sortMode = SortMode.Name
                    4 -> sortMode = SortMode.Size
                    5 -> sortMode = SortMode.Date
                    6 -> sortMode = SortMode.Type
                    7 -> descending = !descending
                }
                renderDirectory()
            }
            .show()
    }

    private fun normalizeLrcName(value: String, fallback: String): String {
        val trimmed = value.trim().ifBlank { fallback }
        return if (trimmed.lowercase(Locale.ROOT).endsWith(".lrc")) trimmed else "$trimmed.lrc"
    }

    private fun readText(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { input ->
            return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
        throw IllegalStateException("\u65e0\u6cd5\u8bfb\u53d6\u6587\u4ef6")
    }

    private fun writeUtf8BomLf(uri: Uri, text: String) {
        val lfText = text.replace("\r\n", "\n").replace("\r", "\n")
        contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(lfText.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("\u65e0\u6cd5\u5199\u5165\u6587\u4ef6")
    }

    private fun buildLrcContent(fileName: String, raw: String): String? {
        val normalized = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("^\\uFEFF"), "")
            .replace(Regex("^WEBVTT.*?\\n\\n", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("^\\s*#.*?\\n", RegexOption.MULTILINE), "")

        val body = vttToLrc(normalized)
        if (!Regex("\\[\\d+:\\d{2}\\.\\d{2}]").containsMatchIn(body)) return null

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
        while (matcher.find()) {
            val startTime = matcher.group(1) ?: continue
            val lyric = matcher.group(3) ?: ""
            val lyricClean = lyric.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            if (lyricClean.isNotEmpty()) lines.add("${convertTime(startTime)} $lyricClean")
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

    private fun emptyHint(text: String): View {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(112, 115, 124))
            setPadding(dp(16), dp(40), dp(16), dp(40))
        }
    }

    private fun rounded(fill: Int, stroke: Int, strokeWidth: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }
    }

    private fun withMargins(view: View, left: Int, top: Int, right: Int, bottom: Int): View {
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(left, top, right, bottom) }
        return view
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        return String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }

    private fun formatDate(time: Long): String {
        if (time <= 0L) return ""
        return SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(Date(time))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class ViewMode(val label: String) {
        Detail("\u8be6\u7ec6"),
        Compact("\u7d27\u51d1"),
        Grid("\u7f51\u683c")
    }

    private enum class SortMode(val label: String) {
        Name("\u540d\u79f0"),
        Size("\u5927\u5c0f"),
        Date("\u65e5\u671f"),
        Type("\u7c7b\u578b")
    }

    private data class FileEntry(
        val file: DocumentFile,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val modified: Long
    ) {
        val key: String = file.uri.toString()
        val extension: String = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val isVtt: Boolean = !isDirectory && extension == "vtt"
        val isLrc: Boolean = !isDirectory && extension == "lrc"
        val isAudio: Boolean = !isDirectory && extension in setOf("mp3", "flac", "m4a", "wav", "aac")
        val icon: String
            get() = when {
                isDirectory -> "\u2191"
                isVtt -> "VTT"
                isLrc -> "LRC"
                isAudio -> "\u266a"
                else -> "\u6587"
            }
        val iconColor: Int
            get() = when {
                isDirectory -> Color.rgb(245, 158, 11)
                isVtt -> Color.rgb(116, 116, 124)
                isLrc -> Color.rgb(139, 92, 246)
                isAudio -> Color.rgb(217, 70, 70)
                else -> Color.rgb(156, 163, 175)
            }
        val meta: String
            get() = if (isDirectory) "\u6587\u4ef6\u5939" else "${sizeText(size)} \u00b7 ${dateText(modified)}"

        companion object {
            fun from(file: DocumentFile): FileEntry? {
                val name = file.name ?: return null
                return FileEntry(file, name, file.isDirectory, file.length(), file.lastModified())
            }

            private fun sizeText(bytes: Long): String {
                if (bytes < 1024) return "$bytes B"
                val kb = bytes / 1024.0
                if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
                return String.format(Locale.US, "%.1f MB", kb / 1024.0)
            }

            private fun dateText(time: Long): String {
                if (time <= 0L) return ""
                return SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(Date(time))
            }
        }
    }

    private data class ProcessStats(
        var found: Int = 0,
        var written: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0
    )
}
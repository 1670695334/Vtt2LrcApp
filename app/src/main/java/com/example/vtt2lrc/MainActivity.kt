package com.example.vtt2lrc

import android.app.Activity
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
import android.widget.GridLayout
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
    private lateinit var selectAllBox: CheckBox
    private lateinit var folderButton: Button
    private lateinit var viewModeButton: Button
    private lateinit var convertButton: Button
    private lateinit var folderInfoView: TextView
    private lateinit var fileCountView: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var logView: TextView

    private var currentFolder: DocumentFile? = null
    private var viewMode = ViewMode.Detail
    private val vttItems = mutableListOf<VttItem>()
    private val selectedUris = linkedSetOf<String>()

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            appendLog(t("\u5df2\u53d6\u6d88\u9009\u62e9\u3002"))
            return@registerForActivityResult
        }

        val uri = result.data?.data
        if (uri == null) {
            appendLog(t("\u6ca1\u6709\u83b7\u53d6\u5230\u6587\u4ef6\u5939\u6743\u9650\u3002"))
            return@registerForActivityResult
        }

        persistTreePermission(uri, result.data?.flags ?: 0)
        val folder = DocumentFile.fromTreeUri(this, uri)
        if (folder == null || !folder.isDirectory) {
            appendLog(t("\u9009\u62e9\u7684\u4e0d\u662f\u6709\u6548\u6587\u4ef6\u5939\u3002"))
            return@registerForActivityResult
        }

        currentFolder = folder
        scanSelectedFolder()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = t("VTT \u8f6c LRC")
            textSize = 26f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.rgb(24, 24, 27))
        }

        val summary = TextView(this).apply {
            text = t("\u5148\u9009\u6587\u4ef6\u5939\u626b\u63cf .vtt\uff0c\u518d\u52fe\u9009\u9700\u8981\u8f6c\u6362\u7684\u6b4c\u8bcd\u6587\u4ef6\u3002\u751f\u6210\u540c\u540d .lrc\uff0cUTF-8 BOM + LF\u3002")
            textSize = 14f
            setTextColor(Color.rgb(86, 86, 94))
            setPadding(0, dp(6), 0, dp(10))
        }

        folderButton = Button(this).apply {
            text = t("\u9009\u62e9\u6587\u4ef6\u5939\u5e76\u626b\u63cf")
            setAllCaps(false)
            setOnClickListener { openFolderPicker() }
        }

        val optionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        overwriteBox = CheckBox(this).apply {
            text = t("\u8986\u76d6\u5df2\u6709 LRC")
            isChecked = true
        }
        recursiveBox = CheckBox(this).apply {
            text = t("\u5305\u542b\u5b50\u76ee\u5f55")
            isChecked = true
            setOnCheckedChangeListener { _, _ -> currentFolder?.let { scanSelectedFolder() } }
        }

        optionRow.addView(overwriteBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        optionRow.addView(recursiveBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        folderInfoView = TextView(this).apply {
            text = t("\u5c1a\u672a\u9009\u62e9\u6587\u4ef6\u5939")
            textSize = 15f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.rgb(35, 35, 40))
            setPadding(0, dp(12), 0, dp(2))
        }

        fileCountView = TextView(this).apply {
            text = t("\u8bf7\u5148\u626b\u63cf\u6587\u4ef6\u5939")
            textSize = 13f
            setTextColor(Color.rgb(95, 95, 105))
            setPadding(0, 0, 0, dp(8))
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        selectAllBox = CheckBox(this).apply {
            text = t("\u5168\u9009")
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedUris.clear()
                    selectedUris.addAll(vttItems.map { it.key })
                } else if (selectedUris.size == vttItems.size) {
                    selectedUris.clear()
                }
                renderFileList()
                refreshActions()
            }
        }

        viewModeButton = Button(this).apply {
            text = viewMode.label
            setAllCaps(false)
            setOnClickListener { cycleViewMode() }
        }

        convertButton = Button(this).apply {
            text = t("\u5f00\u59cb\u8f6c\u6362")
            isEnabled = false
            setAllCaps(false)
            setOnClickListener { convertSelectedFiles() }
        }

        actionRow.addView(selectAllBox, LinearLayout.LayoutParams(0, dp(46), 1f))
        actionRow.addView(viewModeButton, LinearLayout.LayoutParams(dp(96), dp(46)))
        actionRow.addView(convertButton, LinearLayout.LayoutParams(dp(112), dp(46)))

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val listScroll = ScrollView(this).apply {
            addView(listContainer)
        }

        val logTitle = TextView(this).apply {
            text = t("\u8f6c\u6362\u65e5\u5fd7")
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.rgb(55, 55, 62))
            setPadding(0, dp(10), 0, dp(6))
        }

        logView = TextView(this).apply {
            text = t("\u9009\u62e9\u6587\u4ef6\u5939\u540e\uff0c\u8fd9\u91cc\u4f1a\u663e\u793a\u626b\u63cf\u548c\u8f6c\u6362\u7ed3\u679c\u3002")
            textSize = 13f
            setTextColor(Color.rgb(39, 39, 42))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(245, 245, 246), Color.TRANSPARENT, 0)
        }

        val logScroll = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(title)
        root.addView(summary)
        root.addView(folderButton, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        root.addView(optionRow)
        root.addView(folderInfoView)
        root.addView(fileCountView)
        root.addView(actionRow)
        root.addView(listScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f))
        root.addView(logTitle)
        root.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.85f))
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
            appendLog(t("\u8b66\u544a\uff1a\u65e0\u6cd5\u6301\u4e45\u5316\u6587\u4ef6\u5939\u6743\u9650\uff0c\u672c\u6b21\u4ecd\u4f1a\u5c1d\u8bd5\u8f6c\u6362\u3002"))
        }
    }

    private fun scanSelectedFolder() {
        val folder = currentFolder ?: return
        vttItems.clear()
        selectedUris.clear()
        scanFolder(folder, "", vttItems)
        vttItems.sortWith(compareBy<VttItem> { it.relativeFolder.lowercase() }.thenBy { it.name.lowercase() })
        selectedUris.addAll(vttItems.map { it.key })

        folderInfoView.text = t("\u5f53\u524d\u6587\u4ef6\u5939\uff1a") + (folder.name ?: t("\u6240\u9009\u6587\u4ef6\u5939"))
        clearLog()
        appendLog(t("\u626b\u63cf\u5b8c\u6210\uff1a\u627e\u5230 ") + vttItems.size + t(" \u4e2a VTT \u6587\u4ef6\u3002"))
        renderFileList()
        refreshActions()
    }

    private fun scanFolder(folder: DocumentFile, relativePath: String, output: MutableList<VttItem>) {
        folder.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            if (child.isDirectory && recursiveBox.isChecked) {
                val childPath = if (relativePath.isBlank()) name else "$relativePath/$name"
                scanFolder(child, childPath, output)
            } else if (child.isFile && name.lowercase().endsWith(".vtt")) {
                output.add(
                    VttItem(
                        file = child,
                        parentFolder = folder,
                        name = name,
                        relativeFolder = relativePath,
                        size = child.length()
                    )
                )
            }
        }
    }

    private fun cycleViewMode() {
        viewMode = when (viewMode) {
            ViewMode.Detail -> ViewMode.Compact
            ViewMode.Compact -> ViewMode.Grid
            ViewMode.Grid -> ViewMode.Detail
        }
        viewModeButton.text = viewMode.label
        renderFileList()
    }

    private fun renderFileList() {
        listContainer.removeAllViews()
        selectAllBox.setOnCheckedChangeListener(null)
        selectAllBox.isChecked = vttItems.isNotEmpty() && selectedUris.size == vttItems.size
        selectAllBox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectedUris.clear()
                selectedUris.addAll(vttItems.map { it.key })
            } else if (selectedUris.size == vttItems.size) {
                selectedUris.clear()
            }
            renderFileList()
            refreshActions()
        }

        if (vttItems.isEmpty()) {
            listContainer.addView(emptyView())
            return
        }

        if (viewMode == ViewMode.Grid) {
            listContainer.addView(gridList())
        } else {
            vttItems.forEach { item ->
                listContainer.addView(fileRow(item, viewMode == ViewMode.Compact))
            }
        }
        refreshActions()
    }

    private fun emptyView(): View {
        return TextView(this).apply {
            text = t("\u6ca1\u6709\u627e\u5230 .vtt \u6587\u4ef6\u3002\u53ef\u4ee5\u6253\u5f00\u201c\u5305\u542b\u5b50\u76ee\u5f55\u201d\u540e\u91cd\u65b0\u626b\u63cf\u3002")
            textSize = 14f
            setTextColor(Color.rgb(108, 108, 116))
            setPadding(dp(12), dp(18), dp(12), dp(18))
            gravity = Gravity.CENTER
        }
    }

    private fun fileRow(item: VttItem, compact: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), if (compact) dp(6) else dp(10), dp(10), if (compact) dp(6) else dp(10))
            background = rounded(Color.WHITE, Color.rgb(225, 226, 230), 1)
            setOnClickListener { toggleSelection(item) }
        }

        val box = CheckBox(this).apply {
            isChecked = selectedUris.contains(item.key)
            setOnCheckedChangeListener { _, checked -> setSelected(item, checked) }
        }

        val icon = TextView(this).apply {
            text = "VTT"
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(88, 104, 170), Color.TRANSPARENT, 0)
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        val nameView = TextView(this).apply {
            text = item.name
            textSize = if (compact) 14f else 15f
            setTextColor(Color.rgb(24, 24, 27))
            maxLines = if (compact) 1 else 2
        }
        val metaView = TextView(this).apply {
            text = item.subtitle
            textSize = 12f
            setTextColor(Color.rgb(111, 111, 119))
            visibility = if (compact) View.GONE else View.VISIBLE
        }

        textColumn.addView(nameView)
        textColumn.addView(metaView)
        row.addView(box, LinearLayout.LayoutParams(dp(46), dp(46)))
        row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        return withMargins(row, 0, 0, 0, dp(8))
    }

    private fun gridList(): View {
        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        vttItems.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(Color.WHITE, Color.rgb(225, 226, 230), 1)
                setOnClickListener { toggleSelection(item) }
            }
            val box = CheckBox(this).apply {
                isChecked = selectedUris.contains(item.key)
                setOnCheckedChangeListener { _, checked -> setSelected(item, checked) }
            }
            val icon = TextView(this).apply {
                text = "VTT"
                textSize = 16f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(88, 104, 170), Color.TRANSPARENT, 0)
            }
            val name = TextView(this).apply {
                text = item.name
                textSize = 13f
                setTextColor(Color.rgb(24, 24, 27))
                gravity = Gravity.CENTER
                maxLines = 3
                setPadding(0, dp(8), 0, 0)
            }
            card.addView(box, LinearLayout.LayoutParams(dp(48), dp(36)))
            card.addView(icon, LinearLayout.LayoutParams(dp(64), dp(64)))
            card.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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

    private fun toggleSelection(item: VttItem) {
        setSelected(item, !selectedUris.contains(item.key))
        renderFileList()
    }

    private fun setSelected(item: VttItem, selected: Boolean) {
        if (selected) {
            selectedUris.add(item.key)
        } else {
            selectedUris.remove(item.key)
        }
        refreshActions()
    }

    private fun refreshActions() {
        val selectedCount = selectedUris.size
        fileCountView.text = t("\u5171 ") + vttItems.size + t(" \u4e2a VTT\uff0c\u5df2\u9009 ") + selectedCount + t(" \u4e2a")
        convertButton.isEnabled = selectedCount > 0
    }

    private fun convertSelectedFiles() {
        val selectedItems = vttItems.filter { selectedUris.contains(it.key) }
        val stats = ProcessStats(found = selectedItems.size)
        clearLog()
        appendLog(t("\u5f00\u59cb\u8f6c\u6362\uff1a\u5df2\u9009 ") + selectedItems.size + t(" \u4e2a VTT"))

        selectedItems.forEach { item ->
            convertFile(item, stats)
        }

        appendLog("")
        appendLog(
            t("\u5b8c\u6210\uff1a\u5199\u5165 ") + stats.written +
                t(" \u4e2a LRC\uff0c\u8df3\u8fc7 ") + stats.skipped +
                t(" \u4e2a\uff0c\u5931\u8d25 ") + stats.failed + t(" \u4e2a\u3002")
        )
    }

    private fun convertFile(item: VttItem, stats: ProcessStats) {
        val lrcName = item.name.replace(Regex("(?i)\\.vtt$"), ".lrc")

        try {
            val lrcContent = buildLrcContent(item.name, readText(item.file.uri))
            if (lrcContent == null) {
                stats.skipped++
                appendLog(t("\u8df3\u8fc7\uff1a") + item.name + t("\uff0c\u65e0\u6709\u6548\u65f6\u95f4\u8f74\u3002"))
                return
            }

            val existing = item.parentFolder.findFile(lrcName)
            if (existing != null) {
                if (!overwriteBox.isChecked) {
                    stats.skipped++
                    appendLog(t("\u8df3\u8fc7\uff1a") + lrcName + t(" \u5df2\u5b58\u5728\u3002"))
                    return
                }
                if (!existing.delete()) {
                    stats.failed++
                    appendLog(t("\u5931\u8d25\uff1a\u65e0\u6cd5\u8986\u76d6 ") + lrcName)
                    return
                }
            }

            val lrcFile = item.parentFolder.createFile("application/octet-stream", lrcName)
            if (lrcFile == null) {
                stats.failed++
                appendLog(t("\u5931\u8d25\uff1a\u65e0\u6cd5\u521b\u5efa ") + lrcName)
                return
            }

            writeUtf8BomLf(lrcFile.uri, lrcContent)
            stats.written++
            appendLog(t("\u5b8c\u6210\uff1a") + item.name + " -> " + lrcName)
        } catch (e: Exception) {
            stats.failed++
            appendLog(t("\u51fa\u9519\uff1a") + item.name + t("\uff0c") + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun readText(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { input ->
            return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
        throw IllegalStateException(t("\u65e0\u6cd5\u8bfb\u53d6\u6587\u4ef6"))
    }

    private fun writeUtf8BomLf(uri: Uri, text: String) {
        val lfText = text.replace("\r\n", "\n").replace("\r", "\n")
        contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(lfText.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException(t("\u65e0\u6cd5\u5199\u5165\u6587\u4ef6"))
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
                appendLog(t("\u8b66\u544a\uff1a\u65f6\u95f4\u8f74\u4e71\u5e8f\uff0c") + previousTime + " -> " + lrcStart)
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

    private fun rounded(fill: Int, stroke: Int, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fill)
            if (strokeWidth > 0) {
                setStroke(dp(strokeWidth), stroke)
            }
        }
    }

    private fun withMargins(view: View, left: Int, top: Int, right: Int, bottom: Int): View {
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(left, top, right, bottom)
        }
        return view
    }


    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun t(value: String): String = value

    private enum class ViewMode(val label: String) {
        Detail("\u8be6\u7ec6"),
        Compact("\u7d27\u51d1"),
        Grid("\u7f51\u683c")
    }

    private data class VttItem(
        val file: DocumentFile,
        val parentFolder: DocumentFile,
        val name: String,
        val relativeFolder: String,
        val size: Long
    ) {
        val key: String = file.uri.toString()
        val subtitle: String
            get() = listOf(relativeFolder.ifBlank { "." }, formatSize(size)).joinToString("  ")

        private fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            return String.format("%.1f MB", kb / 1024.0)
        }
    }

    private data class ProcessStats(
        var found: Int = 0,
        var written: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0
    )
}
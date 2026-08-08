package com.msi.gittool.ui.filebrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipFile

enum class FileCategory {
    CODE,
    MARKDOWN,
    IMAGE,
    AUDIO,
    VIDEO,
    PDF_DOCUMENT,
    TABULAR,
    ARCHIVE,
    BINARY
}

data class ZipEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val crc: Long
)

data class ParsedCsvTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val totalRows: Int,
    val totalCols: Int
)

data class HexDumpLine(
    val offset: String,
    val hexBytes: String,
    val asciiText: String
)

object FileTypeClassifier {

    fun classifyFile(path: String, forceBinary: Boolean = false): FileCategory {
        if (forceBinary) return FileCategory.BINARY

        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            // Images
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "tiff", "heic" -> FileCategory.IMAGE

            // Audio
            "mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "mid", "midi", "wma" -> FileCategory.AUDIO

            // Video
            "mp4", "mkv", "3gp", "webm", "avi", "mov", "flv", "wmv", "m4v" -> FileCategory.VIDEO

            // PDF & Docs
            "pdf", "epub", "mobi", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt" -> FileCategory.PDF_DOCUMENT

            // Markdown
            "md", "markdown", "mdown", "mkdn" -> FileCategory.MARKDOWN

            // Tabular Data
            "csv", "tsv", "psv" -> FileCategory.TABULAR

            // Archives & Packages
            "zip", "tar", "gz", "tgz", "bz2", "7z", "rar", "apk", "jar", "aar" -> FileCategory.ARCHIVE

            // Executables & Raw Binaries
            "bin", "exe", "dll", "so", "class", "o", "a", "dylib", "iso", "dmg" -> FileCategory.BINARY

            // Default code or text
            else -> FileCategory.CODE
        }
    }

    fun getSyntaxLanguage(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        val name = path.substringAfterLast('/').lowercase()

        if (name == "dockerfile") return "dockerfile"
        if (name == "makefile" || name == "cmakelists.txt") return "makefile"

        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "jsx", "mjs", "cjs" -> "javascript"
            "ts", "tsx" -> "typescript"
            "c", "h" -> "c"
            "cpp", "hpp", "cc", "cxx" -> "cpp"
            "cs" -> "csharp"
            "rs" -> "rust"
            "go" -> "go"
            "swift" -> "swift"
            "html", "htm" -> "html"
            "css", "scss", "sass", "less" -> "css"
            "json" -> "json"
            "xml", "svg" -> "xml"
            "yaml", "yml" -> "yaml"
            "sql" -> "sql"
            "sh", "bash", "zsh" -> "bash"
            "rb" -> "ruby"
            "php" -> "php"
            "dart" -> "dart"
            "scala" -> "scala"
            "lua" -> "lua"
            "groovy" -> "groovy"
            "gradle" -> "gradle"
            "toml" -> "toml"
            "properties", "env" -> "properties"
            else -> "text"
        }
    }

    fun getMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        val mapMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mapMime.isNullOrEmpty()) return mapMime

        return when (ext) {
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "tsv" -> "text/tab-separated-values"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            else -> "text/plain"
        }
    }

    fun parseCsv(text: String, delimiter: Char = ','): ParsedCsvTable {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ParsedCsvTable(emptyList(), emptyList(), 0, 0)
        }

        fun parseLine(line: String): List<String> {
            val tokens = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false

            for (i in line.indices) {
                val c = line[i]
                if (c == '"') {
                    inQuotes = !inQuotes
                } else if (c == delimiter && !inQuotes) {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                } else {
                    sb.append(c)
                }
            }
            tokens.add(sb.toString().trim())
            return tokens
        }

        val headers = parseLine(lines.first())
        val rows = lines.drop(1).map { parseLine(it) }
        val maxCols = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)

        return ParsedCsvTable(
            headers = headers,
            rows = rows,
            totalRows = rows.size,
            totalCols = maxCols
        )
    }

    fun readZipArchiveEntries(file: File): List<ZipEntryInfo> {
        val entries = mutableListOf<ZipEntryInfo>()
        try {
            ZipFile(file).use { zip ->
                val zipEntries = zip.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    entries.add(
                        ZipEntryInfo(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            isDirectory = entry.isDirectory,
                            crc = entry.crc
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entries
    }

    fun generateHexDump(bytes: ByteArray, maxBytes: Int = 4096): List<HexDumpLine> {
        val limit = minOf(bytes.size, maxBytes)
        val lines = mutableListOf<HexDumpLine>()
        val rowSize = 16

        for (i in 0 until limit step rowSize) {
            val offsetStr = String.format("%08X", i)
            val hexSb = StringBuilder()
            val asciiSb = StringBuilder()

            for (j in 0 until rowSize) {
                if (i + j < limit) {
                    val b = bytes[i + j].toInt() and 0xFF
                    hexSb.append(String.format("%02X ", b))
                    val charVal = b.toChar()
                    if (b in 32..126) {
                        asciiSb.append(charVal)
                    } else {
                        asciiSb.append('.')
                    }
                } else {
                    hexSb.append("   ")
                }
            }

            lines.add(
                HexDumpLine(
                    offset = offsetStr,
                    hexBytes = hexSb.toString().trimEnd(),
                    asciiText = asciiSb.toString()
                )
            )
        }
        return lines
    }

    fun openWithExternalApp(context: Context, file: File, customMimeType: String? = null): Boolean {
        try {
            val mime = customMimeType ?: getMimeType(file.name)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open file with..."))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

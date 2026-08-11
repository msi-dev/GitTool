package com.msi.gittool.ui.filebrowser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object CodeSyntaxHighlighter {

    // Dark theme color palette
    private val darkKeyword = Color(0xFFCF8855)   // Warm Orange/Brown keyword
    private val darkString = Color(0xFF6AAB73)    // Soft Green
    private val darkNumber = Color(0xFF2AACB8)    // Cyan/Teal
    private val darkComment = Color(0xFF7A7E85)   // Muted Slate Gray
    private val darkAnnotation = Color(0xFFBBB529)// Yellow/Gold
    private val darkType = Color(0xFF4EC9B0)      // Bright Teal
    private val darkFunction = Color(0xFF569CD6)  // Bright Blue
    private val darkTag = Color(0xFFE5C07B)       // Warm Yellow Tag
    private val darkAttr = Color(0xFF98C379)      // Light Green Attr
    private val darkDefault = Color(0xFFA9B7C6)   // Light Gray Body

    // Light theme color palette
    private val lightKeyword = Color(0xFF0033B3)  // Deep Blue
    private val lightString = Color(0xFF067D17)   // Deep Green
    private val lightNumber = Color(0xFF1750EB)   // Indigo/Blue
    private val lightComment = Color(0xFF8C8C8C)  // Medium Gray
    private val lightAnnotation = Color(0xFF9E880D)// Dark Gold
    private val lightType = Color(0xFF00739D)     // Deep Teal
    private val lightFunction = Color(0xFF00627A) // Deep Cyan
    private val lightTag = Color(0xFF228B22)      // Forest Green Tag
    private val lightAttr = Color(0xFF000080)     // Navy Attr
    private val lightDefault = Color(0xFF1E1E1E)  // Dark Charcoal Body

    private val kotlinKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "typealias", "constructor", "init", "this", "super", "if", "else", "when",
        "try", "catch", "finally", "for", "while", "do", "return", "break", "continue",
        "is", "in", "as", "throw", "null", "true", "false", "sealed", "data", "enum",
        "open", "abstract", "private", "protected", "public", "internal", "override",
        "suspend", "inline", "noinline", "crossinline", "infix", "operator", "out", "by"
    )

    private val javaKeywords = setOf(
        "package", "import", "public", "private", "protected", "class", "interface",
        "extends", "implements", "static", "final", "void", "int", "boolean", "long",
        "double", "float", "char", "byte", "short", "new", "this", "super", "if",
        "else", "switch", "case", "default", "while", "do", "for", "return", "break",
        "continue", "try", "catch", "finally", "throw", "throws", "null", "true", "false",
        "volatile", "transient", "synchronized", "instanceof"
    )

    private val pythonKeywords = setOf(
        "def", "class", "import", "from", "as", "return", "if", "elif", "else",
        "while", "for", "in", "try", "except", "finally", "raise", "with", "lambda",
        "pass", "break", "continue", "and", "or", "not", "is", "None", "True", "False", "async", "await"
    )

    private val jsKeywords = setOf(
        "function", "const", "let", "var", "return", "if", "else", "for", "while",
        "switch", "case", "default", "break", "continue", "try", "catch", "finally",
        "import", "export", "from", "default", "async", "await", "class", "extends",
        "super", "this", "new", "null", "undefined", "true", "false", "type", "interface"
    )

    private val sqlKeywords = setOf(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "INSERT", "INTO", "VALUES",
        "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "ADD",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "JOIN", "INNER", "LEFT", "RIGHT",
        "FULL", "ON", "GROUP", "BY", "ORDER", "ASC", "DESC", "LIMIT", "OFFSET", "HAVING", "AS"
    )

    fun highlightLine(
        line: String,
        language: String,
        isDarkTheme: Boolean = true
    ): AnnotatedString {
        if (line.isEmpty()) return AnnotatedString("")

        val kwColor = if (isDarkTheme) darkKeyword else lightKeyword
        val strColor = if (isDarkTheme) darkString else lightString
        val numColor = if (isDarkTheme) darkNumber else lightNumber
        val cmtColor = if (isDarkTheme) darkComment else lightComment
        val annColor = if (isDarkTheme) darkAnnotation else lightAnnotation
        val typeColor = if (isDarkTheme) darkType else lightType
        val fnColor = if (isDarkTheme) darkFunction else lightFunction
        val tagColor = if (isDarkTheme) darkTag else lightTag
        val attrColor = if (isDarkTheme) darkAttr else lightAttr
        val defColor = if (isDarkTheme) darkDefault else lightDefault

        val trimmed = line.trimStart()

        // 1. Comments
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = cmtColor, fontWeight = FontWeight.Normal)) {
                    append(line)
                }
            }
        }
        if ((language == "python" || language == "bash" || language == "yaml" || language == "toml") && trimmed.startsWith("#")) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = cmtColor, fontWeight = FontWeight.Normal)) {
                    append(line)
                }
            }
        }
        if (language == "sql" && trimmed.startsWith("--")) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = cmtColor, fontWeight = FontWeight.Normal)) {
                    append(line)
                }
            }
        }

        // 2. XML / HTML / SVG
        if (language == "xml" || language == "html") {
            return highlightXmlLine(line, tagColor, attrColor, strColor, cmtColor, defColor)
        }

        // Select keyword set based on language
        val keywords = when (language) {
            "kotlin" -> kotlinKeywords
            "java" -> javaKeywords
            "python" -> pythonKeywords
            "javascript", "typescript" -> jsKeywords
            "sql" -> sqlKeywords
            else -> kotlinKeywords + javaKeywords + pythonKeywords + jsKeywords
        }

        return buildAnnotatedString {
            var i = 0
            val len = line.length

            while (i < len) {
                val c = line[i]

                // Line Comment check mid-line
                if (c == '/' && i + 1 < len && line[i + 1] == '/') {
                    withStyle(SpanStyle(color = cmtColor)) {
                        append(line.substring(i))
                    }
                    break
                }
                if ((language == "python" || language == "bash" || language == "yaml") && c == '#') {
                    withStyle(SpanStyle(color = cmtColor)) {
                        append(line.substring(i))
                    }
                    break
                }

                // String Literals ("..." or '...')
                if (c == '"' || c == '\'') {
                    val quoteChar = c
                    val start = i
                    i++
                    while (i < len && line[i] != quoteChar) {
                        if (line[i] == '\\' && i + 1 < len) i++
                        i++
                    }
                    if (i < len) i++ // Include closing quote
                    withStyle(SpanStyle(color = strColor)) {
                        append(line.substring(start, i))
                    }
                    continue
                }

                // Annotations (@Composable, @Override, @Nullable, etc.)
                if (c == '@' && (i == 0 || line[i - 1].isWhitespace())) {
                    val start = i
                    i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) {
                        i++
                    }
                    withStyle(SpanStyle(color = annColor, fontWeight = FontWeight.Bold)) {
                        append(line.substring(start, i))
                    }
                    continue
                }

                // Numbers
                if (c.isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit())) {
                    val start = i
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) {
                        i++
                    }
                    withStyle(SpanStyle(color = numColor)) {
                        append(line.substring(start, i))
                    }
                    continue
                }

                // Identifier or Keyword or Function
                if (c.isLetter() || c == '_' || c == '$') {
                    val start = i
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$')) {
                        i++
                    }
                    val word = line.substring(start, i)

                    // Check if function call (followed by '(')
                    var nextCharIdx = i
                    while (nextCharIdx < len && line[nextCharIdx].isWhitespace()) nextCharIdx++
                    val isFunctionCall = nextCharIdx < len && line[nextCharIdx] == '('

                    // Check if Type (starts with uppercase)
                    val isType = word.first().isUpperCase() && !keywords.contains(word.uppercase())

                    when {
                        keywords.contains(word) || keywords.contains(word.uppercase()) -> {
                            withStyle(SpanStyle(color = kwColor, fontWeight = FontWeight.Bold)) {
                                append(word)
                            }
                        }
                        isType -> {
                            withStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.SemiBold)) {
                                append(word)
                            }
                        }
                        isFunctionCall -> {
                            withStyle(SpanStyle(color = fnColor)) {
                                append(word)
                            }
                        }
                        else -> {
                            withStyle(SpanStyle(color = defColor)) {
                                append(word)
                            }
                        }
                    }
                    continue
                }

                // Default non-alphanumeric character
                withStyle(SpanStyle(color = defColor)) {
                    append(c.toString())
                }
                i++
            }
        }
    }

    private fun highlightXmlLine(
        line: String,
        tagColor: Color,
        attrColor: Color,
        strColor: Color,
        cmtColor: Color,
        defColor: Color
    ): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = line.length

            while (i < len) {
                val c = line[i]

                if (c == '<') {
                    val start = i
                    i++
                    if (i < len && (line[i] == '/' || line[i] == '?' || line[i] == '!')) i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == ':' || line[i] == '-')) i++
                    withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) {
                        append(line.substring(start, i))
                    }
                    continue
                }

                if (c == '"' || c == '\'') {
                    val quoteChar = c
                    val start = i
                    i++
                    while (i < len && line[i] != quoteChar) i++
                    if (i < len) i++
                    withStyle(SpanStyle(color = strColor)) {
                        append(line.substring(start, i))
                    }
                    continue
                }

                if (c.isLetter()) {
                    val start = i
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == ':' || line[i] == '-')) i++
                    val word = line.substring(start, i)
                    var nextIdx = i
                    while (nextIdx < len && line[nextIdx].isWhitespace()) nextIdx++
                    val isAttr = nextIdx < len && line[nextIdx] == '='

                    if (isAttr) {
                        withStyle(SpanStyle(color = attrColor)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = defColor)) {
                            append(word)
                        }
                    }
                    continue
                }

                withStyle(SpanStyle(color = defColor)) {
                    append(c.toString())
                }
                i++
            }
        }
    }
}

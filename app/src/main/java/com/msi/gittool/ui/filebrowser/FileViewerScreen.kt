package com.msi.gittool.ui.filebrowser

import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    viewModel: FileViewerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val extension = uiState.path.substringAfterLast(".", "").lowercase()

    LaunchedEffect(Unit) {
        viewModel.loadFile(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.path.substringAfterLast("/"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.shareFileContent(context) },
                        modifier = Modifier.testTag("share_file_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share Link")
                    }
                    IconButton(
                        onClick = {
                            val clipManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = android.content.ClipData.newPlainText("Raw Code", uiState.editableContent)
                            clipManager.setPrimaryClip(clipData)
                            Toast.makeText(context, "Copied code to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_code_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Raw Code")
                    }
                    IconButton(
                        onClick = { viewModel.downloadFile(context) },
                        modifier = Modifier.testTag("download_file_button")
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Download File")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && uiState.errorMessage.isNullOrEmpty()) {
                if (uiState.isImage) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.downloadImageUsingDownloadManager(context) },
                        icon = { Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Download Image") },
                        text = { Text("Download Image") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("download_image_fab")
                    )
                } else if (!uiState.isVideo && !uiState.isAudio) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            val clipManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = android.content.ClipData.newPlainText("Raw Code", uiState.editableContent)
                            clipManager.setPrimaryClip(clipData)
                            Toast.makeText(context, "Copied entire raw code content to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        icon = { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Raw Code") },
                        text = { Text("Copy Code") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("copy_code_fab")
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!uiState.errorMessage.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                if (uiState.isImage) {
                    ImagePlayerView(imageModel = uiState.tempCacheFilePath?.let { java.io.File(it) } ?: uiState.contentItem?.download_url, fileName = uiState.path)
                } else if (uiState.isVideo) {
                    VideoPlayerView(videoPath = uiState.tempCacheFilePath, videoUrl = uiState.contentItem?.download_url)
                } else if (uiState.isAudio) {
                    AudioPlayerView(audioPath = uiState.tempCacheFilePath, audioUrl = uiState.contentItem?.download_url, fileName = uiState.path.substringAfterLast("/"))
                } else {
                    // Compose local, native Notepad++ Code Editor with Synchronized Line Numbers
                    CodePlaygroundEditor(
                        content = uiState.editableContent,
                        extension = extension,
                        onContentChange = { newText ->
                            viewModel.updateCodeContent(newText, context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ImagePlayerView(imageModel: Any?, fileName: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                )
            }
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = "Preview of $fileName",
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state)
                .fillMaxSize()
        )
    }
}

@Composable
fun VideoPlayerView(videoPath: String?, videoUrl: String?) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }

    // Position progress tracking daemon
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPos = vv.currentPosition.toFloat()
                    val durState = vv.duration.toFloat()
                    if (durState > 0) duration = durState
                } else {
                    isPlaying = false
                }
            }
            delay(200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14)),
        contentAlignment = Alignment.Center
    ) {
        if (!videoPath.isNullOrEmpty() || !videoUrl.isNullOrEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                if (!videoPath.isNullOrEmpty()) {
                                    setVideoPath(videoPath)
                                } else if (!videoUrl.isNullOrEmpty()) {
                                    setVideoURI(Uri.parse(videoUrl))
                                }
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    duration = mp.duration.toFloat()
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPos = 0f
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Interactive customized seek bar overlays and controller buttons
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(currentPos),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Slider(
                                value = currentPos,
                                onValueChange = { newVal ->
                                    currentPos = newVal
                                    videoViewRef?.seekTo(newVal.toInt())
                                },
                                valueRange = 0f..duration,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )

                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Controller action buttons
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            IconButton(onClick = {
                                videoViewRef?.let { vv ->
                                    val destination = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(destination)
                                    currentPos = destination.toFloat()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Replay10, contentDescription = "Rewind 10s")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            IconButton(
                                onClick = {
                                    videoViewRef?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            IconButton(onClick = {
                                videoViewRef?.let { vv ->
                                    val destination = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                    vv.seekTo(destination)
                                    currentPos = destination.toFloat()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Forward10, contentDescription = "Forward 10s")
                            }
                        }
                    }
                }
            }
        } else {
            Text("No video file link is accessible.", color = Color.White)
        }
    }
}

@Composable
fun AudioPlayerView(audioPath: String?, audioUrl: String?, fileName: String) {
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(100f) }
    var isPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(audioPath, audioUrl) {
        val dataSource = audioPath ?: audioUrl
        if (!dataSource.isNullOrEmpty()) {
            val mp = MediaPlayer().apply {
                setDataSource(dataSource)
                setOnPreparedListener { prepareMediaPlayer ->
                    duration = prepareMediaPlayer.duration.toFloat()
                    isPrepared = true
                }
                setOnCompletionListener {
                    isPlaying = false
                    currentPos = 0f
                }
                prepareAsync()
            }
            mediaPlayerRef = mp
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef?.release()
            mediaPlayerRef = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayerRef?.let { mp ->
                if (mp.isPlaying) {
                    currentPos = mp.currentPosition.toFloat()
                } else {
                    isPlaying = false
                }
            }
            delay(200)
        }
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isPlaying) 36000f else 0f,
        animationSpec = if (isPlaying) {
            infiniteRepeatable(
                animation = tween(durationMillis = 300000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            snap()
        },
        label = "DiscRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Beautiful record disc vinyl spinning layout
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .rotate(rotationAngle)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Audio Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatTime(currentPos),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = currentPos,
                        onValueChange = { newVal ->
                            currentPos = newVal
                            mediaPlayerRef?.seekTo(newVal.toInt())
                        },
                        valueRange = 0f..duration,
                        enabled = isPrepared,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )

                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = {
                        mediaPlayerRef?.let { mp ->
                            if (isPrepared) {
                                val destination = (mp.currentPosition - 10000).coerceAtLeast(0)
                                mp.seekTo(destination)
                                currentPos = destination.toFloat()
                            }
                        }
                    }, enabled = isPrepared) {
                        Icon(imageVector = Icons.Default.Replay10, contentDescription = "Rewind 10s")
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    IconButton(
                        onClick = {
                            mediaPlayerRef?.let { mp ->
                                if (isPrepared) {
                                    if (mp.isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        mp.start()
                                        isPlaying = true
                                    }
                                }
                            }
                        },
                        enabled = isPrepared,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    IconButton(onClick = {
                        mediaPlayerRef?.let { mp ->
                            if (isPrepared) {
                                val destination = (mp.currentPosition + 10000).coerceAtMost(mp.duration)
                                mp.seekTo(destination)
                                currentPos = destination.toFloat()
                            }
                        }
                    }, enabled = isPrepared) {
                        Icon(imageVector = Icons.Default.Forward10, contentDescription = "Forward 10s")
                    }
                }
            }
        }
    }
}

@Composable
fun CodePlaygroundEditor(
    content: String,
    extension: String,
    onContentChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val localContext = LocalContext.current

    val syntaxHighlightTransformation = remember(extension) {
        VisualTransformation { text ->
            TransformedText(
                highlightSyntax(text.text, extension),
                OffsetMapping.Identity
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // Upper status context bar
        Surface(
            color = Color(0xFF161B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Code Language Icon",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Language: ${extension.uppercase().ifEmpty { "TEXT" }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E)
                    )
                }

                Text(
                    text = "${content.split("\n").size} lines",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8B949E)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .background(Color(0xFF161B22))
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                val linesCount = content.split("\n").size
                for (i in 1..linesCount) {
                    Text(
                        text = "$i",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF484F58),
                        modifier = Modifier.padding(end = 8.dp, bottom = 1.34.dp)
                    )
                }
            }

            // Real-time custom syntax-highlighted basic editor
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    color = Color(0xFFC9D1D9),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.2.sp
                ),
                visualTransformation = syntaxHighlightTransformation,
                cursorBrush = SolidColor(Color(0xFF58A6FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

fun formatTime(ms: Float): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun highlightSyntax(text: String, extension: String): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    val lang = extension.lowercase()

    // 1. Comments
    if (lang == "py" || lang == "sh" || lang == "yaml" || lang == "yml" || lang == "properties") {
        val regex = Regex("#.*")
        regex.findAll(text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color(0xFF6A9955)),
                match.range.first,
                match.range.last + 1
            )
        }
    } else {
        val singleLineComment = Regex("//.*")
        singleLineComment.findAll(text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color(0xFF6A9955)),
                match.range.first,
                match.range.last + 1
            )
        }
        val multiLineComment = Regex("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/")
        multiLineComment.findAll(text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color(0xFF6A9955)),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    // 2. Double-Quoted and Single-Quoted Strings
    val doubleQuotes = Regex("\"([^\"]|\\\\\")*\"")
    doubleQuotes.findAll(text).forEach { match ->
        builder.addStyle(
            SpanStyle(color = Color(0xFFCE9178)),
            match.range.first,
            match.range.last + 1
        )
    }
    val singleQuotes = Regex("'([^']|\\\\')*'")
    singleQuotes.findAll(text).forEach { match ->
        builder.addStyle(
            SpanStyle(color = Color(0xFFCE9178)),
            match.range.first,
            match.range.last + 1
        )
    }

    // 3. Language keywords
    val keywords = when (lang) {
        "kt", "kts", "java" -> setOf(
            "class", "interface", "fun", "val", "var", "import", "package", "return", "if", "else", 
            "for", "while", "try", "catch", "public", "private", "protected", "override", "internal", 
            "object", "enum", "null", "this", "super", "throw", "new", "void", "static", "final", 
            "extends", "implements", "throws", "suspend", "as", "is", "in", "break", "continue"
        )
        "py" -> setOf(
            "def", "class", "import", "from", "as", "return", "if", "elif", "else", "for", "while", 
            "try", "except", "finally", "lambda", "in", "is", "not", "and", "or", "None", "True", 
            "False", "yield", "global", "nonlocal", "with", "assert", "break", "continue", "pass"
        )
        "cpp", "hpp", "cc", "cxx", "c", "h", "cs" -> setOf(
            "class", "struct", "void", "int", "float", "double", "char", "bool", "if", "else", "for", 
            "while", "do", "switch", "case", "default", "break", "continue", "return", "public", 
            "private", "protected", "virtual", "override", "using", "namespace", "include", "define", 
            "static", "const", "new", "delete", "this", "try", "catch", "throw", "null", "nullptr", "true", "false"
        )
        "js", "ts" -> setOf(
            "const", "let", "var", "function", "class", "import", "export", "from", "return", "if", 
            "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "try", 
            "catch", "finally", "throw", "new", "this", "super", "null", "undefined", "true", "false", 
            "async", "await", "yield", "interface", "type", "public", "private", "protected"
        )
        else -> setOf(
            "class", "fun", "val", "var", "return", "if", "else", "for", "while", "import", "package", "try", "catch"
        )
    }

    val keywordRegex = Regex("\\b(${keywords.joinToString("|")})\\b")
    keywordRegex.findAll(text).forEach { match ->
        builder.addStyle(
            SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold),
            match.range.first,
            match.range.last + 1
        )
    }

    // 4. Highlight premium type declarations
    val types = setOf(
        "String", "Int", "Boolean", "Double", "Float", "Long", "Short", "Byte", "Char", "Unit", "Any",
        "List", "Map", "Set", "void", "int", "bool", "char", "double", "float"
    )
    val typeRegex = Regex("\\b(${types.joinToString("|")})\\b")
    typeRegex.findAll(text).forEach { match ->
        builder.addStyle(
            SpanStyle(color = Color(0xFF4EC9B0)),
            match.range.first,
            match.range.last + 1
        )
    }

    // 5. Highlights for numbers
    val numberRegex = Regex("\\b\\d+b?\\b")
    numberRegex.findAll(text).forEach { match ->
        builder.addStyle(
            SpanStyle(color = Color(0xFFB5CEA8)),
            match.range.first,
            match.range.last + 1
        )
    }

    // 6. Annotations (like @Composable)
    val annotationRegex = Regex("@[A-Za-z0-9_]+")
    annotationRegex.findAll(text).forEach { match ->
        builder.addStyle(
            SpanStyle(color = Color(0xFFFF8C00)),
            match.range.first,
            match.range.last + 1
        )
    }

    return builder.toAnnotatedString()
}

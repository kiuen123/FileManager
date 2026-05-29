package com.example.filemanager.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.filemanager.model.FileItem
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun AudioPlayerScreen(
    audioFiles: List<FileItem>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, audioFiles.lastIndex)) }
    val currentFile = audioFiles.getOrNull(currentIndex) ?: return

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Load & play when index changes
    LaunchedEffect(currentIndex) {
        val file = audioFiles.getOrNull(currentIndex) ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(file.path))))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isRepeat by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    if (isRepeat) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else {
                        val next = if (isShuffle) audioFiles.indices.random() else currentIndex + 1
                        if (next <= audioFiles.lastIndex) currentIndex = next
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Progress ticker
    LaunchedEffect(Unit) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (exoPlayer.duration > 0) duration = exoPlayer.duration
            }
            delay(500)
        }
    }

    BackHandler { exoPlayer.pause(); onDismiss() }

    Dialog(
        onDismissRequest = { exoPlayer.pause(); onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D1B4E), Color(0xFF000000))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exoPlayer.pause(); onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Text(
                        "Đang phát nhạc",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(48.dp))
                }

                Spacer(Modifier.weight(1f))

                // Album art placeholder
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(96.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Song title + index
                Text(
                    text = currentFile.name.substringBeforeLast("."),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                if (audioFiles.size > 1) {
                    Text(
                        "${currentIndex + 1} / ${audioFiles.size} bài",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(28.dp))

                // Seek bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { frac ->
                        val seekTo = (frac * duration).toLong()
                        exoPlayer.seekTo(seekTo)
                        currentPosition = seekTo
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                // Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatAudioTime(currentPosition), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text(formatAudioTime(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Extra controls row (Shuffle / Repeat)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isShuffle = !isShuffle }) {
                        Icon(
                            Icons.Default.Shuffle,
                            null,
                            tint = if (isShuffle) Color(0xFF82B1FF) else Color.White.copy(alpha = 0.45f)
                        )
                    }
                    IconButton(onClick = { isRepeat = !isRepeat }) {
                        Icon(
                            Icons.Default.Repeat,
                            null,
                            tint = if (isRepeat) Color(0xFF82B1FF) else Color.White.copy(alpha = 0.45f)
                        )
                    }
                }

                // Main controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip previous
                    IconButton(
                        onClick = {
                            if (exoPlayer.currentPosition > 3000) {
                                exoPlayer.seekTo(0)
                            } else if (currentIndex > 0) {
                                currentIndex--
                            }
                        },
                        enabled = currentIndex > 0 || exoPlayer.currentPosition > 3000,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            null,
                            tint = if (currentIndex > 0) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF0D1B4E),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // Skip next
                    IconButton(
                        onClick = { if (currentIndex < audioFiles.lastIndex) currentIndex++ },
                        enabled = currentIndex < audioFiles.lastIndex,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            null,
                            tint = if (currentIndex < audioFiles.lastIndex) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatAudioTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.getDefault(), "%d:%02d", min, sec)
}


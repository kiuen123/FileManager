package com.example.filemanager.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.filemanager.model.FileItem
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    file: FileItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Dùng FileProvider URI để hỗ trợ ổ cứng ngoài (external storage)
    val videoUri = remember(file.path) {
        try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                File(file.path)
            )
        } catch (e: Exception) {
            Uri.fromFile(File(file.path)) // fallback
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
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
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowPreviousButton(false)
                        setShowNextButton(false)
                        setShowFastForwardButton(true)
                        setShowRewindButton(true)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { playerView -> playerView.player = exoPlayer }
            )

            // Back button
            IconButton(
                onClick = { exoPlayer.pause(); onDismiss() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
        }
    }
}

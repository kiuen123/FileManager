package com.example.filemanager.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.model.FileItem
import com.example.filemanager.viewmodel.FileManagerViewModel

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon / Selection indicator
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                val (icon, color) = getFileIcon(item)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Text(
                    text = FileManagerViewModel.formatDate(item.lastModified),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.isDirectory) {
                    Text(
                        text = "  •  ${FileManagerViewModel.formatFileSize(item.size)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Tùy chọn",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val (icon, color) = getFileIcon(item)
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .padding(2.dp)
            ) {
                Icon(Icons.Default.MoreVert, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp))
            }
        }
    }
}

fun getFileIcon(item: FileItem): Pair<ImageVector, Color> {
    if (item.isDirectory) return Pair(Icons.Default.Folder, Color(0xFFFFA726))
    return when (item.extension) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" ->
            Pair(Icons.Default.Image, Color(0xFF42A5F5))
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm" ->
            Pair(Icons.Default.PlayCircle, Color(0xFFAB47BC))
        "mp3", "wav", "flac", "ogg", "aac", "m4a" ->
            Pair(Icons.Default.MusicNote, Color(0xFFEF5350))
        "pdf" ->
            Pair(Icons.Default.PictureAsPdf, Color(0xFFEF5350))
        "doc", "docx" ->
            Pair(Icons.Default.Description, Color(0xFF1E88E5))
        "xls", "xlsx" ->
            Pair(Icons.Default.TableChart, Color(0xFF43A047))
        "ppt", "pptx" ->
            Pair(Icons.Default.Slideshow, Color(0xFFFF7043))
        "zip", "rar", "7z", "tar", "gz", "bz2" ->
            Pair(Icons.Default.FolderZip, Color(0xFF8D6E63))
        "apk" ->
            Pair(Icons.Default.Android, Color(0xFF66BB6A))
        "txt", "log", "md" ->
            Pair(Icons.Default.TextSnippet, Color(0xFF78909C))
        "kt", "java", "py", "js", "ts", "html", "css", "xml", "json", "cpp", "c" ->
            Pair(Icons.Default.Code, Color(0xFF26A69A))
        else ->
            Pair(Icons.Default.InsertDriveFile, Color(0xFF90A4AE))
    }
}




package com.example.filemanager.ui.components

import android.os.Environment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.R
import com.example.filemanager.model.StorageInfo
import com.example.filemanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QuickLink(
    val label: String,
    val path: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun StorageDrawerContent(
    storageList: List<StorageInfo>,
    currentPath: String,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    onOpenDrive: () -> Unit = {},
    onOpenNetwork: () -> Unit = {},
    onClearCache: (onDone: (freedBytes: Long) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var cacheSize by remember { mutableLongStateOf(0L) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    // Tính kích thước cache khi drawer mở
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cacheSize = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }
    }

    val internalRoot = Environment.getExternalStorageDirectory().absolutePath
    val quickLinks = remember(internalRoot) {
        listOf(
            QuickLink("Tải về", "$internalRoot/Download", Icons.Default.Download, Color(0xFF42A5F5)),
            QuickLink("Hình ảnh", "$internalRoot/Pictures", Icons.Default.Image, Color(0xFF66BB6A)),
            QuickLink("Camera", "$internalRoot/DCIM", Icons.Default.CameraAlt, Color(0xFFEF5350)),
            QuickLink("Âm nhạc", "$internalRoot/Music", Icons.Default.MusicNote, Color(0xFFFF7043)),
            QuickLink("Video", "$internalRoot/Movies", Icons.Default.VideoLibrary, Color(0xFFAB47BC)),
            QuickLink("Tài liệu", "$internalRoot/Documents", Icons.Default.Description, Color(0xFF1E88E5)),
            QuickLink("Android", "$internalRoot/Android", Icons.Default.Android, Color(0xFF26A69A)),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .verticalScroll(scrollState)
    ) {
        // ── Header ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                    )
                )
                .padding(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.file),
                        contentDescription = "Logo",
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "File Manager",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quản lý file thông minh",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Nguồn lưu trữ ──────────────────────────────────
        SectionHeader("NGUỒN LƯU TRỮ")

        if (storageList.isEmpty()) {
            // Loading or no permission
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            storageList.forEach { storage ->
                StorageVolumeItem(
                    storage = storage,
                    isSelected = currentPath.startsWith(storage.path),
                    onClick = {
                        // Chuyển nguồn mà không đóng drawer
                        onNavigate(storage.path)
                    }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

        // ── Google Drive ────────────────────────────────────────────
        SectionHeader("CLOUD")
        DriveStorageItem(onClick = { onOpenDrive(); onClose() })

        // ── Ổ cứng mạng ────────────────────────────────────────────
        NetworkStorageItem(onClick = { onOpenNetwork(); onClose() })

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

        // ── Truy cập nhanh ─────────────────────────────────────────────
        SectionHeader("TRUY CẬP NHANH")

        quickLinks.forEach { link ->
            QuickLinkItem(
                link = link,
                isSelected = currentPath == link.path,
                onClick = {
                    onNavigate(link.path)
                    onClose()
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

        // ── Công cụ ─────────────────────────────────────────────────────────
        SectionHeader("CÔNG CỤ")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showClearCacheDialog = true }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEF5350).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Xóa cache",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = if (cacheSize > 0)
                        "Đang dùng: ${FileManagerViewModel.formatFileSize(cacheSize)}"
                    else "Cache trống",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // ── Dialog xác nhận xóa cache ─────────────────────────────────────────
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Xóa cache", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Xóa ${FileManagerViewModel.formatFileSize(cacheSize)} dữ liệu cache của ứng dụng?\n\nBao gồm ảnh xem trước, video/nhạc đã tải tạm và các file cache khác."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        onClearCache { freed ->
                            cacheSize = 0L
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Hủy") }
            }
        )
    }
}

// ── Google Drive item ──────────────────────────────────────────────────────────

@Composable
private fun DriveStorageItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A73E8).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = Color(0xFF1A73E8),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Google Drive",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Truy cập file trên cloud",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.OpenInNew,
            contentDescription = null,
            tint = Color(0xFF1A73E8),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── Network Storage item ───────────────────────────────────────────────────────

@Composable
private fun NetworkStorageItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF00897B).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = Color(0xFF00897B),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ổ cứng mạng",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "FTP · SFTP · SMB / NAS",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.OpenInNew,
            contentDescription = null,
            tint = Color(0xFF00897B),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun StorageVolumeItem(
    storage: StorageInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    else Color.Transparent

    val iconTint = if (storage.isSdCard) Color(0xFF66BB6A) else Color(0xFF1E88E5)
    val iconVec = if (storage.isSdCard) Icons.Default.SdCard else Icons.Default.PhoneAndroid

    val animatedProgress by animateFloatAsState(
        targetValue = storage.usagePercent,
        animationSpec = tween(800),
        label = "storageProgress"
    )

    val progressColor = when {
        storage.usagePercent >= 0.9f -> MaterialTheme.colorScheme.error
        storage.usagePercent >= 0.75f -> Color(0xFFFF9800)
        else -> iconTint
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Storage icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else iconTint.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconVec, null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storage.name,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                if (storage.totalBytes > 0) {
                    Text(
                        text = "${FileManagerViewModel.formatFileSize(storage.usedBytes)} / ${FileManagerViewModel.formatFileSize(storage.totalBytes)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Selected indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check, null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Progress bar
        if (storage.totalBytes > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Còn trống: ${FileManagerViewModel.formatFileSize(storage.freeBytes)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(storage.usagePercent * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else progressColor
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickLinkItem(
    link: QuickLink,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(link.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(link.icon, null, tint = link.color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = link.label,
            fontSize = 14.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}


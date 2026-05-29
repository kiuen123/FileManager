package com.example.filemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.model.DiscoveredServer
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.NetworkConnection
import com.example.filemanager.model.NetworkItem
import com.example.filemanager.model.NetworkProtocol
import com.example.filemanager.viewmodel.FileManagerViewModel
import com.example.filemanager.viewmodel.NetworkDiscoveryState
import com.example.filemanager.viewmodel.NetworkStorageViewModel
import java.util.Locale

val NetworkColor = Color(0xFF00897B)  // Teal cho network storage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NetworkStorageScreen(
    vm: NetworkStorageViewModel,
    onBack: () -> Unit
) {
    val connections by vm.connections.collectAsState()
    val activeConn by vm.activeConnection.collectAsState()
    val files by vm.files.collectAsState()
    val currentPath by vm.currentPath.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isConnecting by vm.isConnecting.collectAsState()
    val error by vm.error.collectAsState()
    val transfer by vm.transfer.collectAsState()
    val selectedItems by vm.selectedItems.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()

    val isSelecting = selectedItems.isNotEmpty()
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<NetworkConnection?>(null) }
    var contextMenuTarget by remember { mutableStateOf<NetworkItem?>(null) }
    var showDeleteItemDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NetworkItem?>(null) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    // Media preview state (sau khi tải về cache)
    var imageViewerStateNet by remember { mutableStateOf<FileItem?>(null) }
    var videoViewerFileNet by remember { mutableStateOf<FileItem?>(null) }
    var audioViewerFileNet by remember { mutableStateOf<FileItem?>(null) }

    // Overflow menu
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableLongStateOf(0L) }

    // Discovery
    val discoveryState by vm.discoveryState.collectAsState()
    val discoveredServers by vm.discoveredServers.collectAsState()
    var showDiscoverySheet by remember { mutableStateOf(false) }
    var discoveryAddTarget by remember { mutableStateOf<NetworkConnection?>(null) }

    LaunchedEffect(snackMessage) {
        snackMessage?.let { snackbarHost.showSnackbar(it); snackMessage = null }
    }

    // Always intercept back: route to the correct action at each navigation level
    BackHandler {
        when {
            isSelecting                              -> vm.clearSelection()
            activeConn != null && vm.canGoBack()     -> vm.navigateBack()
            activeConn != null                       -> vm.disconnectFromServer() // back to connection list
            else                                     -> onBack()                  // back to FileManagerScreen
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        when {
                            isSelecting -> Text("${selectedItems.size} đã chọn", fontWeight = FontWeight.SemiBold)
                            activeConn != null -> Text(activeConn!!.displayName, fontWeight = FontWeight.Bold)
                            else -> Text("Ổ cứng mạng", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        when {
                            isSelecting -> IconButton(onClick = vm::clearSelection) {
                                Icon(Icons.Default.Close, null)
                            }
                            activeConn != null && vm.canGoBack() -> IconButton(onClick = { vm.navigateBack() }) {
                                Icon(Icons.Default.ArrowBack, null)
                            }
                            activeConn != null -> IconButton(onClick = { vm.disconnectFromServer() }) {
                                Icon(Icons.Default.ArrowBack, "Ngắt kết nối")
                            }
                            else -> IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, null)
                            }
                        }
                    },
                    actions = {
                        if (isSelecting) {
                            IconButton(onClick = vm::selectAll) { Icon(Icons.Default.SelectAll, null) }
                            IconButton(onClick = { showDeleteItemDialog = true }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        } else if (activeConn != null) {
                            IconButton(onClick = { vm.loadFiles() }) { Icon(Icons.Default.Refresh, null) }
                            IconButton(onClick = { vm.disconnectFromServer() }) {
                                Icon(Icons.Default.WifiOff, null)
                            }
                        } else {
                            // Scan network button
                            IconButton(onClick = {
                                showDiscoverySheet = true
                                if (discoveryState is NetworkDiscoveryState.Idle ||
                                    discoveryState is NetworkDiscoveryState.Error) {
                                    vm.startDiscovery()
                                }
                            }) {
                                Icon(Icons.Default.NetworkCheck, "Tìm kiếm ổ cứng mạng")
                            }
                            // Overflow menu
                            Box {
                                IconButton(onClick = {
                                    cacheSize = vm.getMediaCacheSize()
                                    showOverflowMenu = true
                                }) {
                                    Icon(Icons.Default.MoreVert, "Tùy chọn thêm")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Xóa cache media")
                                                Text(
                                                    "Đang dùng: ${FileManagerViewModel.formatFileSize(cacheSize)}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showClearCacheDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NetworkColor,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )

                // Breadcrumb khi đang duyệt
                if (activeConn != null && !isSelecting) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth()
                            .background(NetworkColor.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(breadcrumbs) { (name, path) ->
                            val isLast = path == currentPath
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                color = if (isLast) NetworkColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = if (!isLast) Modifier.padding(horizontal = 2.dp)
                                    .clickable { vm.navigateToBreadcrumb(path) }
                                else Modifier.padding(horizontal = 2.dp)
                            )
                            if (!isLast) Icon(
                                Icons.Default.ChevronRight, null,
                                Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (activeConn != null && !isSelecting) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    FloatingActionButton(onClick = { showMenu = !showMenu }, containerColor = NetworkColor) {
                        Icon(if (showMenu) Icons.Default.Close else Icons.Default.Add, null, tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Tạo thư mục") },
                            onClick = { showCreateFolderDialog = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = NetworkColor) }
                        )
                    }
                }
            } else if (activeConn == null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = NetworkColor
                ) { Icon(Icons.Default.Add, null, tint = Color.White) }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                // ── Danh sách kết nối ──────────────────────────────────
                activeConn == null -> {
                    if (connections.isEmpty()) {
                        EmptyConnectionsState { showAddDialog = true }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(connections, key = { it.id }) { conn ->
                                ConnectionItem(
                                    conn = conn,
                                    isConnecting = isConnecting,
                                    onClick = { vm.connectTo(conn) },
                                    onEdit = { editTarget = conn },
                                    onDelete = {
                                        vm.deleteConnection(conn.id)
                                        snackMessage = "Đã xóa kết nối"
                                    }
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                // ── Đang kết nối ────────────────────────────────────────
                isConnecting -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NetworkColor)
                        Spacer(Modifier.height(16.dp))
                        Text("Đang kết nối ${activeConn?.host ?: ""}...")
                    }
                }

                // ── Lỗi ─────────────────────────────────────────────────
                error != null -> Column(
                    Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.clearError(); vm.disconnectFromServer() }, colors = ButtonDefaults.buttonColors(containerColor = NetworkColor)) { Text("Quay lại") }
                }

                // ── Đang tải ────────────────────────────────────────────
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = NetworkColor)
                }

                // ── File list ───────────────────────────────────────────
                files.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOff, null, Modifier.size(72.dp), tint = NetworkColor.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("Thư mục trống", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files, key = { it.path }) { item ->
                        NetworkFileItem(
                            item = item,
                            isSelected = item.path in selectedItems,
                            onClick = {
                                if (isSelecting) vm.toggleSelection(item.path)
                                else if (item.isDirectory) vm.navigateTo(item)
                                else contextMenuTarget = item
                            },
                            onLongClick = { vm.toggleSelection(item.path) },
                            onMenuClick = { contextMenuTarget = item }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    // ── Transfer progress ──────────────────────────────────────────────────
    transfer?.let { t ->
        AlertDialog(
            onDismissRequest = { if (t.isDone || t.error != null) vm.clearTransfer() },
            icon = {
                Icon(
                    if (t.isUpload) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                    null, tint = NetworkColor
                )
            },
            title = { Text(if (t.isDone) "Hoàn thành!" else if (t.isUpload) "Đang upload..." else "Đang tải...") },
            text = {
                Column {
                    Text(t.fileName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(t.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    } else {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { t.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = NetworkColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${(t.progress * 100).toInt()}%", fontSize = 12.sp)
                        if (t.isDone && t.savedPath.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Đã lưu: ${t.savedPath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                if (t.isDone || t.error != null) {
                    Button(onClick = vm::clearTransfer, colors = ButtonDefaults.buttonColors(containerColor = NetworkColor)) { Text("Đóng") }
                }
            }
        )
    }

    // ── Context menu ──────────────────────────────────────────────────────
    contextMenuTarget?.let { item ->
        ModalBottomSheet(onDismissRequest = { contextMenuTarget = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { if (!item.isDirectory) Text(FileManagerViewModel.formatFileSize(item.size)) },
                    leadingContent = {
                        Icon(
                            if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            null, tint = if (item.isDirectory) NetworkColor else Color.Gray
                        )
                    }
                )
                HorizontalDivider()
                if (!item.isDirectory) {
                    // Nút xem/phát trực tiếp cho file media
                    val ext = item.name.substringAfterLast('.', "").lowercase()
                    val isImage = ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif")
                    val isVideo = ext in setOf("mp4","mkv","avi","mov","wmv","flv","3gp","webm","ts","m4v")
                    val isAudio = ext in setOf("mp3","wav","flac","ogg","m4a","aac","opus","wma","amr")
                    if (isImage || isVideo || isAudio) {
                        val icon = when {
                            isImage -> Icons.Default.Image
                            isVideo -> Icons.Default.PlayCircle
                            else    -> Icons.Default.MusicNote
                        }
                        val label = when {
                            isImage -> "Xem ảnh"
                            isVideo -> "Phát video"
                            else    -> "Nghe nhạc"
                        }
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = { Icon(icon, null, tint = NetworkColor) },
                            modifier = Modifier.clickable {
                                contextMenuTarget = null
                                vm.downloadToCache(item) { localPath ->
                                    if (localPath != null) {
                                        val fi = FileItem(
                                            name = item.name,
                                            path = localPath,
                                            isDirectory = false,
                                            size = item.size,
                                            lastModified = 0L,
                                            extension = ext
                                        )
                                        when {
                                            isImage -> imageViewerStateNet = fi
                                            isVideo -> videoViewerFileNet = fi
                                            isAudio -> audioViewerFileNet = fi
                                        }
                                    }
                                }
                            }
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Tải xuống") },
                        leadingContent = { Icon(Icons.Default.Download, null, tint = NetworkColor) },
                        modifier = Modifier.clickable { contextMenuTarget = null; vm.downloadFile(item) }
                    )
                }
                ListItem(
                    headlineContent = { Text("Đổi tên") },
                    leadingContent = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    modifier = Modifier.clickable { renameTarget = item; contextMenuTarget = null }
                )
                ListItem(
                    headlineContent = { Text("Xóa", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        contextMenuTarget = null
                        vm.deleteItems(listOf(item)) { ok ->
                            snackMessage = if (ok) "Đã xóa" else "Xóa thất bại"
                        }
                    }
                )
            }
        }
    }

    // ── Delete confirm ────────────────────────────────────────────────────
    if (showDeleteItemDialog) {
        val toDelete = files.filter { it.path in selectedItems }
        AlertDialog(
            onDismissRequest = { showDeleteItemDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold) },
            text = { Text("Xóa ${toDelete.size} mục đã chọn trên máy chủ?") },
            confirmButton = {
                Button(onClick = {
                    vm.deleteItems(toDelete) { ok -> snackMessage = if (ok) "Đã xóa" else "Xóa thất bại" }
                    showDeleteItemDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { showDeleteItemDialog = false }) { Text("Hủy") } }
        )
    }

    // ── Create folder ─────────────────────────────────────────────────────
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Tạo thư mục", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = folderName, onValueChange = { folderName = it },
                    label = { Text("Tên thư mục") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    vm.createFolder(folderName) { ok -> snackMessage = if (ok) "Đã tạo thư mục" else "Tạo thất bại" }
                    showCreateFolderDialog = false
                }, enabled = folderName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = NetworkColor)) { Text("Tạo") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Hủy") } }
        )
    }

    // ── Rename ────────────────────────────────────────────────────────────
    renameTarget?.let { target ->
        var newName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Đổi tên", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it },
                    label = { Text("Tên mới") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    vm.rename(target, newName) { ok -> snackMessage = if (ok) "Đổi tên thành công" else "Đổi tên thất bại" }
                    renameTarget = null
                }, enabled = newName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = NetworkColor)) { Text("Xác nhận") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Hủy") } }
        )
    }

    // ── Clear cache dialog ────────────────────────────────────────────────
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Xóa cache media", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Xóa ${FileManagerViewModel.formatFileSize(cacheSize)} cache video/ảnh/nhạc đã xem tạm thời?\n\nCác file này sẽ được tải lại khi bạn mở lần sau."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        vm.clearMediaCache { freed ->
                            snackMessage = if (freed > 0)
                                "Đã xóa ${FileManagerViewModel.formatFileSize(freed)} cache"
                            else "Cache đã trống"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { showClearCacheDialog = false }) { Text("Hủy") } }
        )
    }

    // ── Add/Edit connection dialog ────────────────────────────────────────
    if (showAddDialog || editTarget != null) {
        AddConnectionDialog(
            initial = editTarget,
            onSave = { conn ->
                vm.saveConnection(conn)
                snackMessage = if (editTarget != null) "Đã cập nhật kết nối" else "Đã thêm kết nối"
                showAddDialog = false; editTarget = null
            },
            onDismiss = { showAddDialog = false; editTarget = null }
        )
    }

    // ── Discovery sheet ───────────────────────────────────────────────────
    if (showDiscoverySheet) {
        NetworkDiscoverySheet(
            state            = discoveryState,
            servers          = discoveredServers,
            onStart          = { vm.startDiscovery() },
            onStop           = { vm.stopDiscovery() },
            onAddServer      = { server ->
                showDiscoverySheet = false
                discoveryAddTarget = vm.buildConnectionFromDiscovery(server)
            },
            onDismiss = {
                showDiscoverySheet = false
                vm.stopDiscovery()
            }
        )
    }

    // AddConnectionDialog pre-filled from discovery
    discoveryAddTarget?.let { prefilled ->
        AddConnectionDialog(
            initial  = prefilled,
            onSave   = { conn ->
                vm.saveConnection(conn)
                snackMessage = "Đã thêm kết nối ${conn.displayName}"
                discoveryAddTarget = null
            },
            onDismiss = { discoveryAddTarget = null }
        )
    }

    // ── Media viewers (sau khi tải về cache) ─────────────────────────────────
    imageViewerStateNet?.let { fi ->
        ImageViewerScreen(
            images = listOf(fi),
            initialIndex = 0,
            onDismiss = { imageViewerStateNet = null; vm.clearTransfer() }
        )
    }
    videoViewerFileNet?.let { fi ->
        VideoPlayerScreen(
            file = fi,
            onDismiss = { videoViewerFileNet = null; vm.clearTransfer() }
        )
    }
    audioViewerFileNet?.let { fi ->
        AudioPlayerScreen(
            audioFiles = listOf(fi),
            initialIndex = 0,
            onDismiss = { audioViewerFileNet = null; vm.clearTransfer() }
        )
    }
}

// ── Connection list item ────────────────────────────────────────────────────

@Composable
private fun ConnectionItem(
    conn: NetworkConnection,
    isConnecting: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val protoColor = when (conn.protocol) {
        NetworkProtocol.FTP, NetworkProtocol.FTPS -> Color(0xFF1E88E5)
        NetworkProtocol.SFTP -> Color(0xFF43A047)
        NetworkProtocol.SMB -> Color(0xFF6D4C41)
    }
    val protoIcon: ImageVector = when (conn.protocol) {
        NetworkProtocol.FTP, NetworkProtocol.FTPS -> Icons.Default.Storage
        NetworkProtocol.SFTP -> Icons.Default.Lock
        NetworkProtocol.SMB -> Icons.Default.Computer
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isConnecting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(protoColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(protoIcon, null, tint = protoColor, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conn.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                "${conn.protocol.displayName}  ·  ${conn.host}:${conn.port}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NetworkColor)
        } else {
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Network file item ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NetworkFileItem(
    item: NetworkItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val bgColor = if (isSelected) NetworkColor.copy(alpha = 0.1f) else Color.Transparent
    val (iconColor, iconVec) = when {
        item.isDirectory -> NetworkColor to Icons.Default.Folder
        item.extension in listOf("jpg", "jpeg", "png", "gif", "webp") -> Color(0xFF43A047) to Icons.Default.Image
        item.extension in listOf("mp4", "mkv", "avi", "mov") -> Color(0xFFAB47BC) to Icons.Default.PlayCircle
        item.extension in listOf("mp3", "wav", "flac", "ogg") -> Color(0xFFEF5350) to Icons.Default.MusicNote
        item.extension == "pdf" -> Color(0xFFEF5350) to Icons.Default.PictureAsPdf
        item.extension in listOf("doc", "docx") -> Color(0xFF1E88E5) to Icons.Default.Description
        item.extension in listOf("xls", "xlsx") -> Color(0xFF43A047) to Icons.Default.TableChart
        item.extension in listOf("zip", "rar", "7z", "tar") -> Color(0xFF795548) to Icons.Default.FolderZip
        else -> Color(0xFF90A4AE) to Icons.Default.InsertDriveFile
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (isSelected) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(NetworkColor), Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            } else {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.12f)),
                    Alignment.Center
                ) {
                    Icon(iconVec, null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row {
                if (!item.isDirectory && item.size > 0) {
                    Text(formatNetSize(item.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.lastModified > 0) {
                    Text(
                        "  ·  ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(item.lastModified))}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyConnectionsState(onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE0F2F1), Color.White))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(NetworkColor.copy(alpha = 0.1f)),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.Storage, null, tint = NetworkColor, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("Ổ cứng mạng", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NetworkColor)
                Spacer(Modifier.height(8.dp))
                Text("Kết nối NAS, máy chủ FTP, SFTP hoặc Windows Share", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NetworkColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Thêm kết nối mới", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatNetSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val idx = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, idx.toDouble()), units[idx])
}

// ── Network Discovery Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDiscoverySheet(
    state: NetworkDiscoveryState,
    servers: List<DiscoveredServer>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAddServer: (DiscoveredServer) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { onStop(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NetworkColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.NetworkCheck, null,
                            tint = NetworkColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Tìm kiếm ổ cứng mạng",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Quét mạng LAN để tìm SMB, FTP, SFTP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Scan controls ─────────────────────────────────────────────
            item {
                when (state) {
                    is NetworkDiscoveryState.Idle -> {
                        Button(
                            onClick  = onStart,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = NetworkColor),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Bắt đầu quét", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    is NetworkDiscoveryState.Scanning -> {
                        val pct = if (state.total > 0) state.progress.toFloat() / state.total else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Đang quét… ${state.progress}/${state.total}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(pct * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = NetworkColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NetworkColor
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick  = onStop,
                            modifier = Modifier.fillMaxWidth(),
                            border   = BorderStroke(1.dp, NetworkColor),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, null, tint = NetworkColor, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dừng quét", color = NetworkColor)
                        }
                    }

                    is NetworkDiscoveryState.Done -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Quét hoàn tất · Tìm thấy ${servers.size} thiết bị",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick  = onStart,
                            modifier = Modifier.fillMaxWidth(),
                            border   = BorderStroke(1.dp, NetworkColor),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = NetworkColor, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Quét lại", color = NetworkColor)
                        }
                    }

                    is NetworkDiscoveryState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    state.message,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick  = onStart,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = NetworkColor),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Thử lại")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Results ───────────────────────────────────────────────────
            if (servers.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Đã tìm thấy (${servers.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        color      = NetworkColor
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(servers, key = { "${it.host}:${it.port}" }) { server ->
                    DiscoveredServerItem(server = server, onAdd = { onAddServer(server) })
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            } else if (state is NetworkDiscoveryState.Done) {
                item {
                    HorizontalDivider()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.WifiFind, null,
                                modifier = Modifier.size(52.dp),
                                tint     = NetworkColor.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Không tìm thấy thiết bị nào",
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Kiểm tra thiết bị đã bật chia sẻ chưa",
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredServerItem(
    server: DiscoveredServer,
    onAdd: () -> Unit
) {
    val (protoColor, protoIcon) = when (server.protocol) {
        NetworkProtocol.FTP, NetworkProtocol.FTPS -> Color(0xFF1E88E5) to Icons.Default.Storage
        NetworkProtocol.SFTP                       -> Color(0xFF43A047) to Icons.Default.Lock
        NetworkProtocol.SMB                        -> Color(0xFF6D4C41) to Icons.Default.Computer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(protoColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(protoIcon, null, tint = protoColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                server.host,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(server.protocol.displayName)
                    append(" · Port ${server.port}")
                    if (server.source == "mDNS") append(" · mDNS")
                },
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick          = onAdd,
            border           = BorderStroke(1.dp, NetworkColor),
            contentPadding   = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier         = Modifier.height(34.dp),
            shape            = RoundedCornerShape(8.dp)
        ) {
            Text("Thêm", color = NetworkColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


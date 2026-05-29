package com.example.filemanager.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.SortMode
import com.example.filemanager.model.ViewMode
import com.example.filemanager.ui.components.FileGridItem
import com.example.filemanager.ui.components.FileListItem
import com.example.filemanager.ui.components.StorageDrawerContent
import com.example.filemanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    vm: FileManagerViewModel,
    onOpenDrive: () -> Unit = {},
    onOpenNetwork: () -> Unit = {}
) {
    val context = LocalContext.current
    val files by vm.displayFiles.collectAsState()
    val currentPath by vm.currentPath.collectAsState()
    val selectedFiles by vm.selectedFiles.collectAsState()
    val clipboard by vm.clipboardData.collectAsState()
    val sortMode by vm.sortMode.collectAsState()
    val viewMode by vm.viewMode.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val showHidden by vm.showHidden.collectAsState()
    val storageList by vm.storageList.collectAsState()

    val isSelecting = selectedFiles.isNotEmpty()

    var isSearching by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var showContextMenu by remember { mutableStateOf<FileItem?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // --- Media viewer state ---
    var imageViewerState by remember { mutableStateOf<Pair<List<FileItem>, Int>?>(null) }
    var videoViewerFile by remember { mutableStateOf<FileItem?>(null) }
    var audioViewerState by remember { mutableStateOf<Pair<List<FileItem>, Int>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // Refresh storage info when screen shown
    LaunchedEffect(Unit) { vm.refreshStorageInfo() }

    // Double-back to exit at root
    var backPressedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            kotlinx.coroutines.delay(2000)
            backPressedOnce = false
        }
    }

    BackHandler {
        when {
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            isSelecting        -> vm.clearSelection()
            isSearching        -> { isSearching = false; vm.updateSearchQuery("") }
            vm.canGoBack()     -> vm.navigateBack()
            backPressedOnce    -> { /* second press → system exits */ android.os.Process.killProcess(android.os.Process.myPid()) }
            else               -> { backPressedOnce = true; snackbarMessage = "Bấm Back thêm lần nữa để thoát" }
        }
    }

    // Tên nguồn bộ nhớ hiện tại để hiển thị trên tiêu đề
    val currentStorageName = storageList
        .firstOrNull { currentPath.startsWith(it.path) }?.name
        ?: "File Manager"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight()
            ) {
                StorageDrawerContent(
                    storageList = storageList,
                    currentPath = currentPath,
                    onNavigate = { path ->
                        vm.navigateTo(path)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onClose = { coroutineScope.launch { drawerState.close() } },
                    onOpenDrive = onOpenDrive,
                    onOpenNetwork = onOpenNetwork,
                    onClearCache = { onDone ->
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val freed = context.cacheDir
                                .walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                            context.cacheDir.deleteRecursively()
                            onDone(freed)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                snackbarMessage = if (freed > 0)
                                    "Đã xóa ${FileManagerViewModel.formatFileSize(freed)} cache"
                                else "Cache đã trống"
                            }
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            if (isSearching) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = vm::updateSearchQuery,
                                    placeholder = { Text("Tìm kiếm...", fontSize = 15.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent
                                    )
                                )
                            } else if (isSelecting) {
                                Text("${selectedFiles.size} đã chọn", fontWeight = FontWeight.SemiBold)
                            } else {
                                Column {
                                    Text(
                                        text = currentStorageName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                    navigationIcon = {
                        when {
                            isSelecting -> IconButton(onClick = vm::clearSelection) {
                                Icon(Icons.Default.Close, "Bỏ chọn")
                            }
                            isSearching -> IconButton(onClick = {
                                isSearching = false; vm.updateSearchQuery("")
                            }) {
                                Icon(Icons.Default.ArrowBack, "Đóng tìm kiếm")
                            }
                            else -> IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Mở menu nguồn lưu trữ")
                            }
                        }
                    },
                    actions = {
                        if (isSelecting) {
                            IconButton(onClick = vm::selectAll) {
                                Icon(Icons.Default.SelectAll, "Chọn tất cả")
                            }
                        } else {
                            if (!isSearching) {
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Default.Search, "Tìm kiếm")
                                }
                            }
                            IconButton(onClick = vm::toggleViewMode) {
                                Icon(
                                    if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                    "Đổi chế độ xem"
                                )
                            }
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(Icons.Default.Sort, "Sắp xếp")
                            }
                            var showMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, "Thêm")
                                }
                                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (showHidden) "Ẩn file ẩn" else "Hiện file ẩn") },
                                        onClick = { vm.toggleShowHidden(); showMoreMenu = false },
                                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null) }
                                    )
                                    if (vm.canGoBack()) {
                                        DropdownMenuItem(
                                            text = { Text("Về thư mục gốc") },
                                            onClick = { vm.navigateToRoot(); showMoreMenu = false },
                                            leadingIcon = { Icon(Icons.Default.Home, null) }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )

                // Breadcrumb with back button
                if (!isSearching && !isSelecting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button — chỉ hiện khi có thể quay lại
                        if (vm.canGoBack()) {
                            IconButton(
                                onClick = { vm.navigateBack() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Quay lại",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        LazyRow(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val breadcrumbs = vm.getBreadcrumbs()
                            items(breadcrumbs.size) { index ->
                                val (name, path) = breadcrumbs[index]
                                val isLast = index == breadcrumbs.size - 1
                                if (!isLast) {
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Normal,
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .clickable { vm.navigateTo(path) }
                                    )
                                    Icon(
                                        Icons.Default.ChevronRight, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isSelecting) {
                SelectionBottomBar(
                    selectedCount = selectedFiles.size,
                    hasClipboard = clipboard != null,
                    onCopy = {
                        val items = files.filter { it.path in selectedFiles }
                        vm.copyFiles(items)
                        snackbarMessage = "Đã sao chép ${items.size} mục"
                    },
                    onCut = {
                        val items = files.filter { it.path in selectedFiles }
                        vm.cutFiles(items)
                        snackbarMessage = "Đã cắt ${items.size} mục"
                    },
                    onDelete = { showDeleteDialog = true },
                    onRename = {
                        if (selectedFiles.size == 1) {
                            val item = files.first { it.path in selectedFiles }
                            showRenameDialog = item
                        }
                    },
                    onShare = {
                        val items = files.filter { it.path in selectedFiles && !it.isDirectory }
                        shareFiles(context, items)
                    }
                )
            } else if (clipboard != null) {
                PasteBottomBar(
                    isCut = clipboard!!.isCut,
                    count = clipboard!!.files.size,
                    onPaste = {
                        vm.pasteFiles { success, msg ->
                            snackbarMessage = msg
                        }
                    },
                    onClear = vm::clearClipboard
                )
            }
        },
        floatingActionButton = {
            if (!isSelecting && clipboard == null) {
                Box {
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Tạo mới",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Tạo thư mục") },
                            onClick = { showCreateFolderDialog = true; showFabMenu = false },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = Color(0xFFFFA726)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Tạo file") },
                            onClick = { showCreateFileDialog = true; showFabMenu = false },
                            leadingIcon = { Icon(Icons.Default.NoteAdd, null, tint = Color(0xFF42A5F5)) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    ErrorState(
                        message = errorMessage!!,
                        onRetry = { vm.clearError(); vm.loadFiles() }
                    )
                }
                files.isEmpty() -> {
                    EmptyState(isSearching = searchQuery.isNotEmpty())
                }
                else -> {
                    if (viewMode == ViewMode.LIST) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(files, key = { it.path }) { item ->
                                FileListItem(
                                    item = item,
                                    isSelected = item.path in selectedFiles,
                                    onClick = {
                                        if (isSelecting) vm.toggleSelection(item.path)
                                        else if (item.isDirectory) vm.navigateTo(item.path)
                                        else handleMediaOpen(
                                            item, files,
                                            onImage = { list, idx -> imageViewerState = list to idx },
                                            onVideo = { videoViewerFile = it },
                                            onAudio = { list, idx -> audioViewerState = list to idx },
                                            onOther = { openFile(context, it) }
                                        )
                                    },
                                    onLongClick = { vm.toggleSelection(item.path) },
                                    onMenuClick = { showContextMenu = item }
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(files, key = { it.path }) { item ->
                                FileGridItem(
                                    item = item,
                                    isSelected = item.path in selectedFiles,
                                    onClick = {
                                        if (isSelecting) vm.toggleSelection(item.path)
                                        else if (item.isDirectory) vm.navigateTo(item.path)
                                        else handleMediaOpen(
                                            item, files,
                                            onImage = { list, idx -> imageViewerState = list to idx },
                                            onVideo = { videoViewerFile = it },
                                            onAudio = { list, idx -> audioViewerState = list to idx },
                                            onOther = { openFile(context, it) }
                                        )
                                    },
                                    onLongClick = { vm.toggleSelection(item.path) },
                                    onMenuClick = { showContextMenu = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Context Menu ---
    showContextMenu?.let { item ->
        ModalBottomSheet(onDismissRequest = { showContextMenu = null }) {
            ContextMenuContent(
                item = item,
                onOpen = {
                    showContextMenu = null
                    if (item.isDirectory) vm.navigateTo(item.path) else openFile(context, item)
                },
                onRename = { showRenameDialog = item; showContextMenu = null },
                onCopy = { vm.copyFiles(listOf(item)); showContextMenu = null; snackbarMessage = "Đã sao chép" },
                onCut = { vm.cutFiles(listOf(item)); showContextMenu = null; snackbarMessage = "Đã cắt" },
                onDelete = {
                    showContextMenu = null
                    vm.deleteFiles(listOf(item))
                    snackbarMessage = "Đã xóa ${item.name}"
                },
                onShare = { shareFiles(context, listOf(item)); showContextMenu = null }
            )
        }
    }

    // --- Dialogs ---
    if (showSortDialog) {
        SortDialog(current = sortMode, onSelect = { vm.updateSortMode(it); showSortDialog = false })
    }

    if (showCreateFolderDialog) {
        InputDialog(
            title = "Tạo thư mục",
            label = "Tên thư mục",
            icon = Icons.Default.CreateNewFolder,
            onConfirm = { name ->
                vm.createFolder(name) { ok ->
                    snackbarMessage = if (ok) "Tạo thư mục thành công" else "Không thể tạo thư mục"
                }
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    if (showCreateFileDialog) {
        InputDialog(
            title = "Tạo file mới",
            label = "Tên file",
            icon = Icons.Default.NoteAdd,
            onConfirm = { name ->
                vm.createFile(name) { ok ->
                    snackbarMessage = if (ok) "Tạo file thành công" else "Không thể tạo file"
                }
                showCreateFileDialog = false
            },
            onDismiss = { showCreateFileDialog = false }
        )
    }

    showRenameDialog?.let { item ->
        InputDialog(
            title = "Đổi tên",
            label = "Tên mới",
            initialValue = item.name,
            icon = Icons.Default.DriveFileRenameOutline,
            onConfirm = { name ->
                vm.renameFile(item, name) { ok ->
                    snackbarMessage = if (ok) "Đổi tên thành công" else "Không thể đổi tên"
                }
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }

    if (showDeleteDialog) {
        val selectedItems = files.filter { it.path in selectedFiles }
        DeleteConfirmDialog(
            count = selectedItems.size,
            onConfirm = {
                vm.deleteFiles(selectedItems)
                snackbarMessage = "Đã xóa ${selectedItems.size} mục"
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // --- Media Viewers ---
    imageViewerState?.let { (images, idx) ->
        ImageViewerScreen(
            images = images,
            initialIndex = idx,
            onDismiss = { imageViewerState = null }
        )
    }
    videoViewerFile?.let { file ->
        VideoPlayerScreen(
            file = file,
            onDismiss = { videoViewerFile = null }
        )
    }
    audioViewerState?.let { (audios, idx) ->
        AudioPlayerScreen(
            audioFiles = audios,
            initialIndex = idx,
            onDismiss = { audioViewerState = null }
        )
    }

    } // end ModalNavigationDrawer
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    hasClipboard: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onShare) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Chia sẻ", fontSize = 10.sp)
                }
            }
            IconButton(onClick = onCopy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Sao chép", fontSize = 10.sp)
                }
            }
            IconButton(onClick = onCut) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ContentCut, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Cắt", fontSize = 10.sp)
                }
            }
            if (selectedCount == 1) {
                IconButton(onClick = onRename) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DriveFileRenameOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Đổi tên", fontSize = 10.sp)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("Xóa", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PasteBottomBar(isCut: Boolean, count: Int, onPaste: () -> Unit, onClear: () -> Unit) {
    Surface(
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isCut) Icons.Default.ContentCut else Icons.Default.ContentCopy,
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${if (isCut) "Đã cắt" else "Đã sao chép"} $count mục",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TextButton(onClick = onClear) { Text("Hủy") }
            Button(onClick = onPaste) { Text("Dán") }
        }
    }
}

@Composable
private fun ContextMenuContent(
    item: FileItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        // Header
        ListItem(
            headlineContent = {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    if (item.isDirectory) "Thư mục • ${FileManagerViewModel.formatDate(item.lastModified)}"
                    else "${FileManagerViewModel.formatFileSize(item.size)} • ${FileManagerViewModel.formatDate(item.lastModified)}"
                )
            }
        )
        HorizontalDivider()
        MenuRow(Icons.Default.OpenInNew, "Mở", onOpen)
        MenuRow(Icons.Default.DriveFileRenameOutline, "Đổi tên", onRename)
        MenuRow(Icons.Default.ContentCopy, "Sao chép", onCopy)
        MenuRow(Icons.Default.ContentCut, "Cắt", onCut)
        if (!item.isDirectory) MenuRow(Icons.Default.Share, "Chia sẻ", onShare)
        MenuRow(Icons.Default.Delete, "Xóa", onDelete, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = { Icon(icon, null, tint = color) },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun SortDialog(current: SortMode, onSelect: (SortMode) -> Unit) {
    AlertDialog(
        onDismissRequest = { onSelect(current) },
        title = { Text("Sắp xếp theo", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(mode.displayName)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun InputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    icon: ImageVector,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Xác nhận") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold) },
        text = { Text("Bạn có chắc muốn xóa $count mục đã chọn? Hành động này không thể hoàn tác.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Xóa") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun EmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (isSearching) Icons.Default.SearchOff else Icons.Default.FolderOff,
            null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isSearching) "Không tìm thấy kết quả" else "Thư mục trống",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Thử lại") }
    }
}

private fun openFile(context: Context, item: FileItem) {
    try {
        val file = File(item.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mime = context.contentResolver.getType(uri) ?: getMimeType(item.extension)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Mở với..."))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Detect file type and route to appropriate in-app viewer or external app
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "ts", "m4v")
private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "wma", "amr")

private fun FileItem.isImage() = extension.lowercase() in IMAGE_EXTS
private fun FileItem.isVideo() = extension.lowercase() in VIDEO_EXTS
private fun FileItem.isAudio() = extension.lowercase() in AUDIO_EXTS

private fun handleMediaOpen(
    item: FileItem,
    allFiles: List<FileItem>,
    onImage: (List<FileItem>, Int) -> Unit,
    onVideo: (FileItem) -> Unit,
    onAudio: (List<FileItem>, Int) -> Unit,
    onOther: (FileItem) -> Unit
) {
    when {
        item.isImage() -> {
            val images = allFiles.filter { it.isImage() }
            val idx = images.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
            onImage(images, idx)
        }
        item.isVideo() -> onVideo(item)
        item.isAudio() -> {
            val audios = allFiles.filter { it.isAudio() }
            val idx = audios.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
            onAudio(audios, idx)
        }
        else -> onOther(item)
    }
}

private fun shareFiles(context: Context, items: List<FileItem>) {
    try {
        if (items.isEmpty()) return
        if (items.size == 1) {
            val file = File(items[0].path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(items[0].extension)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ"))
        } else {
            val uris = ArrayList(items.map {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", File(it.path))
            })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getMimeType(extension: String): String = when (extension) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "pdf" -> "application/pdf"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "zip" -> "application/zip"
    "apk" -> "application/vnd.android.package-archive"
    "txt" -> "text/plain"
    else -> "*/*"
}










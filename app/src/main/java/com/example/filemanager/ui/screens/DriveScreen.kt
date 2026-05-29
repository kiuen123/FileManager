package com.example.filemanager.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.model.DriveItem
import com.example.filemanager.viewmodel.DriveAuthState
import com.example.filemanager.viewmodel.DriveFolder
import com.example.filemanager.viewmodel.DriveViewModel
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DriveScreen(
    vm: DriveViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val authState by vm.authState.collectAsState()
    val files by vm.files.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()
    val selectedItems by vm.selectedItems.collectAsState()
    val downloadState by vm.downloadState.collectAsState()

    val isSelecting = selectedItems.isNotEmpty()
    var showSearchBar by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DriveItem?>(null) }
    var contextMenuTarget by remember { mutableStateOf<DriveItem?>(null) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(snackMessage) {
        snackMessage?.let { snackbarHost.showSnackbar(it); snackMessage = null }
    }

    // ── Google Sign-In launcher ────────────────────────────────────────────
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)

                // Lấy access token trên background thread
                scope.launch(Dispatchers.IO) {
                    try {
                        val token = GoogleAuthUtil.getToken(
                            context,
                            account.account!!,
                            "oauth2:https://www.googleapis.com/auth/drive " +
                                "https://www.googleapis.com/auth/userinfo.email " +
                                "https://www.googleapis.com/auth/userinfo.profile"
                        )
                        withContext(Dispatchers.Main) { vm.onSignInSuccess(token) }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            snackMessage = "Lỗi lấy token: ${e.message}"
                        }
                    }
                }
            } catch (e: ApiException) {
                snackMessage = "Đăng nhập thất bại (code ${e.statusCode})"
            }
        }
    }

    fun launchSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive"))
            .build()
        signInLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
    }

    // Always intercept back: route to the correct action at each navigation level
    BackHandler {
        when {
            isSelecting  -> vm.clearSelection()
            showSearchBar -> { showSearchBar = false; vm.clearSearch() }
            vm.canGoBack() -> vm.navigateBack()
            else           -> onBack()  // back to FileManagerScreen
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        when {
                            showSearchBar -> TextField(
                                value = searchQuery,
                                onValueChange = { vm.search(it) },
                                placeholder = { Text("Tìm trong Drive...", fontSize = 15.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                )
                            )
                            isSelecting -> Text("${selectedItems.size} đã chọn", fontWeight = FontWeight.SemiBold)
                            else -> Text("Google Drive", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        when {
                            isSelecting -> IconButton(onClick = vm::clearSelection) {
                                Icon(Icons.Default.Close, null)
                            }
                            showSearchBar -> IconButton(onClick = { showSearchBar = false; vm.clearSearch() }) {
                                Icon(Icons.Default.ArrowBack, null)
                            }
                            vm.canGoBack() -> IconButton(onClick = { vm.navigateBack() }) {
                                Icon(Icons.Default.ArrowBack, null)
                            }
                            else -> IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, "Quay lại File Manager")
                            }
                        }
                    },
                    actions = {
                        if (isSelecting) {
                            IconButton(onClick = vm::selectAll) {
                                Icon(Icons.Default.SelectAll, null)
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        } else if (authState is DriveAuthState.SignedIn) {
                            if (!showSearchBar) {
                                IconButton(onClick = { showSearchBar = true }) {
                                    Icon(Icons.Default.Search, null)
                                }
                            }
                            IconButton(onClick = { vm.loadFiles() }) {
                                Icon(Icons.Default.Refresh, null)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A73E8),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )

                // Breadcrumb khi đã đăng nhập
                if (authState is DriveAuthState.SignedIn && !showSearchBar && !isSelecting) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A73E8).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(breadcrumbs) { crumb ->
                            val isLast = crumb == breadcrumbs.last()
                            Text(
                                text = crumb.name,
                                fontSize = 12.sp,
                                color = if (isLast) Color(0xFF1A73E8) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = if (!isLast) Modifier
                                    .padding(horizontal = 2.dp)
                                    .clickable { vm.navigateToBreadcrumb(crumb) }
                                else Modifier.padding(horizontal = 2.dp)
                            )
                            if (!isLast) {
                                Icon(
                                    Icons.Default.ChevronRight, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (authState is DriveAuthState.SignedIn && !isSelecting) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    FloatingActionButton(
                        onClick = { showMenu = !showMenu },
                        containerColor = Color(0xFF1A73E8)
                    ) {
                        Icon(
                            if (showMenu) Icons.Default.Close else Icons.Default.Add,
                            null, tint = Color.White
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Tạo thư mục") },
                            onClick = { showCreateFolderDialog = true; showMenu = false },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = Color(0xFF1A73E8)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = authState) {
                DriveAuthState.NotSignedIn -> DriveSignInContent(onSignIn = { launchSignIn() })
                DriveAuthState.SigningIn -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF1A73E8))
                        Spacer(Modifier.height(16.dp))
                        Text("Đang đăng nhập...")
                    }
                }
                is DriveAuthState.SignedIn -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // User info header
                        DriveUserHeader(state = state, onSignOut = { vm.signOut() })

                        // Content
                        val displayFiles = if (isSearching || searchQuery.isNotEmpty()) searchResults else files
                        when {
                            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFF1A73E8))
                            }
                            error != null -> ErrorContent(error!!, onRetry = { vm.clearError(); vm.loadFiles() })
                            displayFiles.isEmpty() -> EmptyDriveContent(isSearchMode = isSearching || searchQuery.isNotEmpty())
                            else -> {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(displayFiles, key = { it.id }) { item ->
                                        DriveFileItem(
                                            item = item,
                                            isSelected = item.id in selectedItems,
                                            onClick = {
                                                if (isSelecting) vm.toggleSelection(item.id)
                                                else if (item.isFolder) vm.navigateTo(item)
                                                else contextMenuTarget = item
                                            },
                                            onLongClick = { vm.toggleSelection(item.id) },
                                            onMenuClick = { contextMenuTarget = item }
                                        )
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Download progress ──────────────────────────────────────────────────
    downloadState?.let { ds ->
        AlertDialog(
            onDismissRequest = { if (ds.isDone || ds.error != null) vm.clearDownload() },
            icon = { Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF1A73E8)) },
            title = { Text(if (ds.isDone) "Tải xong!" else "Đang tải...") },
            text = {
                Column {
                    Text(ds.item.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (ds.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(ds.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    } else {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { ds.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF1A73E8)
                        )
                        Spacer(Modifier.height(6.dp))
                        if (ds.isDone) Text("Đã lưu: Download/DriveFiles/${ds.item.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text("${(ds.progress * 100).toInt()}%", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                if (ds.isDone || ds.error != null) {
                    Button(
                        onClick = { vm.clearDownload() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                    ) { Text("Đóng") }
                }
            }
        )
    }

    // ── Context menu ──────────────────────────────────────────────────────
    contextMenuTarget?.let { item ->
        ModalBottomSheet(onDismissRequest = { contextMenuTarget = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = {
                        Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        Icon(
                            if (item.isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            null,
                            tint = if (item.isFolder) Color(0xFF1A73E8) else Color.Gray
                        )
                    }
                )
                HorizontalDivider()
                if (!item.isFolder) {
                    ListItem(
                        headlineContent = { Text("Tải xuống") },
                        leadingContent = { Icon(Icons.Default.Download, null, tint = Color(0xFF1A73E8)) },
                        modifier = Modifier.clickable {
                            contextMenuTarget = null
                            vm.downloadFile(item)
                        }
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

    // ── Create Folder dialog ───────────────────────────────────────────────
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            icon = { Icon(Icons.Default.CreateNewFolder, null, tint = Color(0xFF1A73E8)) },
            title = { Text("Tạo thư mục", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Tên thư mục") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.createFolder(folderName) { ok, msg -> snackMessage = msg }
                        showCreateFolderDialog = false
                    },
                    enabled = folderName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                ) { Text("Tạo") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Hủy") } }
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────
    renameTarget?.let { target ->
        var newName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Đổi tên", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Tên mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.renameItem(target, newName) { ok ->
                            snackMessage = if (ok) "Đổi tên thành công" else "Đổi tên thất bại"
                        }
                        renameTarget = null
                    },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                ) { Text("Xác nhận") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Hủy") } }
        )
    }

    // ── Delete confirm ────────────────────────────────────────────────────
    if (showDeleteDialog) {
        val toDelete = files.filter { it.id in selectedItems }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa ${toDelete.size} mục?") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteItems(toDelete) { ok ->
                            snackMessage = if (ok) "Đã xóa ${toDelete.size} mục" else "Xóa thất bại"
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") } }
        )
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun DriveSignInContent(onSignIn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE8F0FE), Color.White))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                        .background(Color(0xFF1A73E8).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Cloud, null, tint = Color(0xFF1A73E8), modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("Google Drive", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Đăng nhập để truy cập file trên Google Drive của bạn",
                    fontSize = 14.sp, color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng nhập với Google", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠ Cần cấu hình OAuth trong Google Cloud Console",
                    fontSize = 11.sp, color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun DriveUserHeader(state: DriveAuthState.SignedIn, onSignOut: () -> Unit) {
    Surface(color = Color(0xFF1A73E8).copy(alpha = 0.08f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color(0xFF1A73E8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (state.displayName.firstOrNull() ?: state.email.firstOrNull() ?: 'G').toString().uppercase(),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (state.displayName.isNotEmpty()) {
                    Text(state.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Text(state.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSignOut) {
                Text("Đăng xuất", color = Color(0xFF1A73E8), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DriveFileItem(
    item: DriveItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF1A73E8).copy(alpha = 0.1f) else Color.Transparent
    val (iconColor, iconVec) = when {
        item.isFolder -> Color(0xFF1A73E8) to Icons.Default.Folder
        item.mimeType.contains("image") -> Color(0xFF43A047) to Icons.Default.Image
        item.mimeType.contains("video") -> Color(0xFFAB47BC) to Icons.Default.PlayCircle
        item.mimeType.contains("audio") -> Color(0xFFEF5350) to Icons.Default.MusicNote
        item.mimeType.contains("pdf") -> Color(0xFFEF5350) to Icons.Default.PictureAsPdf
        item.isGoogleDoc -> when (item.mimeType) {
            "application/vnd.google-apps.document" -> Color(0xFF1E88E5) to Icons.Default.Description
            "application/vnd.google-apps.spreadsheet" -> Color(0xFF43A047) to Icons.Default.TableChart
            "application/vnd.google-apps.presentation" -> Color(0xFFFF7043) to Icons.Default.Slideshow
            else -> Color(0xFF1A73E8) to Icons.Default.CloudDone
        }
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
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1A73E8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
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
                if (item.modifiedTime.isNotEmpty()) {
                    Text(
                        item.modifiedTime.take(10).replace("-", "/"),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!item.isFolder && item.size > 0) {
                    Text(
                        "  •  ${formatSize(item.size)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.isGoogleDoc) {
                    Text(
                        "  •  Google ${item.mimeType.substringAfterLast(".")}",
                        fontSize = 12.sp, color = Color(0xFF1A73E8)
                    )
                }
            }
        }
        IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyDriveContent(isSearchMode: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (isSearchMode) Icons.Default.SearchOff else Icons.Default.CloudQueue,
            null, modifier = Modifier.size(72.dp), tint = Color(0xFF1A73E8).copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isSearchMode) "Không tìm thấy kết quả" else "Thư mục trống",
            fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))) {
            Text("Thử lại")
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val idx = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, idx.toDouble()), units[idx])
}






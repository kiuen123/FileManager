package com.example.filemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.model.NetworkConnection
import com.example.filemanager.model.NetworkProtocol
import com.example.filemanager.network.SmbStorageClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConnectionDialog(
    initial: NetworkConnection? = null,
    onSave: (NetworkConnection) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = initial != null
    var displayName by remember { mutableStateOf(initial?.displayName ?: "") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: NetworkProtocol.FTP) }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: protocol.defaultPort).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var initialPath by remember { mutableStateOf(initial?.initialPath ?: "/") }
    var shareName by remember { mutableStateOf(initial?.shareName ?: "") }
    var domain by remember { mutableStateOf(initial?.domain ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var showProtocolMenu by remember { mutableStateOf(false) }

    // Share browsing state (SMB only)
    val scope = rememberCoroutineScope()
    var isBrowsingShares by remember { mutableStateOf(false) }
    var foundShares by remember { mutableStateOf<List<String>>(emptyList()) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var showSharePicker by remember { mutableStateOf(false) }

    val isSmb = protocol == NetworkProtocol.SMB

    LaunchedEffect(protocol) {
        port = protocol.defaultPort.toString()
        browseError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Storage, null, tint = NetworkColor) },
        title = {
            Text(if (isEdit) "Chỉnh sửa kết nối" else "Thêm kết nối mới", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it },
                    label = { Text("Tên hiển thị *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Label, null) })

                Box {
                    OutlinedTextField(value = protocol.displayName, onValueChange = {},
                        label = { Text("Giao thức *") }, readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Wifi, null) },
                        trailingIcon = {
                            IconButton(onClick = { showProtocolMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        })
                    DropdownMenu(expanded = showProtocolMenu, onDismissRequest = { showProtocolMenu = false }) {
                        NetworkProtocol.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.displayName) },
                                onClick = { protocol = p; showProtocolMenu = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = host, onValueChange = { host = it; browseError = null },
                    label = { Text("Địa chỉ IP / Hostname *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Dns, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))

                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Cổng") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Router, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                if (isSmb) {
                    // ── Share name + Browse button ────────────────────────
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top) {
                        OutlinedTextField(
                            value = shareName,
                            onValueChange = { shareName = it },
                            label = { Text("Tên Share *") },
                            placeholder = { Text("VD: Public, Users, D", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Default.Folder, null) },
                            isError = browseError != null,
                            supportingText = browseError?.let { err ->
                                { Text(err, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                            })
                        FilledTonalButton(
                            onClick = {
                                browseError = null
                                isBrowsingShares = true
                                scope.launch {
                                    SmbStorageClient.browseShares(
                                        host     = host.trim(),
                                        username = username.trim(),
                                        password = password,
                                        domain   = domain.trim().ifBlank { "WORKGROUP" }
                                    ).onSuccess { shares ->
                                        foundShares = shares
                                        if (shares.isNotEmpty()) showSharePicker = true
                                        else browseError = "Không tìm thấy share nào. Nhập tên thủ công."
                                    }.onFailure { err ->
                                        browseError = err.message ?: "Kết nối thất bại"
                                    }
                                    isBrowsingShares = false
                                }
                            },
                            enabled = host.isNotBlank() && !isBrowsingShares,
                            modifier = Modifier.height(56.dp).padding(top = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = NetworkColor.copy(alpha = 0.12f))) {
                            if (isBrowsingShares) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                    color = NetworkColor, strokeWidth = 2.dp)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ManageSearch, null,
                                        modifier = Modifier.size(18.dp), tint = NetworkColor)
                                    Text("Tìm", fontSize = 10.sp, color = NetworkColor)
                                }
                            }
                        }
                    }

                    OutlinedTextField(value = domain, onValueChange = { domain = it },
                        label = { Text("Domain (để trống với Win10 thường)") },
                        placeholder = { Text("Để trống hoặc nhập tên máy tính", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Business, null) })

                    // Tips card for Windows sharing
                    Card(colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp),
                               verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("💡 Hướng dẫn chia sẻ trên Windows:",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("1. Chuột phải folder → Properties → Sharing → Share",
                                fontSize = 11.sp)
                            Text("2. Tên Share = tên folder hoặc tên bạn đặt",
                                fontSize = 11.sp)
                            Text("3. Bật File & Printer Sharing trong Windows Firewall",
                                fontSize = 11.sp)
                            Text("4. Dùng tên đăng nhập Windows (không phải Microsoft account)",
                                fontSize = 11.sp)
                        }
                    }
                } else {
                    OutlinedTextField(value = initialPath, onValueChange = { initialPath = it },
                        label = { Text("Đường dẫn gốc (mặc định: /)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
                }

                OutlinedTextField(value = username, onValueChange = { username = it },
                    label = { Text("Tên đăng nhập") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    placeholder = { Text("Để trống nếu ẩn danh") })

                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Mật khẩu") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    })
            }
        },
        confirmButton = {
            Button(onClick = {
                val conn = (initial ?: NetworkConnection()).copy(
                    displayName = displayName.trim(), protocol = protocol,
                    host = host.trim(), port = port.toIntOrNull() ?: protocol.defaultPort,
                    username = username.trim(), password = password,
                    initialPath = initialPath.trim().ifBlank { "/" },
                    shareName = shareName.trim(),
                    domain = domain.trim().ifBlank { "WORKGROUP" })
                onSave(conn)
            }, enabled = displayName.isNotBlank() && host.isNotBlank() && (!isSmb || shareName.isNotBlank()),
               colors = ButtonDefaults.buttonColors(containerColor = NetworkColor)) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )

    // ── Share picker dialog ───────────────────────────────────────────────
    if (showSharePicker && foundShares.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showSharePicker = false },
            icon = { Icon(Icons.Default.FolderOpen, null, tint = NetworkColor) },
            title = { Text("Chọn share", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Tìm thấy ${foundShares.size} share trên ${host.trim()}:",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    foundShares.forEach { name ->
                        ListItem(
                            headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                            leadingContent = { Icon(Icons.Default.Folder, null, tint = Color(0xFF6D4C41)) },
                            modifier = Modifier.clickable { shareName = name; showSharePicker = false })
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSharePicker = false }) { Text("Đóng") } }
        )
    }
}


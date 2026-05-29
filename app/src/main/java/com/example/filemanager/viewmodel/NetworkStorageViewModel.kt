package com.example.filemanager.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanager.data.NetworkConnectionRepository
import com.example.filemanager.model.DiscoveredServer
import com.example.filemanager.model.NetworkConnection
import com.example.filemanager.model.NetworkItem
import com.example.filemanager.network.NetworkClientFactory
import com.example.filemanager.network.NetworkDiscoveryService
import com.example.filemanager.network.NetworkStorageClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

// ── Discovery state ────────────────────────────────────────────────────────
sealed class NetworkDiscoveryState {
    object Idle    : NetworkDiscoveryState()
    data class Scanning(val progress: Int = 0, val total: Int = 254) : NetworkDiscoveryState()
    object Done    : NetworkDiscoveryState()
    data class Error(val message: String) : NetworkDiscoveryState()
}

data class NetworkTransferState(
    val fileName: String,
    val progress: Float = 0f,
    val isDone: Boolean = false,
    val isUpload: Boolean = false,
    val error: String? = null,
    val savedPath: String = ""
)

class NetworkStorageViewModel(application: Application) : AndroidViewModel(application) {

    val repository = NetworkConnectionRepository(application)
    private val discoveryService = NetworkDiscoveryService(application)

    private val _connections = MutableStateFlow<List<NetworkConnection>>(emptyList())
    val connections = _connections.asStateFlow()

    private val _activeConnection = MutableStateFlow<NetworkConnection?>(null)
    val activeConnection = _activeConnection.asStateFlow()

    private val _files = MutableStateFlow<List<NetworkItem>>(emptyList())
    val files = _files.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath = _currentPath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _transfer = MutableStateFlow<NetworkTransferState?>(null)
    val transfer = _transfer.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems = _selectedItems.asStateFlow()

    private var client: NetworkStorageClient? = null
    private val navStack = ArrayDeque<String>()

    // ── Discovery ─────────────────────────────────────────────────────────
    private val _discoveryState = MutableStateFlow<NetworkDiscoveryState>(NetworkDiscoveryState.Idle)
    val discoveryState = _discoveryState.asStateFlow()

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers = _discoveredServers.asStateFlow()

    private var discoveryJob: Job? = null
    private var nsdStopFn: (() -> Unit)? = null

    val breadcrumbs: StateFlow<List<Pair<String, String>>> = _currentPath.map { path ->
        val parts = path.split("/").filter { it.isNotEmpty() }
        val crumbs = mutableListOf("Gốc" to "/")
        var buildPath = ""
        for (part in parts) {
            buildPath += "/$part"
            crumbs.add(part to buildPath)
        }
        crumbs
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), listOf("Gốc" to "/"))

    init { loadConnections() }

    fun loadConnections() {
        _connections.value = repository.getAll()
    }

    fun saveConnection(connection: NetworkConnection) {
        repository.save(connection)
        loadConnections()
    }

    fun deleteConnection(id: String) {
        if (_activeConnection.value?.id == id) disconnectFromServer()
        repository.delete(id)
        loadConnections()
    }

    // ── Connect/Disconnect ────────────────────────────────────────────────

    fun connectTo(connection: NetworkConnection) {
        viewModelScope.launch {
            _isConnecting.value = true
            _error.value = null

            // Disconnect previous if any
            client?.disconnect()
            client = null

            val newClient = NetworkClientFactory.create(connection)
            newClient.connect().onSuccess {
                client = newClient
                _activeConnection.value = connection
                val rootPath = when {
                    connection.protocol.name == "SMB" -> "/"
                    connection.initialPath.isNotBlank() -> connection.initialPath
                    else -> "/"
                }
                navStack.clear()
                _currentPath.value = rootPath
                loadFiles(rootPath)
            }.onFailure {
                _error.value = it.message
            }
            _isConnecting.value = false
        }
    }

    fun disconnectFromServer() {
        viewModelScope.launch {
            client?.disconnect()
            client = null
            _activeConnection.value = null
            _files.value = emptyList()
            navStack.clear()
            _currentPath.value = "/"
            _selectedItems.value = emptySet()
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun loadFiles(path: String = _currentPath.value) {
        val c = client ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            c.listFiles(path).onSuccess { items ->
                _files.value = items
                _currentPath.value = path
            }.onFailure {
                _error.value = it.message
            }
            _isLoading.value = false
        }
    }

    fun navigateTo(item: NetworkItem) {
        if (!item.isDirectory) return
        navStack.addLast(_currentPath.value)
        _selectedItems.value = emptySet()
        loadFiles(item.path)
    }

    fun navigateBack(): Boolean {
        if (navStack.isEmpty()) return false
        val prev = navStack.removeLast()
        _selectedItems.value = emptySet()
        loadFiles(prev)
        return true
    }

    fun navigateToBreadcrumb(path: String) {
        while (navStack.isNotEmpty() && navStack.last() != path) navStack.removeLast()
        if (navStack.isNotEmpty()) navStack.removeLast()
        _selectedItems.value = emptySet()
        loadFiles(path)
    }

    fun canGoBack() = navStack.isNotEmpty()

    // ── File operations ───────────────────────────────────────────────────

    fun downloadFile(item: NetworkItem) {
        val c = client ?: return
        val destDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "NetworkFiles"
        ).also { it.mkdirs() }
        val destFile = File(destDir, item.name)
        _transfer.value = NetworkTransferState(fileName = item.name, isUpload = false)

        viewModelScope.launch {
            c.downloadFile(item.path, destFile) { progress ->
                _transfer.value = _transfer.value?.copy(progress = progress)
            }.onSuccess { file ->
                _transfer.value = _transfer.value?.copy(isDone = true, progress = 1f, savedPath = file.absolutePath)
            }.onFailure {
                _transfer.value = _transfer.value?.copy(error = it.message)
            }
        }
    }

    /** Tải file về thư mục cache tạm rồi gọi [onReady] với đường dẫn local.
     *  Dùng cho xem ảnh/video/âm thanh trực tiếp mà không lưu vào Downloads. */
    fun downloadToCache(item: NetworkItem, onReady: (String?) -> Unit) {
        val c = client ?: run { onReady(null); return }
        val cacheDir = getApplication<Application>().cacheDir
        val destDir = File(cacheDir, "media_preview").also { it.mkdirs() }
        val destFile = File(destDir, item.name)

        // Nếu đã cache sẵn thì dùng luôn
        if (destFile.exists() && destFile.length() > 0) {
            onReady(destFile.absolutePath)
            return
        }

        _transfer.value = NetworkTransferState(fileName = item.name, isUpload = false)
        viewModelScope.launch {
            c.downloadFile(item.path, destFile) { progress ->
                _transfer.value = _transfer.value?.copy(progress = progress)
            }.onSuccess { file ->
                _transfer.value = _transfer.value?.copy(isDone = true, progress = 1f)
                onReady(file.absolutePath)
            }.onFailure {
                _transfer.value = _transfer.value?.copy(error = it.message)
                onReady(null)
            }
        }
    }

    fun uploadFile(localPath: String, onResult: (Boolean, String) -> Unit) {
        val c = client ?: return
        val localFile = File(localPath)
        if (!localFile.exists()) { onResult(false, "File không tồn tại"); return }
        val remotePath = "${_currentPath.value}/${localFile.name}"
        _transfer.value = NetworkTransferState(fileName = localFile.name, isUpload = true)

        viewModelScope.launch {
            c.uploadFile(localFile, remotePath) { progress ->
                _transfer.value = _transfer.value?.copy(progress = progress)
            }.onSuccess {
                _transfer.value = _transfer.value?.copy(isDone = true, progress = 1f)
                onResult(true, "Đã tải lên thành công")
                loadFiles()
            }.onFailure {
                _transfer.value = _transfer.value?.copy(error = it.message)
                onResult(false, "Lỗi: ${it.message}")
            }
        }
    }

    fun deleteItems(items: List<NetworkItem>, onResult: (Boolean) -> Unit) {
        val c = client ?: return
        viewModelScope.launch {
            var allOk = true
            items.forEach { item ->
                c.deleteFile(item.path).onFailure { allOk = false }
            }
            onResult(allOk)
            clearSelection()
            loadFiles()
        }
    }

    fun createFolder(name: String, onResult: (Boolean) -> Unit) {
        val c = client ?: return
        viewModelScope.launch {
            val path = "${_currentPath.value}/$name"
            c.createFolder(path).onSuccess { onResult(true); loadFiles() }
                .onFailure { onResult(false) }
        }
    }

    fun rename(item: NetworkItem, newName: String, onResult: (Boolean) -> Unit) {
        val c = client ?: return
        viewModelScope.launch {
            c.rename(item.path, newName).onSuccess { onResult(true); loadFiles() }
                .onFailure { onResult(false) }
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    fun toggleSelection(path: String) {
        _selectedItems.update { if (path in it) it - path else it + path }
    }
    fun selectAll() { _selectedItems.value = _files.value.map { it.path }.toSet() }
    fun clearSelection() { _selectedItems.value = emptySet() }
    fun clearTransfer() { _transfer.value = null }
    fun clearError() { _error.value = null }

    /** Trả về kích thước cache media (bytes). */
    fun getMediaCacheSize(): Long {
        val dir = File(getApplication<Application>().cacheDir, "media_preview")
        return if (dir.exists()) dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    /** Xóa toàn bộ cache media tạm, trả về số bytes đã giải phóng qua [onDone]. */
    fun clearMediaCache(onDone: (freedBytes: Long) -> Unit) {
        viewModelScope.launch {
            val dir = File(getApplication<Application>().cacheDir, "media_preview")
            val freed = if (dir.exists()) dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else 0L
            dir.deleteRecursively()
            onDone(freed)
        }
    }

    // ── Network discovery ─────────────────────────────────────────────────

    fun startDiscovery() {
        val subnet = discoveryService.getLocalSubnet()
        if (subnet == null) {
            _discoveryState.value = NetworkDiscoveryState.Error(
                "Không tìm thấy mạng WiFi.\nVui lòng kết nối WiFi và thử lại."
            )
            return
        }

        // Cancel any running discovery
        discoveryJob?.cancel()
        nsdStopFn?.invoke()

        _discoveredServers.value = emptyList()
        _discoveryState.value = NetworkDiscoveryState.Scanning(0, 254)

        // Start mDNS discovery (quick, device-advertised)
        nsdStopFn = discoveryService.startNsdDiscovery { server ->
            _discoveredServers.update { list ->
                if (list.none { it.host == server.host && it.port == server.port }) list + server
                else list
            }
        }

        // Start subnet scan (comprehensive)
        discoveryJob = viewModelScope.launch {
            discoveryService.scanSubnet(
                onFound = { server ->
                    _discoveredServers.update { list ->
                        if (list.none { it.host == server.host && it.port == server.port }) list + server
                        else list
                    }
                },
                onProgress = { done, total ->
                    _discoveryState.value = NetworkDiscoveryState.Scanning(done, total)
                }
            )
            _discoveryState.value = NetworkDiscoveryState.Done
            nsdStopFn?.invoke()
            nsdStopFn = null
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        nsdStopFn?.invoke()
        nsdStopFn = null
        _discoveryState.value = NetworkDiscoveryState.Idle
    }

    fun resetDiscovery() {
        stopDiscovery()
        _discoveredServers.value = emptyList()
    }

    /** Pre-fills AddConnectionDialog with data from a discovered server. */
    fun buildConnectionFromDiscovery(server: DiscoveredServer): NetworkConnection =
        NetworkConnection(
            displayName = if (server.name == server.host) "" else server.name,
            protocol    = server.protocol,
            host        = server.host,
            port        = server.port
        )
}


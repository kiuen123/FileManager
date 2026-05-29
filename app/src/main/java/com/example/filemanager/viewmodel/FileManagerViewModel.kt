package com.example.filemanager.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.SortMode
import com.example.filemanager.model.StorageInfo
import com.example.filemanager.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class ClipboardData(val files: List<FileItem>, val isCut: Boolean)

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val rootPath = "/storage/emulated/0"

    private val _currentPath = MutableStateFlow(rootPath)
    val currentPath = _currentPath.asStateFlow()

    private val _rawFiles = MutableStateFlow<List<FileItem>>(emptyList())

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles = _selectedFiles.asStateFlow()

    private val _clipboardData = MutableStateFlow<ClipboardData?>(null)
    val clipboardData = _clipboardData.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.NAME_ASC)
    val sortMode = _sortMode.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode = _viewMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _showHidden = MutableStateFlow(false)
    val showHidden = _showHidden.asStateFlow()

    private val _storageList = MutableStateFlow<List<StorageInfo>>(emptyList())
    val storageList = _storageList.asStateFlow()

    private val navigationStack = ArrayDeque<String>()

    val displayFiles: StateFlow<List<FileItem>> = combine(
        _rawFiles, _searchQuery, _sortMode, _showHidden
    ) { files, query, sort, showHidden ->
        files
            .filter { if (!showHidden) !it.name.startsWith(".") else true }
            .filter { if (query.isBlank()) true else it.name.contains(query, ignoreCase = true) }
            .sortedWith(sort.comparator)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadFiles(rootPath)
        refreshStorageInfo()
    }

    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = buildStorageList()
            withContext(Dispatchers.Main) {
                _storageList.value = list
            }
        }
    }

    private fun buildStorageList(): List<StorageInfo> {
        val result = mutableListOf<StorageInfo>()
        val context = getApplication<Application>()

        // ── Primary: use StorageManager.getStorageVolumes() (API 30+, minSdk=35) ──
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            for (volume in sm.storageVolumes) {
                // Only include volumes that are currently mounted
                if (volume.state != Environment.MEDIA_MOUNTED &&
                    volume.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue

                val dir = volume.directory ?: continue
                if (!dir.exists()) continue

                val displayName = when {
                    volume.isPrimary      -> "Bộ nhớ trong"
                    else                  -> volume.getDescription(context) ?: "Ổ đĩa ngoài"
                }

                val (total, used) = try {
                    val stat = StatFs(dir.absolutePath)
                    val t = stat.blockSizeLong * stat.blockCountLong
                    val f = stat.blockSizeLong * stat.availableBlocksLong
                    t to (t - f)
                } catch (_: Exception) { 0L to 0L }

                result.add(
                    StorageInfo(
                        name       = displayName,
                        path       = dir.absolutePath,
                        totalBytes = total,
                        usedBytes  = used,
                        isSdCard   = volume.isRemovable
                    )
                )
            }
        } catch (_: Exception) { /* fall through to legacy method */ }

        // ── Fallback if StorageManager returned nothing ────────────────────
        if (result.isEmpty()) {
            try {
                val internalPath = Environment.getExternalStorageDirectory().absolutePath
                val stat = StatFs(internalPath)
                val total = stat.blockSizeLong * stat.blockCountLong
                val free  = stat.blockSizeLong * stat.availableBlocksLong
                result.add(StorageInfo("Bộ nhớ trong", internalPath, total, total - free, isSdCard = false))
            } catch (_: Exception) {}

            try {
                val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
                val internalPath = Environment.getExternalStorageDirectory().absolutePath
                for (i in 1 until externalDirs.size) {
                    val dir  = externalDirs[i] ?: continue
                    val path = dir.absolutePath.substringBefore("/Android/data")
                    if (path != internalPath && File(path).exists()) {
                        val stat  = StatFs(path)
                        val total = stat.blockSizeLong * stat.blockCountLong
                        val free  = stat.blockSizeLong * stat.availableBlocksLong
                        result.add(StorageInfo("Thẻ nhớ SD", path, total, total - free, isSdCard = true))
                    }
                }
            } catch (_: Exception) {}
        }

        return result
    }

    fun loadFiles(path: String = _currentPath.value) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val (files, error) = withContext(Dispatchers.IO) {
                    val dir = File(path)
                    when {
                        !dir.exists() ->
                            emptyList<FileItem>() to "Thư mục không tồn tại:\n$path"
                        !dir.isDirectory ->
                            emptyList<FileItem>() to "Đây không phải thư mục"
                        else -> {
                            val fileList = dir.listFiles()
                            if (fileList == null) {
                                // listFiles() returns null when permission denied or I/O error
                                emptyList<FileItem>() to buildString {
                                    appendLine("Không thể đọc ổ đĩa này.")
                                    appendLine("Kiểm tra:")
                                    appendLine("• Đã cấp quyền 'Quản lý tất cả file' trong Cài đặt chưa?")
                                    append("• Ổ đĩa ngoài đã được nhận diện chưa?")
                                }
                            } else {
                                fileList.map { file ->
                                    FileItem(
                                        name = file.name,
                                        path = file.absolutePath,
                                        isDirectory = file.isDirectory,
                                        size = if (file.isFile) file.length() else 0L,
                                        lastModified = file.lastModified(),
                                        extension = file.extension.lowercase()
                                    )
                                } to null
                            }
                        }
                    }
                }
                if (error != null) {
                    _errorMessage.value = error
                } else {
                    _rawFiles.value = files
                    _currentPath.value = path
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi truy cập: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun navigateTo(path: String) {
        navigationStack.addLast(_currentPath.value)
        _selectedFiles.value = emptySet()
        _searchQuery.value = ""
        loadFiles(path)
    }

    fun navigateBack(): Boolean {
        return if (navigationStack.isNotEmpty()) {
            val prev = navigationStack.removeLast()
            _selectedFiles.value = emptySet()
            _searchQuery.value = ""
            loadFiles(prev)
            true
        } else false
    }

    fun canGoBack() = navigationStack.isNotEmpty()

    fun navigateToRoot() {
        navigationStack.clear()
        _selectedFiles.value = emptySet()
        _searchQuery.value = ""
        loadFiles(rootPath)
    }

    fun toggleSelection(path: String) {
        _selectedFiles.update { current ->
            if (path in current) current - path else current + path
        }
    }

    fun selectAll() {
        _selectedFiles.value = _rawFiles.value.map { it.path }.toSet()
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun isInSelectionMode() = _selectedFiles.value.isNotEmpty()

    fun deleteFiles(files: List<FileItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            files.forEach { file ->
                try { File(file.path).deleteRecursively() } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                clearSelection()
                loadFiles()
                refreshStorageInfo()
            }
        }
    }

    fun renameFile(file: FileItem, newName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(file.path)
            val newFile = File(oldFile.parent ?: return@launch, newName.trim())
            val success = oldFile.renameTo(newFile)
            withContext(Dispatchers.Main) {
                onResult(success)
                if (success) loadFiles()
            }
        }
    }

    fun createFolder(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = File(_currentPath.value, name.trim())
            val success = folder.mkdirs()
            withContext(Dispatchers.Main) {
                onResult(success)
                if (success) loadFiles()
            }
        }
    }

    fun createFile(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(_currentPath.value, name.trim())
            val success = try { file.createNewFile() } catch (_: Exception) { false }
            withContext(Dispatchers.Main) {
                onResult(success)
                if (success) loadFiles()
            }
        }
    }

    fun copyFiles(files: List<FileItem>) {
        _clipboardData.value = ClipboardData(files, isCut = false)
        clearSelection()
    }

    fun cutFiles(files: List<FileItem>) {
        _clipboardData.value = ClipboardData(files, isCut = true)
        clearSelection()
    }

    fun pasteFiles(onResult: (Boolean, String) -> Unit) {
        val clipboard = _clipboardData.value ?: run {
            onResult(false, "Không có gì để dán")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            var success = true
            var message = "Dán thành công"
            clipboard.files.forEach { item ->
                try {
                    val src = File(item.path)
                    val dst = File(_currentPath.value, src.name)
                    if (clipboard.isCut) {
                        if (!src.renameTo(dst)) {
                            src.copyRecursively(dst, overwrite = true)
                            src.deleteRecursively()
                        }
                    } else {
                        src.copyRecursively(dst, overwrite = true)
                    }
                } catch (e: Exception) {
                    success = false
                    message = "Lỗi: ${e.message}"
                }
            }
            if (clipboard.isCut && success) _clipboardData.value = null
            withContext(Dispatchers.Main) {
                onResult(success, message)
                loadFiles()
            }
        }
    }

    fun clearClipboard() { _clipboardData.value = null }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateSortMode(mode: SortMode) { _sortMode.value = mode }
    fun toggleViewMode() { _viewMode.update { if (it == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST } }
    fun toggleShowHidden() { _showHidden.update { !it } }
    fun clearError() { _errorMessage.value = null }

    fun getBreadcrumbs(): List<Pair<String, String>> {
        val current = _currentPath.value
        val internalRoot = "/storage/emulated/0"
        val sdRoot = _storageList.value.firstOrNull { it.isSdCard }?.path

        val (rootLabel, rootPath) = when {
            sdRoot != null && current.startsWith(sdRoot) -> "Thẻ nhớ SD" to sdRoot
            else -> "Bộ nhớ trong" to internalRoot
        }
        if (current == rootPath) return listOf(rootLabel to rootPath)

        val relative = current.removePrefix(rootPath)
        val parts = relative.split("/").filter { it.isNotEmpty() }
        val crumbs = mutableListOf(rootLabel to rootPath)
        var buildPath = rootPath
        for (part in parts) {
            buildPath += "/$part"
            crumbs.add(part to buildPath)
        }
        return crumbs
    }

    companion object {
        fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
                .coerceIn(0, units.size - 1)
            return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

        fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}



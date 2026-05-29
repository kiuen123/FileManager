package com.example.filemanager.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanager.model.DriveItem
import com.example.filemanager.network.DriveApiClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class DriveFolder(val id: String, val name: String)

sealed class DriveAuthState {
    object NotSignedIn : DriveAuthState()
    object SigningIn : DriveAuthState()
    data class SignedIn(val email: String, val displayName: String, val accessToken: String) : DriveAuthState()
}

data class DownloadState(
    val item: DriveItem,
    val progress: Float = 0f,
    val isDone: Boolean = false,
    val error: String? = null,
    val savedPath: String = ""
)

class DriveViewModel(application: Application) : AndroidViewModel(application) {

    private val api = DriveApiClient()

    private val _authState = MutableStateFlow<DriveAuthState>(DriveAuthState.NotSignedIn)
    val authState = _authState.asStateFlow()

    private val _files = MutableStateFlow<List<DriveItem>>(emptyList())
    val files = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DriveItem>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState = _downloadState.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems = _selectedItems.asStateFlow()

    // Navigation stack: list of (folderId, folderName)
    private val folderStack = ArrayDeque<DriveFolder>()
    private val _currentFolder = MutableStateFlow(DriveFolder("root", "My Drive"))
    val currentFolder = _currentFolder.asStateFlow()

    val breadcrumbs: StateFlow<List<DriveFolder>> = _currentFolder.map { current ->
        val list = mutableListOf(DriveFolder("root", "My Drive"))
        list.addAll(folderStack.drop(1)) // skip initial "root"
        if (current.id != "root") list.add(current)
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), listOf(DriveFolder("root", "My Drive")))

    // ── Auth ──────────────────────────────────────────────────────────────

    fun onSignInSuccess(accessToken: String) {
        _authState.value = DriveAuthState.SigningIn
        viewModelScope.launch {
            val result = api.getUserInfo(accessToken)
            result.onSuccess { (name, email) ->
                _authState.value = DriveAuthState.SignedIn(email, name, accessToken)
                loadFiles()
            }.onFailure {
                _authState.value = DriveAuthState.SignedIn("", "", accessToken)
                loadFiles()
            }
        }
    }

    fun signOut() {
        _authState.value = DriveAuthState.NotSignedIn
        _files.value = emptyList()
        _selectedItems.value = emptySet()
        folderStack.clear()
        _currentFolder.value = DriveFolder("root", "My Drive")
    }

    private fun getToken(): String? =
        (_authState.value as? DriveAuthState.SignedIn)?.accessToken

    // ── File listing ──────────────────────────────────────────────────────

    fun loadFiles(folderId: String = _currentFolder.value.id) {
        val token = getToken() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            api.listFiles(token, folderId).onSuccess { (items, _) ->
                _files.value = items
            }.onFailure {
                _error.value = "Không tải được file: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    fun navigateTo(folder: DriveItem) {
        if (!folder.isFolder) return
        folderStack.addLast(_currentFolder.value)
        _currentFolder.value = DriveFolder(folder.id, folder.name)
        _selectedItems.value = emptySet()
        loadFiles(folder.id)
    }

    fun navigateBack(): Boolean {
        if (folderStack.isEmpty()) return false
        val prev = folderStack.removeLast()
        _currentFolder.value = prev
        _selectedItems.value = emptySet()
        loadFiles(prev.id)
        return true
    }

    fun canGoBack() = folderStack.isNotEmpty()

    fun navigateToRoot() {
        folderStack.clear()
        _currentFolder.value = DriveFolder("root", "My Drive")
        _selectedItems.value = emptySet()
        loadFiles("root")
    }

    fun navigateToBreadcrumb(folder: DriveFolder) {
        while (folderStack.isNotEmpty() && folderStack.last().id != folder.id) {
            folderStack.removeLast()
        }
        if (folderStack.isNotEmpty()) folderStack.removeLast()
        _currentFolder.value = folder
        _selectedItems.value = emptySet()
        loadFiles(folder.id)
    }

    // ── Search ────────────────────────────────────────────────────────────

    fun search(query: String) {
        val token = getToken() ?: return
        _searchQuery.value = query
        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        viewModelScope.launch {
            api.searchFiles(token, query).onSuccess {
                _searchResults.value = it
            }.onFailure {
                _error.value = "Lỗi tìm kiếm: ${it.message}"
            }
        }
    }

    fun clearSearch() {
        _isSearching.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    // ── Download ──────────────────────────────────────────────────────────

    fun downloadFile(item: DriveItem) {
        val token = getToken() ?: return
        val destDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "DriveFiles"
        ).also { it.mkdirs() }

        _downloadState.value = DownloadState(item = item, progress = 0f)
        viewModelScope.launch {
            api.downloadFile(token, item, destDir) { progress ->
                _downloadState.value = _downloadState.value?.copy(progress = progress)
            }.onSuccess { file ->
                _downloadState.value = _downloadState.value?.copy(
                    isDone = true, progress = 1f, savedPath = file.absolutePath
                )
            }.onFailure {
                _downloadState.value = _downloadState.value?.copy(error = it.message)
            }
        }
    }

    fun clearDownload() { _downloadState.value = null }

    // ── Upload ────────────────────────────────────────────────────────────

    fun uploadFile(localPath: String, onResult: (Boolean, String) -> Unit) {
        val token = getToken() ?: return
        val localFile = File(localPath)
        if (!localFile.exists()) { onResult(false, "File không tồn tại"); return }

        viewModelScope.launch {
            api.uploadFile(token, localFile, _currentFolder.value.id) { }.onSuccess {
                onResult(true, "Đã tải lên: ${it.name}")
                loadFiles()
            }.onFailure {
                onResult(false, "Lỗi tải lên: ${it.message}")
            }
        }
    }

    // ── Create folder ─────────────────────────────────────────────────────

    fun createFolder(name: String, onResult: (Boolean, String) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            api.createFolder(token, name, _currentFolder.value.id).onSuccess {
                onResult(true, "Đã tạo thư mục: ${it.name}")
                loadFiles()
            }.onFailure {
                onResult(false, "Lỗi: ${it.message}")
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────

    fun deleteItems(items: List<DriveItem>, onResult: (Boolean) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            var allOk = true
            items.forEach { item ->
                api.deleteFile(token, item.id).onFailure { allOk = false }
            }
            onResult(allOk)
            clearSelection()
            loadFiles()
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────

    fun renameItem(item: DriveItem, newName: String, onResult: (Boolean) -> Unit) {
        val token = getToken() ?: return
        viewModelScope.launch {
            api.renameFile(token, item.id, newName).onSuccess {
                onResult(true)
                loadFiles()
            }.onFailure {
                onResult(false)
            }
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────

    fun toggleSelection(id: String) {
        _selectedItems.update { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        _selectedItems.value = _files.value.map { it.id }.toSet()
    }

    fun clearSelection() { _selectedItems.value = emptySet() }
    fun clearError() { _error.value = null }
}


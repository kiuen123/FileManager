package com.example.filemanager.network

import com.example.filemanager.model.NetworkItem
import java.io.File

interface NetworkStorageClient {
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun listFiles(path: String): Result<List<NetworkItem>>
    suspend fun downloadFile(remotePath: String, localFile: File, onProgress: (Float) -> Unit): Result<File>
    suspend fun uploadFile(localFile: File, remotePath: String, onProgress: (Float) -> Unit): Result<Unit>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun createFolder(path: String): Result<Unit>
    suspend fun rename(oldPath: String, newName: String): Result<Unit>
    fun isConnected(): Boolean
}


package com.example.filemanager.network

import com.example.filemanager.model.NetworkConnection
import com.example.filemanager.model.NetworkItem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SftpStorageClient(private val conn: NetworkConnection) : NetworkStorageClient {

    private var session: Session? = null
    private var channel: ChannelSftp? = null

    override fun isConnected() = session?.isConnected == true && channel?.isConnected == true

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            val sess = jsch.getSession(
                conn.username.ifBlank { "anonymous" },
                conn.host,
                conn.port
            )
            sess.setPassword(conn.password)
            sess.setConfig("StrictHostKeyChecking", "no")
            sess.setConfig("PreferredAuthentications", "password")
            sess.connect(30_000)
            session = sess

            val ch = sess.openChannel("sftp") as ChannelSftp
            ch.connect(15_000)
            channel = ch
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Không thể kết nối SFTP: ${e.message}"))
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try { channel?.disconnect(); session?.disconnect() } catch (_: Exception) {}
    }

    override suspend fun listFiles(path: String): Result<List<NetworkItem>> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(Exception("Chưa kết nối"))
            @Suppress("UNCHECKED_CAST")
            val entries = ch.ls(path) as Vector<ChannelSftp.LsEntry>
            val items = entries
                .filter { it.filename != "." && it.filename != ".." }
                .map { entry ->
                    NetworkItem.from(
                        name = entry.filename,
                        parentPath = path,
                        isDir = entry.attrs.isDir,
                        size = entry.attrs.size,
                        modified = entry.attrs.mTime.toLong() * 1000
                    )
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi liệt kê file: ${e.message}"))
        }
    }

    override suspend fun downloadFile(
        remotePath: String, localFile: File, onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(Exception("Chưa kết nối"))
            val attrs = ch.lstat(remotePath)
            val total = attrs.size

            ch.get(remotePath, FileOutputStream(localFile), object : SftpProgressMonitor {
                var transferred = 0L
                override fun init(op: Int, src: String?, dest: String?, max: Long) {}
                override fun count(count: Long): Boolean {
                    transferred += count
                    if (total > 0) onProgress(transferred.toFloat() / total)
                    return true
                }
                override fun end() { onProgress(1f) }
            })
            Result.success(localFile)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi tải file: ${e.message}"))
        }
    }

    override suspend fun uploadFile(
        localFile: File, remotePath: String, onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(Exception("Chưa kết nối"))
            val total = localFile.length()

            ch.put(FileInputStream(localFile), remotePath, object : SftpProgressMonitor {
                var transferred = 0L
                override fun init(op: Int, src: String?, dest: String?, max: Long) {}
                override fun count(count: Long): Boolean {
                    transferred += count
                    if (total > 0) onProgress(transferred.toFloat() / total)
                    return true
                }
                override fun end() { onProgress(1f) }
            }, ChannelSftp.OVERWRITE)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi upload: ${e.message}"))
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(Exception("Chưa kết nối"))
            try { ch.rm(path) } catch (_: Exception) { ch.rmdir(path) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi xóa: ${e.message}"))
        }
    }

    override suspend fun createFolder(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            channel?.mkdir(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rename(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val newPath = "${oldPath.substringBeforeLast("/")}/$newName"
            channel?.rename(oldPath, newPath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Helper alias
typealias SftpProgressMonitor = com.jcraft.jsch.SftpProgressMonitor
typealias Vector<T> = java.util.Vector<T>


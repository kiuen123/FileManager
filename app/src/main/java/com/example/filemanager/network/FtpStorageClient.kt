package com.example.filemanager.network

import com.example.filemanager.model.NetworkConnection
import com.example.filemanager.model.NetworkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FtpStorageClient(private val conn: NetworkConnection) : NetworkStorageClient {

    private val client: FTPClient = if (conn.protocol.name == "FTPS") FTPSClient("TLS", true) else FTPClient()

    override fun isConnected() = client.isConnected

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.connect(conn.host, conn.port)
            client.soTimeout = 30_000
            val ok = if (conn.isAnonymous) {
                client.login("anonymous", "")
            } else {
                client.login(conn.username, conn.password)
            }
            if (!ok) return@withContext Result.failure(Exception("Sai tên đăng nhập hoặc mật khẩu"))
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Không thể kết nối FTP: ${e.message}"))
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try { if (client.isConnected) { client.logout(); client.disconnect() } } catch (_: Exception) {}
    }

    override suspend fun listFiles(path: String): Result<List<NetworkItem>> = withContext(Dispatchers.IO) {
        try {
            val files = client.listFiles(path) ?: return@withContext Result.success(emptyList())
            val items = files
                .filter { it.name != "." && it.name != ".." && it.name.isNotBlank() }
                .map { f ->
                    NetworkItem.from(
                        name = f.name,
                        parentPath = path,
                        isDir = f.isDirectory,
                        size = f.size,
                        modified = f.timestamp?.timeInMillis ?: 0L
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
            val size = client.listFiles(remotePath.substringBeforeLast("/"))
                ?.firstOrNull { it.name == remotePath.substringAfterLast("/") }?.size ?: -1L

            val input = client.retrieveFileStream(remotePath)
                ?: return@withContext Result.failure(Exception("Không thể lấy file"))

            var downloaded = 0L
            FileOutputStream(localFile).use { out ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    if (size > 0) onProgress(downloaded.toFloat() / size)
                }
            }
            input.close()
            client.completePendingCommand()
            onProgress(1f)
            Result.success(localFile)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi tải file: ${e.message}"))
        }
    }

    override suspend fun uploadFile(
        localFile: File, remotePath: String, onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val total = localFile.length()
            var uploaded = 0L
            val outStream = client.storeFileStream(remotePath)
                ?: return@withContext Result.failure(Exception("Không thể upload"))
            FileInputStream(localFile).use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    outStream.write(buf, 0, read)
                    uploaded += read
                    if (total > 0) onProgress(uploaded.toFloat() / total)
                }
            }
            outStream.close()
            client.completePendingCommand()
            onProgress(1f)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi upload: ${e.message}"))
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deleted = client.deleteFile(path) || client.removeDirectory(path)
            if (deleted) Result.success(Unit)
            else Result.failure(Exception("Không thể xóa: $path"))
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi xóa: ${e.message}"))
        }
    }

    override suspend fun createFolder(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (client.makeDirectory(path)) Result.success(Unit)
            else Result.failure(Exception("Không thể tạo thư mục"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rename(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val newPath = "${oldPath.substringBeforeLast("/")}/$newName"
            if (client.rename(oldPath, newPath)) Result.success(Unit)
            else Result.failure(Exception("Không thể đổi tên"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


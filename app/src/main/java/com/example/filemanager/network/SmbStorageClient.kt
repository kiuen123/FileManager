package com.example.filemanager.network

import com.example.filemanager.model.NetworkConnection
import com.example.filemanager.model.NetworkItem
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class SmbStorageClient(private val conn: NetworkConnection) : NetworkStorageClient {

    private var smbClient: SMBClient? = null
    private var smbConn: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null
    private var connected = false

    override fun isConnected() = connected && diskShare != null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Close any previous connections cleanly
            runCatching { diskShare?.close() }
            runCatching { session?.close() }
            runCatching { smbConn?.close() }
            runCatching { smbClient?.close() }

            val client = SMBClient(buildSmbConfig())
            smbClient = client

            val port = conn.port.takeIf { it > 0 } ?: 445
            val newConn = client.connect(conn.host, port)
            smbConn = newConn

            val auth = if (conn.isAnonymous) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    conn.username,
                    conn.password.toCharArray(),
                    conn.domain.let { if (it.isBlank() || it == "WORKGROUP") "" else it }
                )
            }

            val newSession = newConn.authenticate(auth)
            session = newSession
            val share = newSession.connectShare(conn.shareName)

            diskShare = share as? DiskShare ?: run {
                runCatching { share.close() }
                return@withContext Result.failure(
                    Exception(
                        "Share '${conn.shareName}' không phải ổ đĩa.\n" +
                        "Hãy kiểm tra lại tên share trong Windows (Win+R → \\\\localhost)."
                    )
                )
            }
            connected = true
            Result.success(Unit)
        } catch (e: Exception) {
            connected = false
            Result.failure(Exception(friendlySmbError(e)))
        }
    }

    /** Tự kết nối lại nếu share đã bị đóng (idle timeout từ server). */
    private suspend fun reconnect(): Result<Unit> {
        connected = false
        diskShare = null
        return connect()
    }

    /** Thực hiện thao tác với DiskShare, tự động kết nối lại nếu bị mất. */
    private suspend fun <T> withAutoReconnect(
        operation: suspend (DiskShare) -> T
    ): Result<T> {
        // Ensure connected before first attempt
        if (!connected || diskShare == null) {
            val r = reconnect()
            if (r.isFailure) return Result.failure(r.exceptionOrNull()!!)
        }

        return try {
            val share = diskShare ?: return Result.failure(Exception("Chưa kết nối"))
            Result.success(operation(share))
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            val isStaleConn = "already been closed" in msg ||
                "broken pipe" in msg ||
                "connection reset" in msg ||
                "socket closed" in msg ||
                "end of stream" in msg ||
                "session expired" in msg ||
                "nt_status_network_name_deleted" in msg

            if (isStaleConn) {
                // Reconnect once and retry
                val r = reconnect()
                if (r.isFailure) {
                    return Result.failure(
                        Exception("Mất kết nối SMB và không thể kết nối lại:\n${r.exceptionOrNull()?.message}")
                    )
                }
                try {
                    val share = diskShare ?: return Result.failure(Exception("Chưa kết nối"))
                    Result.success(operation(share))
                } catch (e2: Exception) {
                    Result.failure(Exception(friendlySmbError(e2)))
                }
            } else {
                Result.failure(Exception(friendlySmbError(e)))
            }
        }
    }

    companion object {

        fun buildSmbConfig(): SmbConfig = SmbConfig.builder()
            .withSigningRequired(false)
            .withDfsEnabled(false)
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(60, TimeUnit.SECONDS)
            .build()

        fun friendlySmbError(e: Exception): String {
            val raw = e.message ?: "Unknown error"
            val msg = raw.lowercase()
            return when {
                "connection refused" in msg || "connect timed out" in msg ||
                "connection timed out" in msg || "etimedout" in msg ||
                "enetunreach" in msg ->
                    "❌ Không kết nối được tới máy Windows.\n\n" +
                    "Kiểm tra trên máy Windows:\n" +
                    "① Bật 'File and Printer Sharing' trong Windows Firewall\n" +
                    "② Đặt WiFi sang chế độ 'Private' (không phải Public)\n" +
                    "③ Kiểm tra IP: Settings → Wi-Fi → Properties → IPv4\n" +
                    "④ Tắt thử VPN/proxy nếu đang bật"

                "nt_status_logon_failure" in msg || "logon failure" in msg ||
                "authentication" in msg || "nt_status_wrong_password" in msg ||
                "wrong password" in msg ->
                    "❌ Sai tên đăng nhập hoặc mật khẩu.\n\n" +
                    "Lưu ý quan trọng:\n" +
                    "• Dùng tài khoản LOCAL Windows\n  (không phải email @outlook/@gmail)\n" +
                    "• Tên đăng nhập = tên hiện ở màn hình khóa\n" +
                    "• Để trống Domain hoặc nhập tên máy tính\n  (VD: DESKTOP-XXXX)"

                "nt_status_account_disabled" in msg ->
                    "❌ Tài khoản Windows bị vô hiệu hóa.\nBật lại tài khoản trong User Accounts."

                "nt_status_password_expired" in msg ->
                    "❌ Mật khẩu Windows đã hết hạn. Hãy đổi mật khẩu trên máy tính."

                "nt_status_bad_network_name" in msg || "bad network name" in msg ||
                "share not found" in msg || "does not exist" in msg ->
                    "❌ Không tìm thấy share '${raw.substringAfterLast("'").trimEnd('.')}'\n\n" +
                    "Cách tìm đúng tên share:\n" +
                    "① Nhấn Win+R → gõ \\\\localhost → Enter\n" +
                    "② Xem danh sách các share hiện có\n" +
                    "③ Chuột phải folder → Properties → Sharing → Share name"

                "nt_status_access_denied" in msg || "access denied" in msg ->
                    "❌ Không có quyền truy cập share này.\n\n" +
                    "Kiểm tra trên máy Windows:\n" +
                    "• Chuột phải folder → Properties → Sharing\n" +
                    "• Thêm user hoặc 'Everyone' vào danh sách\n" +
                    "• Cấp quyền 'Read' hoặc 'Read/Write'"

                "dialect" in msg || "protocol" in msg || "negotiate" in msg ->
                    "❌ Không tương thích giao thức SMB.\n" +
                    "Windows 10/11: Kiểm tra SMB1 bị tắt (bình thường),\n" +
                    "ứng dụng dùng SMB2/3 nên không cần bật SMB1."

                "nt_status_" in msg ->
                    "❌ Windows trả lỗi SMB: $raw\n" +
                    "Thử tắt rồi bật lại Windows Firewall."

                else -> "❌ Lỗi SMB: $raw"
            }
        }

        suspend fun browseShares(
            host: String,
            username: String = "",
            password: String = "",
            domain: String = ""
        ): Result<List<String>> = withContext(Dispatchers.IO) {
            try {
                val client = SMBClient(buildSmbConfig())
                val smbConn = client.connect(host)
                val auth = if (username.isBlank())
                    AuthenticationContext.anonymous()
                else
                    AuthenticationContext(
                        username,
                        password.toCharArray(),
                        domain.let { if (it.isBlank() || it == "WORKGROUP") "" else it }
                    )

                val session = smbConn.authenticate(auth)
                val found = mutableListOf<String>()

                val candidates = listOf(
                    "Users", "Public", "SharedDocs", "USERS",
                    "Documents", "Downloads", "Desktop", "Pictures", "Videos", "Music",
                    "C", "D", "E", "F",
                    "homes", "home", "share", "Share", "Shared",
                    "Data", "Backup", "Files", "NAS", "Media"
                )
                for (name in candidates) {
                    try {
                        val s = session.connectShare(name)
                        if (s is DiskShare) found.add(name)
                        runCatching { s.close() }
                    } catch (_: Exception) { }
                }

                runCatching { session.close() }
                runCatching { smbConn.close() }
                runCatching { client.close() }

                Result.success(found)
            } catch (e: Exception) {
                Result.failure(Exception(friendlySmbError(e)))
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connected = false
        runCatching { diskShare?.close() }
        runCatching { session?.close() }
        runCatching { smbConn?.close() }
        runCatching { smbClient?.close() }
        diskShare = null; session = null; smbConn = null; smbClient = null
    }

    override suspend fun listFiles(path: String): Result<List<NetworkItem>> =
        withContext(Dispatchers.IO) {
            withAutoReconnect { share ->
                val smbPath = normalizeSmbPath(path)
                val entries = if (smbPath.isEmpty()) share.list("") else share.list(smbPath)
                entries
                    .filter { it.fileName != "." && it.fileName != ".." }
                    .map { entry ->
                        val isDir = entry.fileAttributes and 0x10.toLong() != 0L
                        NetworkItem.from(
                            name = entry.fileName,
                            parentPath = path,
                            isDir = isDir,
                            size = entry.endOfFile,
                            modified = entry.lastWriteTime.toEpochMillis()
                        )
                    }
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
        }

    override suspend fun downloadFile(
        remotePath: String, localFile: File, onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        withAutoReconnect { share ->
            val smbPath = normalizeSmbPath(remotePath)
            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            val total = file.fileInformation.standardInformation.endOfFile
            var downloaded = 0L
            file.inputStream.use { input ->
                FileOutputStream(localFile).use { out ->
                    val buf = ByteArray(65536)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) onProgress(downloaded.toFloat() / total)
                    }
                }
            }
            file.close()
            onProgress(1f)
            localFile
        }
    }

    override suspend fun uploadFile(
        localFile: File, remotePath: String, onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        withAutoReconnect { share ->
            val smbPath = normalizeSmbPath(remotePath)
            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null
            )
            val total = localFile.length()
            var uploaded = 0L
            FileInputStream(localFile).use { input ->
                file.outputStream.use { out ->
                    val buf = ByteArray(65536)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        uploaded += read
                        if (total > 0) onProgress(uploaded.toFloat() / total)
                    }
                }
            }
            file.close()
            onProgress(1f)
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        withAutoReconnect { share ->
            val smbPath = normalizeSmbPath(path)
            try { share.rm(smbPath) } catch (_: Exception) { share.rmdir(smbPath, true) }
        }
    }

    override suspend fun createFolder(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        withAutoReconnect { share ->
            share.mkdir(normalizeSmbPath(path))
        }
    }

    override suspend fun rename(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        withAutoReconnect { share ->
            val file = share.openFile(
                normalizeSmbPath(oldPath),
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_DELETE, SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            val newPath = "${oldPath.substringBeforeLast("/")}/$newName"
            file.rename(normalizeSmbPath(newPath))
            file.close()
        }
    }

    private fun normalizeSmbPath(path: String): String =
        path.trimStart('/').replace('/', '\\')
}


package com.example.filemanager.network

import com.example.filemanager.model.DriveItem
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// ── Response models ─────────────────────────────────────────────────────────

data class DriveFileResponse(
    val id: String = "",
    val name: String = "",
    val mimeType: String = "",
    @SerializedName("size") val sizeStr: String? = null,
    val modifiedTime: String = "",
    val parents: List<String>? = null,
    val webViewLink: String = "",
    val thumbnailLink: String = ""
) {
    fun toDriveItem() = DriveItem(
        id = id,
        name = name,
        mimeType = mimeType,
        size = sizeStr?.toLongOrNull() ?: 0L,
        modifiedTime = modifiedTime,
        parents = parents ?: emptyList(),
        webViewLink = webViewLink,
        thumbnailLink = thumbnailLink
    )
}

data class DriveListResponse(
    val files: List<DriveFileResponse> = emptyList(),
    val nextPageToken: String? = null
)

// ── Drive API Client ─────────────────────────────────────────────────────────

class DriveApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val baseUrl = "https://www.googleapis.com/drive/v3"

    // ── List files in a folder ─────────────────────────────────────────────

    suspend fun listFiles(
        accessToken: String,
        folderId: String = "root",
        pageToken: String? = null
    ): Result<Pair<List<DriveItem>, String?>> = withContext(Dispatchers.IO) {
        try {
            val query = "'$folderId' in parents and trashed = false"
            val fields = "files(id,name,mimeType,size,modifiedTime,parents,webViewLink),nextPageToken"
            val urlBuilder = StringBuilder("$baseUrl/files")
                .append("?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                .append("&fields=${java.net.URLEncoder.encode(fields, "UTF-8")}")
                .append("&orderBy=folder,name")
                .append("&pageSize=100")
            if (pageToken != null) urlBuilder.append("&pageToken=$pageToken")

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi ${response.code}: $body"))

            val listResponse = gson.fromJson(body, DriveListResponse::class.java)
            val items = listResponse.files.map { it.toDriveItem() }
            Result.success(Pair(items, listResponse.nextPageToken))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Search files ───────────────────────────────────────────────────────

    suspend fun searchFiles(accessToken: String, query: String): Result<List<DriveItem>> =
        withContext(Dispatchers.IO) {
            try {
                val q = "name contains '${query.replace("'", "\\'")}' and trashed = false"
                val fields = "files(id,name,mimeType,size,modifiedTime,parents)"
                val url = "$baseUrl/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
                    "&fields=${java.net.URLEncoder.encode(fields, "UTF-8")}&pageSize=50"

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get().build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi ${response.code}"))

                val listResponse = gson.fromJson(body, DriveListResponse::class.java)
                Result.success(listResponse.files.map { it.toDriveItem() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ── Download file ──────────────────────────────────────────────────────

    suspend fun downloadFile(
        accessToken: String,
        item: DriveItem,
        destDir: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = if (item.isGoogleDoc) {
                "$baseUrl/files/${item.id}/export?mimeType=${java.net.URLEncoder.encode(item.exportMimeType, "UTF-8")}"
            } else {
                "$baseUrl/files/${item.id}?alt=media"
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get().build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi ${response.code}"))

            val fileName = if (item.isGoogleDoc) item.exportFileName else item.name
            val destFile = File(destDir, fileName)
            val body = response.body ?: return@withContext Result.failure(Exception("Không có dữ liệu"))
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            var downloadedBytes = 0L

            FileOutputStream(destFile).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Upload file ────────────────────────────────────────────────────────

    suspend fun uploadFile(
        accessToken: String,
        localFile: File,
        parentFolderId: String,
        onProgress: (Float) -> Unit
    ): Result<DriveItem> = withContext(Dispatchers.IO) {
        try {
            val mimeType = getMimeType(localFile.extension)
            val metadata = """{"name":"${localFile.name}","parents":["$parentFolderId"]}"""

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata", null,
                    metadata.toRequestBody("application/json".toMediaType())
                )
                .addFormDataPart(
                    "file", localFile.name,
                    localFile.asRequestBody(mimeType.toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime")
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi ${response.code}: $body"))

            val file = gson.fromJson(body, DriveFileResponse::class.java)
            onProgress(1f)
            Result.success(file.toDriveItem())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Create folder ──────────────────────────────────────────────────────

    suspend fun createFolder(
        accessToken: String,
        name: String,
        parentId: String
    ): Result<DriveItem> = withContext(Dispatchers.IO) {
        try {
            val body = """{"name":"$name","mimeType":"application/vnd.google-apps.folder","parents":["$parentId"]}"""
            val request = Request.Builder()
                .url("$baseUrl/files?fields=id,name,mimeType,modifiedTime")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi ${response.code}"))

            val file = gson.fromJson(respBody, DriveFileResponse::class.java)
            Result.success(file.toDriveItem())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Delete file ────────────────────────────────────────────────────────

    suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/files/$fileId")
                    .header("Authorization", "Bearer $accessToken")
                    .delete().build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi ${response.code}"))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ── Rename file ────────────────────────────────────────────────────────

    suspend fun renameFile(
        accessToken: String,
        fileId: String,
        newName: String
    ): Result<DriveItem> = withContext(Dispatchers.IO) {
        try {
            val body = """{"name":"$newName"}"""
            val request = Request.Builder()
                .url("$baseUrl/files/$fileId?fields=id,name,mimeType,size,modifiedTime")
                .header("Authorization", "Bearer $accessToken")
                .patch(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi ${response.code}"))

            val file = gson.fromJson(respBody, DriveFileResponse::class.java)
            Result.success(file.toDriveItem())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Get user info ──────────────────────────────────────────────────────

    suspend fun getUserInfo(accessToken: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer $accessToken")
                    .get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val name = json.get("name")?.asString ?: ""
                val email = json.get("email")?.asString ?: ""
                Result.success(Pair(name, email))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun getMimeType(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "doc", "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}


package com.example.filemanager.model

import java.util.UUID

enum class NetworkProtocol(val displayName: String, val defaultPort: Int) {
    FTP("FTP", 21),
    FTPS("FTPS", 990),
    SFTP("SFTP", 22),
    SMB("SMB / Windows Share", 445)
}

data class NetworkConnection(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "",
    val protocol: NetworkProtocol = NetworkProtocol.FTP,
    val host: String = "",
    val port: Int = 21,
    val username: String = "",
    val password: String = "",
    val initialPath: String = "/",   // FTP/SFTP root path
    val shareName: String = "",      // SMB share name (e.g. "Public")
    val domain: String = ""       // SMB domain; empty = let Windows negotiate (best for Win10)
) {
    val isAnonymous: Boolean get() = username.isBlank() || username == "anonymous"
}


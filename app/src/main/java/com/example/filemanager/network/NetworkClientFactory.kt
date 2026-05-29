package com.example.filemanager.network

import com.example.filemanager.model.NetworkConnection
import com.example.filemanager.model.NetworkProtocol

object NetworkClientFactory {
    fun create(connection: NetworkConnection): NetworkStorageClient = when (connection.protocol) {
        NetworkProtocol.FTP, NetworkProtocol.FTPS -> FtpStorageClient(connection)
        NetworkProtocol.SFTP -> SftpStorageClient(connection)
        NetworkProtocol.SMB -> SmbStorageClient(connection)
    }
}


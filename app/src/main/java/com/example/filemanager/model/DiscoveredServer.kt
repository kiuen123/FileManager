package com.example.filemanager.model

data class DiscoveredServer(
    val host: String,
    val port: Int,
    val protocol: NetworkProtocol,
    val name: String,
    val source: String = "Quét mạng"  // "Quét mạng" or "mDNS"
)


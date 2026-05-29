package com.example.filemanager.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.filemanager.model.DiscoveredServer
import com.example.filemanager.model.NetworkProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class NetworkDiscoveryService(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    /** Port → Protocol mapping, checked in priority order */
    private val portProtocols = listOf(
        445 to NetworkProtocol.SMB,
        21  to NetworkProtocol.FTP,
        22  to NetworkProtocol.SFTP,
    )

    // ── IP helpers ────────────────────────────────────────────────────────

    /** Returns the device's active local IPv4 address, e.g. "192.168.1.100" */
    fun getLocalIpAddress(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { !it.isLoopbackAddress && !it.hostAddress!!.contains(':') }
            ?.hostAddress
    } catch (_: Exception) { null }

    /** Returns the /24 subnet prefix, e.g. "192.168.1" */
    fun getLocalSubnet(): String? =
        getLocalIpAddress()?.substringBeforeLast(".")

    // ── Subnet scan ───────────────────────────────────────────────────────

    /**
     * Scans 192.168.x.1–254 for open SMB / FTP / SFTP ports.
     * [onFound] is called from a background thread each time a server is discovered.
     * [onProgress] is called with (scanned, total) after each host.
     */
    suspend fun scanSubnet(
        onFound: (DiscoveredServer) -> Unit,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val subnet = getLocalSubnet() ?: return@withContext
        val myIp   = getLocalIpAddress()
        val total  = 254
        val scanned = AtomicInteger(0)

        (1..254).map { i ->
            async {
                val host = "$subnet.$i"
                if (host != myIp) {
                    checkHost(host)?.let { onFound(it) }
                }
                onProgress(scanned.incrementAndGet(), total)
            }
        }.awaitAll()
    }

    private fun checkHost(host: String): DiscoveredServer? {
        for ((port, protocol) in portProtocols) {
            if (isPortOpen(host, port)) {
                return DiscoveredServer(
                    host     = host,
                    port     = port,
                    protocol = protocol,
                    name     = host,
                    source   = "Quét mạng"
                )
            }
        }
        return null
    }

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int = 400): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Exception) { false }

    // ── mDNS / DNS-SD discovery ───────────────────────────────────────────

    /**
     * Starts mDNS discovery for FTP / SFTP / SMB services (NAS, macOS shares, etc.).
     * [onFound] may be called from an arbitrary thread.
     * Returns a lambda that stops all discovery listeners when invoked.
     */
    fun startNsdDiscovery(onFound: (DiscoveredServer) -> Unit): () -> Unit {
        val nsd = nsdManager ?: return {}

        val serviceTypes = mapOf(
            "_ftp._tcp."      to NetworkProtocol.FTP,
            "_sftp-ssh._tcp." to NetworkProtocol.SFTP,
            "_smb._tcp."      to NetworkProtocol.SMB,
            "_smb2._tcp."     to NetworkProtocol.SMB,
        )

        val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

        for ((serviceType, protocol) in serviceTypes) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(st: String)             {}
                override fun onDiscoveryStopped(st: String)             {}
                override fun onStartDiscoveryFailed(st: String, e: Int) {}
                override fun onStopDiscoveryFailed(st: String, e: Int)  {}
                override fun onServiceLost(info: NsdServiceInfo)        {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    resolveNsdService(info, protocol, onFound)
                }
            }
            try {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                activeListeners += listener
            } catch (_: Exception) { /* NSD unavailable or already active */ }
        }

        return {
            activeListeners.forEach { l ->
                try { nsd.stopServiceDiscovery(l) } catch (_: Exception) {}
            }
            activeListeners.clear()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNsdService(
        serviceInfo: NsdServiceInfo,
        protocol: NetworkProtocol,
        onFound: (DiscoveredServer) -> Unit
    ) {
        val nsd = nsdManager ?: return
        try {
            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                @Suppress("DEPRECATION")
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress?.takeIf { ':' !in it } ?: return
                    val port = info.port.takeIf { it > 0 } ?: protocol.defaultPort
                    onFound(
                        DiscoveredServer(
                            host     = host,
                            port     = port,
                            protocol = protocol,
                            name     = info.serviceName.ifBlank { host },
                            source   = "mDNS"
                        )
                    )
                }
            })
        } catch (_: Exception) {}
    }
}

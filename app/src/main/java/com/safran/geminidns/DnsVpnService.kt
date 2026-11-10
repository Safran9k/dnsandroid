package com.safran.geminidns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class DnsVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.safran.geminidns.START_VPN"
        const val ACTION_STOP = "com.safran.geminidns.STOP_VPN"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_DNS_IPS = "dns_ips"
        const val EXTRA_DOH_URL = "doh_url"
        const val EXTRA_USE_DOH = "use_doh"

        @Volatile
        var isRunning: Boolean = false
        @Volatile
        var activeServerName: String = ""
        @Volatile
        var activeDnsInfo: String = ""
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile
    private var dohLoopActive = false
    private val executor = Executors.newCachedThreadPool()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Smart DNS"
                val ips = intent.getStringArrayListExtra(EXTRA_DNS_IPS) ?: arrayListOf()
                val dohUrl = intent.getStringExtra(EXTRA_DOH_URL) ?: ""
                val useDoh = intent.getBooleanExtra(EXTRA_USE_DOH, false)
                startVpn(serverName, ips, dohUrl, useDoh)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(serverName: String, ips: List<String>, dohUrl: String, useDoh: Boolean) {
        stopVpn()

        val channelId = "gemini_dns_vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Gemini DNS Switcher Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(chan)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeDesc = if (useDoh && dohUrl.isNotEmpty()) "DoH: $dohUrl" else "DNS: ${ips.joinToString(", ")}"
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("🟢 Активен: $serverName")
                .setContentText(modeDesc)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("🟢 Активен: $serverName")
                .setContentText(modeDesc)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(101, notification)
            }
        } catch (_: Exception) {}

        try {
            val builder = Builder()
                .setSession("Gemini DNS ($serverName)")
                .setMtu(1500)
                .addAddress("10.215.173.1", 30)

            // Исключаем само приложение из VPN, чтобы тестировщик и DoH-клиент работали напрямую
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            if (useDoh && dohUrl.isNotEmpty()) {
                // В режиме DoH перехватываем виртуальный DNS 10.215.173.2 и туннелируем в HTTPS (порт 443)
                builder.addDnsServer("10.215.173.2")
                builder.addRoute("10.215.173.2", 32)
                vpnInterface = builder.establish()
                isRunning = vpnInterface != null
                activeServerName = serverName
                activeDnsInfo = "DoH-мост ($dohUrl)"
                if (isRunning) {
                    dohLoopActive = true
                    startDohPacketLoop(vpnInterface!!, dohUrl)
                }
            } else {
                // В режиме IPv4 Smart DNS прописываем выбранные DNS-серверы на весь телефон
                val validIps = ips.filter { DnsTester.isValidIpv4(it) }
                for (ip in validIps) {
                    builder.addDnsServer(ip)
                }
                // Маршрутизируем только служебный локальный адрес, чтобы весь трафик шёл напрямую без потери скорости
                builder.addRoute("10.215.173.2", 32)
                vpnInterface = builder.establish()
                isRunning = vpnInterface != null
                activeServerName = serverName
                activeDnsInfo = validIps.joinToString(", ")
            }
        } catch (_: Exception) {
            isRunning = false
        }
    }

    private fun startDohPacketLoop(pfd: ParcelFileDescriptor, dohUrl: String) {
        Thread {
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)

            while (dohLoopActive && isRunning) {
                try {
                    val length = input.read(buffer)
                    if (length <= 28) continue
                    val pkt = buffer.copyOfRange(0, length)
                    val version = (pkt[0].toInt() ushr 4) and 0x0F
                    val protocol = pkt[9].toInt() and 0xFF
                    if (version != 4 || protocol != 17) continue // Только IPv4 UDP

                    val ihl = (pkt[0].toInt() and 0x0F) * 4
                    if (length < ihl + 8) continue

                    val dstPort = ((pkt[ihl + 2].toInt() and 0xFF) shl 8) or (pkt[ihl + 3].toInt() and 0xFF)
                    if (dstPort != 53) continue

                    val dnsPayload = pkt.copyOfRange(ihl + 8, length)
                    executor.execute {
                        val dnsAnswer = DnsTester.queryDnsDohRaw(dohUrl, dnsPayload, 3000)
                        if (dnsAnswer != null && dnsAnswer.isNotEmpty()) {
                            val replyPacket = buildUdpIpResponse(pkt, ihl, dnsAnswer)
                            synchronized(output) {
                                try {
                                    output.write(replyPacket)
                                    output.flush()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (!dohLoopActive) break
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun buildUdpIpResponse(reqPkt: ByteArray, ihl: Int, dnsPayload: ByteArray): ByteArray {
        val totalLen = 20 + 8 + dnsPayload.size
        val out = ByteBuffer.allocate(totalLen)

        // IPv4 Header (20 bytes)
        out.put(0x45.toByte())
        out.put(0x00.toByte())
        out.putShort(totalLen.toShort())
        out.putShort(0.toShort())
        out.putShort(0x4000.toShort())
        out.put(64.toByte())
        out.put(17.toByte()) // UDP
        out.putShort(0.toShort()) // Checksum placeholder

        // Swap Source & Destination IP
        out.put(reqPkt, 16, 4) // Src IP = Old Dst IP
        out.put(reqPkt, 12, 4) // Dst IP = Old Src IP

        // Calculate IPv4 header checksum
        val arr = out.array()
        var sum = 0
        for (i in 0 until 20 step 2) {
            val word = ((arr[i].toInt() and 0xFF) shl 8) or (arr[i + 1].toInt() and 0xFF)
            sum += word
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val checksum = sum.inv() and 0xFFFF
        out.putShort(10, checksum.toShort())

        // UDP Header (8 bytes) - Swap ports
        out.position(20)
        out.put(reqPkt, ihl + 2, 2) // Src Port = 53
        out.put(reqPkt, ihl, 2)     // Dst Port = Client Port
        out.putShort((8 + dnsPayload.size).toShort())
        out.putShort(0.toShort())   // Zero UDP checksum (valid in IPv4)

        // DNS Payload
        out.put(dnsPayload)
        return out.array()
    }

    private fun stopVpn() {
        dohLoopActive = false
        isRunning = false
        activeServerName = ""
        activeDnsInfo = ""
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

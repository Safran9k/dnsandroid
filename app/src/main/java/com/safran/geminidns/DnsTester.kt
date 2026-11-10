package com.safran.geminidns

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class DnsEntry(
    var name: String,
    var rawTargets: List<String>,
    var ips: List<String>,
    var dohUrl: String = "",
    var working: Boolean? = null,
    var partial: Boolean = false,
    var geminiOk: Boolean? = null,
    var chatgptOk: Boolean? = null,
    var claudeOk: Boolean? = null,
    var ping: Int = 9999,
    var aiMs: Int = 9999,
    var quality: Int = 0,
    var score: Int = 99999,
    var country: String = "—",
    var proxyIp: String? = null,
    var usedDoh: Boolean = false,
    var reason: String = "Нажмите «Тестировать» для проверки Gemini / ChatGPT / Claude"
)

object DnsTester {

    val DEFAULT_DNS_TEXT = """
MagicDNS (Швеция / Нидерланды — Лучший) | 109.120.132.43, 94.249.177.102 | https://api.magicdns.ru/dns-query
Германия, Франкфурт (GeoHide #1) | 5.175.161.40, 5.175.161.3 | https://eu.geohide.ru/dns-query
Keyoneday DNS (DoH Франция / Италия) | dns.keyoneday.ru | https://dns.keyoneday.ru/dns-query
MagicDNS (Узел #2 Швеция) | 109.120.132.44, 45.88.163.92 | https://se.magicdns.ru/dns-query
MagicDNS (Основной DoH/IPv4) | 45.88.163.92, 109.120.132.43 | https://dns.magicdns.ru/dns-query
Германия (Узел 94.249.177.102) | 94.249.177.102, 109.120.132.43
Германия, Франкфурт (Узел 37.221.93.141) | 37.221.93.141, 37.221.93.128
Германия, Франкфурт (Узел 5.175.161.3) | 5.175.161.3, 5.175.161.40
GeoHide EU (Европа Кластер) | 143.20.64.192, 45.13.237.213 | https://eu.geohide.ru/dns-query
Нидерланды, Амстердам (Bezmezhau #1) | 31.58.216.201, 181.215.40.49 | https://dns.bezmezhau.com/dns-query
Bezmezhau DNS (Европа #2) | 143.20.64.55, 185.236.10.189 | https://dns.bezmezhau.com/dns-query
GeoHide US (США Основной) | 192.255.159.240, 192.3.233.253 | https://us.geohide.ru/dns-query
GeoHide US (США Резерв) | 107.175.76.134, 192.255.159.240 | https://us.geohide.ru/dns-query
Финляндия (Smart AI Node) | 144.31.82.230, 95.216.232.133
AstraCat Smart DNS | 135.106.217.200 | https://dns.astracat.ru/dns-query
GeoHide DNS (Кластер #1) | 159.194.200.33, 193.233.112.88 | https://dns.geohide.ru/dns-query
GeoHide DNS (Кластер #2) | 193.233.112.67, 193.233.112.68
GeoHide DNS (Кластер #3) | 193.233.112.130, 37.230.192.51
GeoHide DNS (Кластер #4) | 185.87.51.182, 31.25.239.132
DNS-AI.ru (Новый кластер) | 194.87.196.13, 192.144.59.14 | https://dns.dns-ai.ru/dns-query
DNS-AI.ru (Резерв) | 192.144.59.16, 194.87.196.13
WL RUS Smart DNS | 95.85.240.254, 138.124.254.134 | https://dns.wlrus.cfd/dns-query
WebShift Smart DNS #1 | 31.31.207.68, 87.228.47.200
WebShift Smart DNS #2 | 87.228.47.201, 31.31.207.68
MagicDNS / Dagami | 109.120.177.170, 45.88.163.92 | https://dns.dagami.ru/dns-query
Mafioznik Smart DNS (DoH) | dns.mafioznik.xyz | https://dns.mafioznik.xyz/dns-query
GeminiDNS Dedicated (DoH) | geminidns.site | https://geminidns.site/dns-query
NTGW Relay DNS (DoH) | ntgw-relay.site | https://ntgw-relay.site/dns-query
Comss.one Smart DNS #1 | 83.220.169.155, 212.109.195.93 | https://dns.comss.one/dns-query
Comss.one Router DNS | 195.133.25.16, 195.133.25.99 | https://router.comss.one/dns-query
Comss.one Reserve | 92.223.65.71, 185.146.157.78
Malw.link Smart DNS (Новый) | 179.254.166.171, 164.5.224.81 | https://dns.malw.link/dns-query
Malw.link Smart DNS (Резерв) | 193.23.209.189, 89.111.174.129 | https://dns.malw.link/dns-query
Xbox-DNS.ru (Основной) | 111.88.96.54, 111.88.96.55 | https://xbox-dns.ru/dns-query
Xbox Smart DNS (Узел #1) | 176.99.11.77, 80.78.247.254
Xbox Smart DNS (Узел #2) | 31.192.108.180, 37.220.84.124
Xbox Smart DNS (Узел #3) | 45.90.33.120, 212.220.116.30
Nulls Proxy DNS #1 | 45.139.239.54, 45.139.239.56
Nulls Proxy DNS #2 | 193.124.47.103, 50.7.85.221
ControlD Unblock Proxy | 76.76.2.5, 76.76.10.5
Shecan Anti-Sanction | 178.22.122.100, 185.51.200.2
Electro Anti-Geo DNS | 78.157.42.100, 78.157.42.101
""".trimIndent()

    val DISCOVERY_DOMAINS = listOf(
        Triple("MagicDNS API", "api.magicdns.ru", "https://api.magicdns.ru/dns-query"),
        Triple("MagicDNS SE", "se.magicdns.ru", "https://se.magicdns.ru/dns-query"),
        Triple("MagicDNS Main", "dns.magicdns.ru", "https://dns.magicdns.ru/dns-query"),
        Triple("KeyOneDay DNS", "dns.keyoneday.ru", "https://dns.keyoneday.ru/dns-query"),
        Triple("GeoHide EU", "eu.geohide.ru", "https://eu.geohide.ru/dns-query"),
        Triple("GeoHide US", "us.geohide.ru", "https://us.geohide.ru/dns-query"),
        Triple("GeoHide Main", "dns.geohide.ru", "https://dns.geohide.ru/dns-query"),
        Triple("Bezmezhau DNS", "dns.bezmezhau.com", "https://dns.bezmezhau.com/dns-query"),
        Triple("AstraCat DNS", "dns.astracat.ru", "https://dns.astracat.ru/dns-query"),
        Triple("Dagami DNS", "dns.dagami.ru", "https://dns.dagami.ru/dns-query"),
        Triple("DNS-AI", "dns.dns-ai.ru", "https://dns.dns-ai.ru/dns-query"),
        Triple("WL RUS DNS", "dns.wlrus.cfd", "https://dns.wlrus.cfd/dns-query"),
        Triple("Mafioznik DNS", "dns.mafioznik.xyz", "https://dns.mafioznik.xyz/dns-query"),
        Triple("Comss DNS", "dns.comss.one", "https://dns.comss.one/dns-query"),
        Triple("Comss Router", "router.comss.one", "https://router.comss.one/dns-query"),
        Triple("Malw DNS", "dns.malw.link", "https://dns.malw.link/dns-query"),
        Triple("Xbox DNS", "xbox-dns.ru", "https://xbox-dns.ru/dns-query"),
        Triple("GeminiDNS", "geminidns.site", "https://geminidns.site/dns-query")
    )

    private val GOOGLE_DIRECT_PREFIXES = listOf(
        "142.250.", "142.251.", "172.217.", "172.253.",
        "216.58.", "216.239.", "74.125.", "108.177.",
        "64.233.", "209.85."
    )

    private val CLOUDFLARE_DIRECT_PREFIXES = listOf(
        "104.16.", "104.17.", "104.18.", "104.19.",
        "104.20.", "104.21.", "104.22.", "172.64.",
        "172.65.", "172.66.", "172.67.", "162.159."
    )

    private val BOGON_PREFIXES = listOf("0.", "127.", "10.", "192.168.", "169.254.")
    private val geoCache = ConcurrentHashMap<String, Pair<String, String>>()
    private val random = SecureRandom()

    fun isValidIpv4(text: String): Boolean {
        val parts = text.trim().split(".")
        if (parts.size != 4) return false
        for (p in parts) {
            val n = p.toIntOrNull() ?: return false
            if (n < 0 || n > 255) return false
        }
        return true
    }

    fun isDirectGoogle(ip: String): Boolean = GOOGLE_DIRECT_PREFIXES.any { ip.startsWith(it) }
    fun isDirectCloudflare(ip: String): Boolean = CLOUDFLARE_DIRECT_PREFIXES.any { ip.startsWith(it) }
    fun isBogon(ip: String): Boolean = BOGON_PREFIXES.any { ip.startsWith(it) }

    fun parseDnsLines(rawText: String): MutableList<DnsEntry> {
        val entries = mutableListOf<DnsEntry>()
        val lines = rawText.lines()
        for ((idx, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val parts = line.split("|").map { it.trim() }
            var name: String
            val targetPart: String
            var dohUrl = ""

            if (parts.size >= 3) {
                name = parts[0]
                targetPart = parts[1]
                dohUrl = parts[2]
            } else if (parts.size == 2) {
                name = parts[0]
                targetPart = parts[1]
            } else {
                targetPart = parts[0]
                name = "DNS Сервер #${idx + 1}"
            }

            val targets = targetPart.replace(";", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val validTargets = mutableListOf<String>()
            val initialIps = mutableListOf<String>()

            for (t in targets) {
                if (t.startsWith("https://") && t.contains("/dns-query")) {
                    dohUrl = t
                    val host = t.removePrefix("https://").substringBefore("/").trim()
                    validTargets.add(host)
                    continue
                }
                val clean = t.removePrefix("https://").removePrefix("http://").substringBefore("/").trim()
                if (isValidIpv4(clean)) {
                    validTargets.add(clean)
                    initialIps.add(clean)
                } else if (clean.contains(".") && !clean.contains(" ")) {
                    validTargets.add(clean)
                    if (dohUrl.isEmpty()) {
                        dohUrl = "https://$clean/dns-query"
                    }
                }
            }

            if (validTargets.isNotEmpty()) {
                if (name.startsWith("DNS Сервер #")) {
                    name = "DNS (${validTargets[0]})"
                }
                entries.add(
                    DnsEntry(
                        name = name,
                        rawTargets = validTargets,
                        ips = initialIps,
                        dohUrl = dohUrl
                    )
                )
            }
        }
        return entries
    }

    fun buildDnsQuery(domain: String, customTid: Int? = null): Pair<ByteArray, Int> {
        val tid = customTid ?: (random.nextInt(65535) and 0xFFFF)
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(12)
        header.putShort(tid.toShort())
        header.putShort(0x0100.toShort())
        header.putShort(1.toShort())
        header.putShort(0.toShort())
        header.putShort(0.toShort())
        header.putShort(0.toShort())
        out.write(header.array())

        for (part in domain.trim('.').split(".")) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)

        val qTail = ByteBuffer.allocate(4)
        qTail.putShort(1.toShort()) // A
        qTail.putShort(1.toShort()) // IN
        out.write(qTail.array())
        return Pair(out.toByteArray(), tid)
    }

    private fun skipDnsName(packet: ByteArray, startOffset: Int): Int {
        var offset = startOffset
        while (offset < packet.size) {
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) return offset + 1
            if ((len and 0xC0) == 0xC0) return offset + 2
            offset += 1 + len
        }
        return offset
    }

    fun parseDnsResponse(data: ByteArray, expectedTid: Int? = null): List<String> {
        if (data.size < 12) return emptyList()
        val buf = ByteBuffer.wrap(data)
        val rTid = buf.short.toInt() and 0xFFFF
        val flags = buf.short.toInt() and 0xFFFF
        val qdCount = buf.short.toInt() and 0xFFFF
        val anCount = buf.short.toInt() and 0xFFFF

        if (expectedTid != null && rTid != expectedTid) return emptyList()
        if ((flags and 0x000F) != 0 || anCount == 0) return emptyList()

        var offset = 12
        for (i in 0 until qdCount) {
            offset = skipDnsName(data, offset) + 4
        }

        val ips = mutableListOf<String>()
        for (i in 0 until anCount) {
            offset = skipDnsName(data, offset)
            if (offset + 10 > data.size) break
            val rType = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val rdLength = ((data[offset + 8].toInt() and 0xFF) shl 8) or (data[offset + 9].toInt() and 0xFF)
            offset += 10
            if (rType == 1 && rdLength == 4 && offset + 4 <= data.size) {
                val ip = "${data[offset].toInt() and 0xFF}.${data[offset + 1].toInt() and 0xFF}.${data[offset + 2].toInt() and 0xFF}.${data[offset + 3].toInt() and 0xFF}"
                ips.add(ip)
            }
            offset += rdLength
        }
        return ips
    }

    fun queryDnsUdpTcp(dnsIp: String, domain: String = "gemini.google.com", timeoutMs: Int = 1800): Pair<List<String>, Int> {
        val (packet, tid) = buildDnsQuery(domain)
        val start = System.currentTimeMillis()

        // 1. UDP 53
        var udpSocket: DatagramSocket? = null
        try {
            udpSocket = DatagramSocket()
            udpSocket.soTimeout = timeoutMs
            val addr = InetAddress.getByName(dnsIp)
            val sendPkt = DatagramPacket(packet, packet.size, addr, 53)
            udpSocket.send(sendPkt)

            val recvBuf = ByteArray(1024)
            val recvPkt = DatagramPacket(recvBuf, recvBuf.size)
            udpSocket.receive(recvPkt)
            val elapsed = (System.currentTimeMillis() - start).toInt()
            val actualData = recvBuf.copyOfRange(0, recvPkt.length)
            val ips = parseDnsResponse(actualData, tid)
            if (ips.isNotEmpty()) return Pair(ips, elapsed)
        } catch (_: Exception) {
        } finally {
            try { udpSocket?.close() } catch (_: Exception) {}
        }

        // 2. TCP 53 Fallback
        val startTcp = System.currentTimeMillis()
        var tcpSocket: Socket? = null
        try {
            tcpSocket = Socket()
            tcpSocket.connect(InetSocketAddress(dnsIp, 53), timeoutMs)
            tcpSocket.soTimeout = timeoutMs
            val out = tcpSocket.getOutputStream()
            val lenPrefix = byteArrayOf(((packet.size shr 8) and 0xFF).toByte(), (packet.size and 0xFF).toByte())
            out.write(lenPrefix)
            out.write(packet)
            out.flush()

            val inp = tcpSocket.getInputStream()
            val b1 = inp.read()
            val b2 = inp.read()
            if (b1 >= 0 && b2 >= 0) {
                val respLen = (b1 shl 8) or b2
                val data = ByteArray(respLen)
                var readTotal = 0
                while (readTotal < respLen) {
                    val r = inp.read(data, readTotal, respLen - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                val elapsed = (System.currentTimeMillis() - startTcp).toInt()
                val ips = parseDnsResponse(data, tid)
                if (ips.isNotEmpty()) return Pair(ips, elapsed)
            }
        } catch (_: Exception) {
        } finally {
            try { tcpSocket?.close() } catch (_: Exception) {}
        }

        return Pair(emptyList(), 9999)
    }

    fun queryDnsDohRaw(dohUrl: String, rawQueryPacket: ByteArray, timeoutMs: Int = 2800): ByteArray? {
        if (dohUrl.isEmpty() || rawQueryPacket.size < 12) return null
        val origTid0 = rawQueryPacket[0]
        val origTid1 = rawQueryPacket[1]
        val copy = rawQueryPacket.copyOf()
        copy[0] = 0
        copy[1] = 0
        val b64 = Base64.encodeToString(copy, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        var conn: HttpURLConnection? = null
        try {
            val url = URL("$dohUrl?dns=$b64")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode == 200) {
                val bytes = conn.inputStream.readBytes()
                if (bytes.size >= 12) {
                    bytes[0] = origTid0
                    bytes[1] = origTid1
                    return bytes
                }
            }
        } catch (_: Exception) {
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
        return null
    }

    fun queryDnsDoh(dohUrl: String, domain: String = "gemini.google.com", timeoutMs: Int = 2800): Pair<List<String>, Int> {
        if (dohUrl.isEmpty()) return Pair(emptyList(), 9999)
        val (packet, tid) = buildDnsQuery(domain, 0)
        val start = System.currentTimeMillis()
        val resp = queryDnsDohRaw(dohUrl, packet, timeoutMs) ?: return Pair(emptyList(), 9999)
        val elapsed = (System.currentTimeMillis() - start).toInt()
        val ips = parseDnsResponse(resp, tid)
        return if (ips.isNotEmpty()) Pair(ips, elapsed) else Pair(emptyList(), 9999)
    }

    fun resolveHostAllIps(host: String): List<String> {
        if (isValidIpv4(host)) return listOf(host)
        val found = mutableListOf<String>()
        for (resolver in listOf("77.88.8.8", "1.1.1.1", "8.8.8.8")) {
            val (ips, _) = queryDnsUdpTcp(resolver, host, 1400)
            for (ip in ips) {
                if (!found.contains(ip) && !isBogon(ip)) {
                    found.add(ip)
                }
            }
        }
        if (found.isEmpty()) {
            try {
                val addrs = InetAddress.getAllByName(host)
                for (a in addrs) {
                    val ip = a.hostAddress ?: continue
                    if (isValidIpv4(ip) && !found.contains(ip)) found.add(ip)
                }
            } catch (_: Exception) {}
        }
        return found
    }

    fun getIpGeo(ip: String?): Pair<String, String> {
        if (ip.isNullOrEmpty()) return Pair("EU", "Smart Proxy")
        geoCache[ip]?.let { return it }
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://ip-api.com/json/$ip?fields=status,countryCode,country,city&lang=ru")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1800
            conn.readTimeout = 1800
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                if (json.optString("status") == "success") {
                    val cc = json.optString("countryCode", "EU")
                    val country = json.optString("country", "Европа")
                    val city = json.optString("city", "")
                    val loc = if (city.isNotEmpty()) "$country, $city" else country
                    val pair = Pair(cc, loc)
                    geoCache[ip] = pair
                    return pair
                }
            }
        } catch (_: Exception) {
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
        val fallback = Pair("EU", "Smart Proxy")
        geoCache[ip] = fallback
        return fallback
    }

    private fun createInsecureSslContext(): SSLContext {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        return ctx
    }

    private data class HttpSniResult(
        val firstLine: String,
        val location: String,
        val body: String,
        val elapsedMs: Int
    )

    private fun httpGetOverSni(proxyIp: String, host: String, path: String = "/", timeoutMs: Int = 3500): HttpSniResult {
        val start = System.currentTimeMillis()
        val rawSocket = Socket()
        val outBuf = ByteArrayOutputStream()

        try {
            rawSocket.connect(InetSocketAddress(proxyIp, 443), timeoutMs)
            rawSocket.soTimeout = timeoutMs

            val sslCtx = createInsecureSslContext()
            val sslSocket = sslCtx.socketFactory.createSocket(rawSocket, host, 443, true) as SSLSocket
            sslSocket.soTimeout = timeoutMs

            val params: SSLParameters = sslSocket.sslParameters
            params.serverNames = listOf(SNIHostName(host))
            sslSocket.sslParameters = params

            sslSocket.startHandshake()

            val req = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $host\r\n")
                append("User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36\r\n")
                append("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n")
                append("Accept-Language: ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7\r\n")
                append("Cookie: SOCS=CAISHAgBEhJnd3NfMjAyNDAxMDEtMF9SQzIaAmVuIAEaBgiA_LyuBg; CONSENT=YES+cb\r\n")
                append("Connection: close\r\n\r\n")
            }

            val out = sslSocket.outputStream
            out.write(req.toByteArray(Charsets.UTF_8))
            out.flush()

            val inp: InputStream = sslSocket.inputStream
            val buf = ByteArray(4096)
            var headersReceived = false

            while (outBuf.size() < 16384) {
                try {
                    val r = inp.read(buf)
                    if (r <= 0) break
                    outBuf.write(buf, 0, r)

                    val currentStr = outBuf.toString("UTF-8")
                    if (!headersReceived && currentStr.contains("\r\n\r\n")) {
                        headersReceived = true
                        val hdrPart = currentStr.substringBefore("\r\n\r\n")
                        val bodyPart = currentStr.substringAfter("\r\n\r\n", "")
                        val firstLine = hdrPart.lineSequence().firstOrNull() ?: ""
                        if (listOf("301", "302", "303", "307", "308").any { firstLine.contains(it) }) {
                            break
                        }
                        if (bodyPart.length >= 2048) {
                            break
                        }
                        sslSocket.soTimeout = 800
                    }
                } catch (_: Exception) {
                    break
                }
            }
            try { sslSocket.close() } catch (_: Exception) {}
        } finally {
            try { rawSocket.close() } catch (_: Exception) {}
        }

        val elapsed = (System.currentTimeMillis() - start).toInt()
        val rawText = outBuf.toString("UTF-8")
        if (rawText.isEmpty()) throw Exception("Empty response")

        val headers = rawText.substringBefore("\r\n\r\n")
        val body = rawText.substringAfter("\r\n\r\n", "")
        val firstLine = headers.lineSequence().firstOrNull() ?: ""
        var location = ""
        for (line in headers.lines()) {
            if (line.lowercase().startsWith("location:")) {
                location = line.substringAfter(":").trim()
                break
            }
        }
        return HttpSniResult(firstLine, location, body, elapsed)
    }

    fun verifyGeminiViaProxy(proxyIp: String?): Triple<Boolean, Int, String> {
        if (proxyIp.isNullOrEmpty() || isDirectGoogle(proxyIp) || isBogon(proxyIp)) {
            return Triple(false, 9999, "Прямой IP Google (не обходит блок РФ)")
        }

        val blockMarkers = listOf("/faq", "b/0/faq", "unsupported", "support.google.com", "sorry/index")

        try {
            val res1 = httpGetOverSni(proxyIp, "gemini.google.com", "/app", 3500)
            val locLow = res1.location.lowercase()
            val bodyLow = res1.body.lowercase()

            if (blockMarkers.any { locLow.contains(it) }) {
                return Triple(false, 9999, "Блок региона РФ (редирект на FAQ)")
            }

            if (listOf("400", "403", "404", "429", "451", "500", "502", "503", "504").any { res1.firstLine.contains(it) }) {
                return Triple(false, 9999, "Отказ прокси (${res1.firstLine.take(15)})")
            }

            if (res1.firstLine.contains("200")) {
                if (listOf(
                        "isn't currently supported in your country",
                        "not currently supported in your country",
                        "не поддерживается в вашей стране",
                        "unsupported-country"
                    ).any { bodyLow.contains(it) }
                ) {
                    return Triple(false, 9999, "Страница гео-блокировки")
                }
                return Triple(true, res1.elapsedMs, "Gemini доступен")
            }

            if (listOf("301", "302", "303", "307", "308").any { res1.firstLine.contains(it) }) {
                if (locLow.contains("accounts.google.com") || locLow.contains("consent.google.com")) {
                    return Triple(true, res1.elapsedMs, "Gemini доступен")
                }

                val res2 = httpGetOverSni(proxyIp, "gemini.google.com", "/", 3200)
                val loc2Low = res2.location.lowercase()
                val body2Low = res2.body.lowercase()

                if (blockMarkers.any { loc2Low.contains(it) }) {
                    return Triple(false, 9999, "Блок региона РФ (редирект на FAQ)")
                }

                if (listOf("200", "301", "302", "303", "307", "308").any { res2.firstLine.contains(it) }) {
                    if (!body2Low.contains("not currently supported in your country")) {
                        return Triple(true, minOf(res1.elapsedMs, res2.elapsedMs), "Gemini доступен")
                    }
                }
            }
            return Triple(false, 9999, "Неизвестный ответ прокси")
        } catch (_: Exception) {
            return Triple(false, 9999, "Прокси-узел не отвечает (порт 443)")
        }
    }

    fun verifyOtherAiViaProxy(proxyIp: String?, host: String): Boolean {
        if (proxyIp.isNullOrEmpty() || isDirectCloudflare(proxyIp) || isDirectGoogle(proxyIp) || isBogon(proxyIp)) {
            return false
        }
        return try {
            val res = httpGetOverSni(proxyIp, host, "/", 2500)
            val bodyLow = res.body.lowercase()
            val locLow = res.location.lowercase()
            if (listOf("200", "301", "302", "303", "307", "308").any { res.firstLine.contains(it) }) {
                !bodyLow.contains("unable to load site") &&
                    !bodyLow.contains("unsupported_country") &&
                    !locLow.contains("app-unavailable")
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun evaluateDnsServer(entry: DnsEntry): DnsEntry {
        val dnsIps = entry.ips.toMutableList()
        val dohUrl = entry.dohUrl

        if (dnsIps.isEmpty()) {
            for (t in entry.rawTargets) {
                for (ip in resolveHostAllIps(t)) {
                    if (!dnsIps.contains(ip)) dnsIps.add(ip)
                }
            }
        }

        data class Candidate(val proxyIp: String, val ms: Int, val isDoh: Boolean, val resolverIp: String)
        val candidates = mutableListOf<Candidate>()

        for (ip in dnsIps.take(2)) {
            val (gIps, ms) = queryDnsUdpTcp(ip, "gemini.google.com", 1600)
            if (gIps.isNotEmpty()) {
                candidates.add(Candidate(gIps[0], ms, false, ip))
            }
        }

        if (dohUrl.isNotEmpty() && (candidates.isEmpty() || dnsIps.isEmpty())) {
            val (gIps, ms) = queryDnsDoh(dohUrl, "gemini.google.com", 2600)
            if (gIps.isNotEmpty()) {
                candidates.add(Candidate(gIps[0], ms, true, ""))
            }
        }

        if (candidates.isEmpty()) {
            return entry.copy(
                ips = dnsIps.take(2),
                working = false,
                partial = false,
                geminiOk = false,
                chatgptOk = false,
                claudeOk = false,
                ping = 9999,
                aiMs = 9999,
                quality = 0,
                score = 99999,
                country = "—",
                proxyIp = null,
                usedDoh = false,
                reason = "DNS не отвечает (порт 53 / DoH недоступен)"
            )
        }

        var chosenProxy = candidates[0].proxyIp
        var dnsPing = candidates[0].ms
        var usedDoh = candidates[0].isDoh
        var activeResolver = candidates[0].resolverIp
        var geminiOk = false
        var aiMs = 9999
        var reasonMsg = ""

        val checkedProxies = mutableMapOf<String, Triple<Boolean, Int, String>>()
        for (c in candidates) {
            val res = checkedProxies.getOrPut(c.proxyIp) { verifyGeminiViaProxy(c.proxyIp) }
            if (res.first) {
                chosenProxy = c.proxyIp
                dnsPing = c.ms
                usedDoh = c.isDoh
                activeResolver = c.resolverIp
                geminiOk = true
                aiMs = res.second
                reasonMsg = res.third
                break
            } else {
                reasonMsg = res.third
            }
        }

        val cgIps: List<String>
        val clIps: List<String>
        if (usedDoh) {
            cgIps = queryDnsDoh(dohUrl, "chatgpt.com", 2000).first
            clIps = queryDnsDoh(dohUrl, "claude.ai", 2000).first
        } else {
            val resIp = if (activeResolver.isNotEmpty()) activeResolver else dnsIps.firstOrNull().orEmpty()
            cgIps = if (resIp.isNotEmpty()) queryDnsUdpTcp(resIp, "chatgpt.com", 1400).first else emptyList()
            clIps = if (resIp.isNotEmpty()) queryDnsUdpTcp(resIp, "claude.ai", 1400).first else emptyList()
        }

        val chatgptOk = if (cgIps.isNotEmpty()) verifyOtherAiViaProxy(cgIps[0], "chatgpt.com") else false
        val claudeOk = if (clIps.isNotEmpty()) verifyOtherAiViaProxy(clIps[0], "claude.ai") else false

        val aiCount = (if (geminiOk) 1 else 0) + (if (chatgptOk) 1 else 0) + (if (claudeOk) 1 else 0)
        val partial = !geminiOk && aiCount > 0

        val (cc, locName) = if (!isDirectGoogle(chosenProxy)) getIpGeo(chosenProxy) else Pair("—", "Прямой IP")

        val quality: Int
        val score: Int
        val reason: String

        if (geminiOk) {
            val baseQ = 68 + (if (chatgptOk) 14 else 0) + (if (claudeOk) 14 else 0)
            val speedBonus = maxOf(0, 4 - (dnsPing / 30))
            quality = minOf(100, baseQ + speedBonus)
            score = dnsPing + (aiMs / 4) - (aiCount * 80)
            val protoTag = if (usedDoh) "DoH" else "Smart DNS $chosenProxy"
            reason = "ИИ $aiCount/3 ($cc) • Пинг $dnsPing мс • ИИ $aiMs мс • Оценка $quality [$protoTag]"
        } else if (partial) {
            quality = 25 + (15 * aiCount)
            score = 8000 + dnsPing
            reason = "Частично ($aiCount/3 ИИ, без Gemini) • $locName"
        } else {
            quality = 0
            score = 99999
            reason = "$reasonMsg ($chosenProxy)"
        }

        return entry.copy(
            ips = dnsIps.take(2),
            working = geminiOk,
            partial = partial,
            geminiOk = geminiOk,
            chatgptOk = chatgptOk,
            claudeOk = claudeOk,
            ping = dnsPing,
            aiMs = aiMs,
            quality = quality,
            score = score,
            country = "$cc ($locName)",
            proxyIp = chosenProxy,
            usedDoh = usedDoh,
            reason = reason
        )
    }
}

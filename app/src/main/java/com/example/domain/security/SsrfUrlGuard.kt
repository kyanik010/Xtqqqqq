package com.example.domain.security

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

interface DnsResolver {
  fun resolveAll(host: String): List<InetAddress>
}

object SystemDnsResolver : DnsResolver {
  override fun resolveAll(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()
}

sealed class UrlSafetyCheckResult {
  data class Safe(val uri: URI, val resolvedAddresses: List<InetAddress>) : UrlSafetyCheckResult()

  data class Unsafe(val reasonAr: String, val code: String) : UrlSafetyCheckResult()
}

/**
 * Strict SSRF guard for URL inspection:
 * - Allows only http/https without embedded credentials.
 * - Blocks localhost, .local, .internal, loopback, private RFC1918, link-local (169.254.0.0/16),
 *   CGNAT (100.64.0.0/10), IPv6 ULA/link-local/loopback, and IPv4-mapped IPv6 addresses.
 * - Pre-resolves DNS and validates every resolved IP before any network connection or redirect hop.
 */
class SsrfUrlGuard(private val dnsResolver: DnsResolver = SystemDnsResolver) {

  companion object {
    const val MAX_REDIRECTS = 5
    const val CONNECT_TIMEOUT_MS = 3000
    const val READ_TIMEOUT_MS = 4000
    const val MAX_RESPONSE_BYTES = 131072 // 128 KB strict cap

    private val BLOCKED_HOSTNAMES =
      setOf(
        "localhost",
        "localhost.localdomain",
        "ip6-localhost",
        "ip6-loopback",
        "metadata.google.internal",
        "metadata",
      )

    private val BLOCKED_SUFFIXES = listOf(".local", ".internal", ".localhost", ".lan", ".home", ".corp")
  }

  fun validateSyntaxAndHost(rawUrl: String): UrlSafetyCheckResult {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty() || trimmed.length > 2048) {
      return UrlSafetyCheckResult.Unsafe("طول الرابط غير صالح أو فارغ.", "INVALID_LENGTH")
    }

    val uri =
      try {
        URI(trimmed)
      } catch (_: Exception) {
        return UrlSafetyCheckResult.Unsafe("صيغة الرابط غير صالحة.", "MALFORMED_URI")
      }

    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") {
      return UrlSafetyCheckResult.Unsafe("يُسمح فقط ببروتوكولات HTTP/HTTPS.", "DISALLOWED_SCHEME")
    }

    if (!uri.userInfo.isNullOrEmpty()) {
      return UrlSafetyCheckResult.Unsafe("الرابط يحتوي على بيانات اعتماد مضمنة غير مسموحة.", "EMBEDDED_CREDENTIALS")
    }

    val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
    if (host.isNullOrBlank()) {
      return UrlSafetyCheckResult.Unsafe("لا يحتوي الرابط على اسم نطاق واضح.", "MISSING_HOST")
    }

    if (host in BLOCKED_HOSTNAMES || BLOCKED_SUFFIXES.any { host.endsWith(it) }) {
      return UrlSafetyCheckResult.Unsafe("النطاق يشير إلى مضيف محلي أو شبكة داخلية محظورة.", "BLOCKED_INTERNAL_HOST")
    }

    return UrlSafetyCheckResult.Safe(uri, emptyList())
  }

  fun validateWithDns(rawUrl: String): UrlSafetyCheckResult {
    val syntaxCheck = validateSyntaxAndHost(rawUrl)
    if (syntaxCheck is UrlSafetyCheckResult.Unsafe) return syntaxCheck
    val uri = (syntaxCheck as UrlSafetyCheckResult.Safe).uri
    val host = uri.host.lowercase(Locale.ROOT).trimEnd('.')

    // If host is already an IP literal (IPv4 or bracketed IPv6)
    val strippedHost = host.removePrefix("[").removeSuffix("]")
    val addresses =
      try {
        dnsResolver.resolveAll(strippedHost)
      } catch (_: Exception) {
        return UrlSafetyCheckResult.Unsafe("تعذر حل اسم النطاق (DNS) للرابط.", "DNS_RESOLUTION_FAILED")
      }

    if (addresses.isEmpty()) {
      return UrlSafetyCheckResult.Unsafe("لم يُرجع فحص DNS أي عنوان للنطاق.", "EMPTY_DNS_RESULT")
    }

    for (addr in addresses) {
      if (isBlockedAddress(addr)) {
        return UrlSafetyCheckResult.Unsafe(
          "تم حظر الاتصال لأن النطاق يشير إلى عنوان IP داخلي أو خاص (حماية SSRF).",
          "SSRF_BLOCKED_IP",
        )
      }
    }

    return UrlSafetyCheckResult.Safe(uri, addresses)
  }

  fun isIpLiteralHost(host: String): Boolean {
    val clean = host.trim().removePrefix("[").removeSuffix("]")
    val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    return ipv4Regex.matches(clean) || clean.contains(":")
  }

  fun isBlockedAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress ||
      address.isLoopbackAddress ||
      address.isLinkLocalAddress ||
      address.isSiteLocalAddress ||
      address.isMulticastAddress
    ) {
      return true
    }

    return when (address) {
      is Inet4Address -> isBlockedIpv4Bytes(address.address)
      is Inet6Address -> {
        val bytes = address.address
        // Check IPv4-mapped IPv6 (::ffff:0:0/96)
        val isIpv4Mapped =
          bytes.size == 16 &&
            (0..9).all { bytes[it].toInt() == 0 } &&
            bytes[10] == 0xFF.toByte() &&
            bytes[11] == 0xFF.toByte()
        if (isIpv4Mapped) {
          val v4 = byteArrayOf(bytes[12], bytes[13], bytes[14], bytes[15])
          return isBlockedIpv4Bytes(v4)
        }
        val firstByte = bytes[0].toInt() and 0xFF
        val secondByte = bytes[1].toInt() and 0xFF
        // fc00::/7 Unique Local Address
        val isUla = (firstByte and 0xFE) == 0xFC
        // fe80::/10 Link-Local
        val isLinkLocal = firstByte == 0xFE && (secondByte and 0xC0) == 0x80
        // ::/128 unspecified
        val isUnspecified = bytes.all { it.toInt() == 0 }
        isUla || isLinkLocal || isUnspecified
      }
      else -> true
    }
  }

  private fun isBlockedIpv4Bytes(bytes: ByteArray): Boolean {
    if (bytes.size != 4) return true
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    return when {
      b0 == 0 -> true // 0.0.0.0/8 "this network"
      b0 == 10 -> true // 10.0.0.0/8 RFC1918
      b0 == 127 -> true // 127.0.0.0/8 loopback
      b0 == 169 && b1 == 254 -> true // 169.254.0.0/16 link-local & cloud metadata
      b0 == 172 && b1 in 16..31 -> true // 172.16.0.0/12 RFC1918
      b0 == 192 && b1 == 168 -> true // 192.168.0.0/16 RFC1918
      b0 == 100 && b1 in 64..127 -> true // 100.64.0.0/10 CGNAT
      b0 == 192 && b1 == 0 && (bytes[2].toInt() and 0xFF) == 0 -> true // 192.0.0.0/24
      b0 == 192 && b1 == 0 && (bytes[2].toInt() and 0xFF) == 2 -> true // TEST-NET-1
      b0 == 198 && b1 in 18..19 -> true // 198.18.0.0/15 benchmarking
      b0 >= 224 -> true // Multicast / reserved
      else -> false
    }
  }
}

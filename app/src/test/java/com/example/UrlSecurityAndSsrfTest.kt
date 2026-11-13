package com.example

import com.example.domain.engines.RedirectTraceOutcome
import com.example.domain.engines.UrlIntelligenceEngine
import com.example.domain.engines.UrlRedirectTracer
import com.example.domain.model.AnalysisConfig
import com.example.domain.model.RiskLevel
import com.example.domain.model.UrlCheckStatus
import com.example.domain.security.DnsResolver
import com.example.domain.security.SsrfUrlGuard
import com.example.domain.security.UrlSafetyCheckResult
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSecurityAndSsrfTest {

  private fun fakeDns(map: Map<String, List<String>>): DnsResolver =
    object : DnsResolver {
      override fun resolveAll(host: String): List<InetAddress> =
        (map[host] ?: emptyList()).map { InetAddress.getByName(it) }
    }

  @Test
  fun `blocks localhost private networks link-local metadata and IPv6 loopback`() {
    val guard =
      SsrfUrlGuard(
        fakeDns(
          mapOf(
            "127.0.0.1" to listOf("127.0.0.1"),
            "10.0.0.5" to listOf("10.0.0.5"),
            "172.16.5.4" to listOf("172.16.5.4"),
            "192.168.1.10" to listOf("192.168.1.10"),
            "169.254.169.254" to listOf("169.254.169.254"),
            "::1" to listOf("::1"),
            "evil-rebind.example" to listOf("10.10.0.8"),
          )
        )
      )

    val blockedUrls =
      listOf(
        "http://localhost/admin",
        "http://127.0.0.1:8080/health",
        "http://10.0.0.5/internal",
        "http://172.16.5.4/",
        "http://192.168.1.10/router",
        "http://169.254.169.254/latest/meta-data/",
        "http://[::1]/",
        "http://evil-rebind.example/test",
        "file:///etc/passwd",
        "javascript:alert(1)",
        "https://user:pass@example.com/",
      )

    for (url in blockedUrls) {
      val res = guard.validateWithDns(url)
      assertTrue("Expected SSRF/security block for $url, got $res", res is UrlSafetyCheckResult.Unsafe)
    }
  }

  @Test
  fun `allows public routable IP resolution`() {
    val guard = SsrfUrlGuard(fakeDns(mapOf("example.org" to listOf("93.184.216.34"))))
    val res = guard.validateWithDns("https://example.org/guide")
    assertTrue(res is UrlSafetyCheckResult.Safe)
  }

  @Test
  fun `marks uninspected urls honestly when live inspection is disabled or fails`() {
    val failingTracer =
      object : UrlRedirectTracer {
        override fun trace(url: String) =
          RedirectTraceOutcome(inspected = false, blockedBySsrf = false, finalUrl = null, redirectCount = 0, errorNoteAr = "انتهاء المهلة")
      }
    val engine = UrlIntelligenceEngine(redirectTracer = failingTracer)

    val offlineOut = engine.runEngine(listOf("راجع الدليل الرسمي https://example.org/docs"), AnalysisConfig(enableLiveUrlInspection = false))
    assertEquals(UrlCheckStatus.UNINSPECTED, offlineOut.summaries.single().status)

    val timeoutOut = engine.runEngine(listOf("راجع الدليل الرسمي https://example.org/docs"), AnalysisConfig(enableLiveUrlInspection = true))
    assertEquals(UrlCheckStatus.UNINSPECTED, timeoutOut.summaries.single().status)
  }

  @Test
  fun `detects shorteners typosquatting lookalikes and duplicate urls`() {
    val engine = UrlIntelligenceEngine()
    val out =
      engine.runEngine(
        listOf(
          "تحقق من https://twiter.com/verify-account أو https://bit.ly/abc",
          "نفس الرابط المختصر https://bit.ly/abc",
        )
      )
    assertTrue(out.summaries.any { it.isShortener })
    assertTrue(out.engineResult.findings.any { it.category == "انتحال نطاقات" })
    assertTrue(out.engineResult.findings.any { it.category == "تكرار الروابط" })
    assertTrue(out.engineResult.riskLevel.weightOrder >= RiskLevel.HIGH.weightOrder)
  }
}

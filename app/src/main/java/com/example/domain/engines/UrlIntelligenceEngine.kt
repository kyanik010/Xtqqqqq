package com.example.domain.engines

import com.example.domain.model.AnalysisConfig
import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.EvidenceLocation
import com.example.domain.model.Finding
import com.example.domain.model.RiskLevel
import com.example.domain.model.UrlCheckStatus
import com.example.domain.model.UrlInspectionSummary
import com.example.domain.security.SsrfUrlGuard
import com.example.domain.security.UnsafeDnsResolutionException
import com.example.domain.security.UrlSafetyCheckResult
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

data class RedirectTraceOutcome(
  val inspected: Boolean,
  val blockedBySsrf: Boolean,
  val finalUrl: String?,
  val redirectCount: Int,
  val errorNoteAr: String? = null,
)

interface UrlRedirectTracer {
  fun trace(url: String): RedirectTraceOutcome
}

class SafeHttpRedirectTracer(
  private val ssrfGuard: SsrfUrlGuard = SsrfUrlGuard(),
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      // Resolve and validate DNS at the socket-connection boundary, then return those exact
      // addresses to OkHttp. This pins the connection to the checked IPs and blocks DNS rebinding.
      .dns(object : Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> =
          ssrfGuard.resolveSafeAddresses(hostname)
      })
      .connectTimeout(SsrfUrlGuard.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
      .readTimeout(SsrfUrlGuard.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
      .build(),
) : UrlRedirectTracer {

  override fun trace(url: String): RedirectTraceOutcome {
    var currentUrl = url
    var hops = 0

    while (hops <= SsrfUrlGuard.MAX_REDIRECTS) {
      val check = ssrfGuard.validateWithDns(currentUrl)
      if (check is UrlSafetyCheckResult.Unsafe) {
        val isSsrf =
          check.code in setOf("SSRF_BLOCKED_IP", "BLOCKED_INTERNAL_HOST", "DISALLOWED_SCHEME", "EMBEDDED_CREDENTIALS")
        return RedirectTraceOutcome(
          inspected = false,
          blockedBySsrf = isSsrf,
          finalUrl = null,
          redirectCount = hops,
          errorNoteAr = check.reasonAr,
        )
      }

      val request =
        Request.Builder()
          .url(currentUrl)
          .head()
          .header("User-Agent", "ThreadGuardX-SafeInspector/1.0")
          .build()

      try {
        client.newCall(request).execute().use { response ->
          // Enforce response size cap even on non-HEAD fallback; never execute JS or download executables
          val bodyLen = response.header("Content-Length")?.toLongOrNull() ?: 0L
          if (bodyLen > SsrfUrlGuard.MAX_RESPONSE_BYTES * 8L) {
            return RedirectTraceOutcome(
              inspected = false,
              blockedBySsrf = false,
              finalUrl = currentUrl,
              redirectCount = hops,
              errorNoteAr = "حجم استجابة الخادم يتجاوز الحد المسموح للفحص الآمن.",
            )
          }

          if (response.code in 300..399) {
            val location = response.header("Location")
            if (location.isNullOrBlank()) {
              return RedirectTraceOutcome(true, false, currentUrl, hops, null)
            }
            val resolved = URI(currentUrl).resolve(location).toString()
            currentUrl = resolved
            hops++
          } else {
            return RedirectTraceOutcome(true, false, currentUrl, hops, null)
          }
        }
      } catch (e: Exception) {
        val dnsBlock =
          generateSequence<Throwable>(e) { it.cause }
            .filterIsInstance<UnsafeDnsResolutionException>()
            .any { it.code == "SSRF_BLOCKED_IP" || it.code == "BLOCKED_INTERNAL_HOST" }
        return RedirectTraceOutcome(
          inspected = false,
          blockedBySsrf = dnsBlock,
          finalUrl = null,
          redirectCount = hops,
          errorNoteAr = if (dnsBlock) {
            "حُظر الاتصال أثناء حل DNS لأن عنوان الوجهة أصبح داخلياً أو خاصاً (حماية من DNS Rebinding)."
          } else {
            "تعذر الوصول للرابط أثناء الفحص (${e.javaClass.simpleName})."
          },
        )
      }
    }

    return RedirectTraceOutcome(
      inspected = false,
      blockedBySsrf = false,
      finalUrl = currentUrl,
      redirectCount = hops,
      errorNoteAr = "تجاوز الرابط الحد الأقصى للتحويلات (${SsrfUrlGuard.MAX_REDIRECTS}).",
    )
  }
}

class UrlIntelligenceEngine(
  private val ssrfGuard: SsrfUrlGuard = SsrfUrlGuard(),
  private val redirectTracer: UrlRedirectTracer = SafeHttpRedirectTracer(ssrfGuard),
) {

  companion object {
    private val URL_EXTRACT_REGEX =
      Regex(
        """(https?://[^\s<>"'\u200B]+|www\.[^\s<>"'\u200B]+|\b(?:bit\.ly|t\.co|tinyurl\.com|is\.gd|ow\.ly|rb\.gy|cutt\.ly|shorturl\.at|goo\.gl|t\.ly)/[^\s<>"'\u200B]+)""",
        RegexOption.IGNORE_CASE,
      )

    private val SHORTENER_DOMAINS =
      setOf(
        "bit.ly", "t.co", "tinyurl.com", "is.gd", "ow.ly", "rb.gy",
        "cutt.ly", "shorturl.at", "goo.gl", "lnkd.in", "t.ly", "rebrand.ly",
      )

    private val PROTECTED_BRAND_DOMAINS =
      listOf(
        "x.com", "twitter.com", "google.com", "paypal.com", "apple.com",
        "microsoft.com", "whatsapp.com", "telegram.org", "instagram.com", "youtube.com",
      )

    private val PHISHING_KEYWORDS =
      listOf("verify-account", "login-update", "wallet-claim", "airdrop-claim", "free-nitro", "security-check", "confirm-card")

    private val SUSPICIOUS_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "zip", "mov", "xyz", "top", "click")
  }

  data class UrlEngineOutput(
    val engineResult: EngineResult,
    val summaries: List<UrlInspectionSummary>,
  )

  fun extractUrls(text: String): List<Pair<String, IntRange>> {
    return URL_EXTRACT_REGEX.findAll(text).map { match ->
      val cleaned = match.value.trimEnd('.', ',', '،', '؛', '!', '?', ')', ']', '"', '\'')
      cleaned to (match.range.first until (match.range.first + cleaned.length))
    }.toList()
  }

  fun normalizeUrl(raw: String): String {
    return if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
      raw
    } else {
      "https://$raw"
    }
  }

  fun levenshtein(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      var prev = dp[0]
      dp[0] = i
      for (j in 1..b.length) {
        val temp = dp[j]
        dp[j] =
          if (a[i - 1] == b[j - 1]) prev
          else 1 + minOf(prev, dp[j], dp[j - 1])
        prev = temp
      }
    }
    return dp[b.length]
  }

  fun findLookalikeTarget(domain: String): String? {
    val cleanDomain = domain.removePrefix("www.").lowercase(Locale.ROOT)
    if (cleanDomain in PROTECTED_BRAND_DOMAINS) return null
    val parts = cleanDomain.split('.')
    if (parts.size < 2) return null
    val registrable = parts.takeLast(2).joinToString(".")
    if (registrable in PROTECTED_BRAND_DOMAINS) return null
    return PROTECTED_BRAND_DOMAINS.firstOrNull { brand ->
      val dist = levenshtein(registrable, brand)
      dist in 1..2 && registrable.length >= 4
    }
  }

  fun runEngine(tweets: List<String>, config: AnalysisConfig = AnalysisConfig()): UrlEngineOutput {
    val start = System.currentTimeMillis()
    return try {
      val findings = mutableListOf<Finding>()
      val summaries = mutableListOf<UrlInspectionSummary>()
      val seenUrls = mutableMapOf<String, Int>()
      var totalLinks = 0

      tweets.forEachIndexed { idx, rawTweet ->
        if (rawTweet.isBlank()) return@forEachIndexed
        val extracted = extractUrls(rawTweet)
        totalLinks += extracted.size

        for ((rawUrl, range) in extracted) {
          val normalized = normalizeUrl(rawUrl)
          val syntaxCheck = ssrfGuard.validateSyntaxAndHost(normalized)
          val riskNotes = mutableListOf<String>()

          if (syntaxCheck is UrlSafetyCheckResult.Unsafe) {
            val isSsrf =
              syntaxCheck.code in setOf("BLOCKED_INTERNAL_HOST", "DISALLOWED_SCHEME", "EMBEDDED_CREDENTIALS")
            riskNotes.add(syntaxCheck.reasonAr)
            summaries.add(
              UrlInspectionSummary(
                rawUrl = rawUrl,
                normalizedUrl = normalized,
                finalUrl = null,
                finalDomain = "غير آمن / محظور",
                tweetIndex = idx,
                isShortener = false,
                redirectCount = 0,
                status = if (isSsrf) UrlCheckStatus.BLOCKED_SSRF else UrlCheckStatus.SUSPICIOUS,
                riskNotesAr = riskNotes,
              )
            )
            findings.add(
              Finding(
                id = "url_unsafe_${idx}_${range.first}",
                dedupKey = "URL_SSRF_OR_INVALID:$idx:$normalized",
                engineId = EngineId.URL_INTELLIGENCE,
                category = "أمان الروابط",
                titleAr = "رابط غير صالح أو محظور أمنياً في التغريدة ${idx + 1}",
                explanationAr = "${syntaxCheck.reasonAr} ($rawUrl).",
                suggestionAr = "أزل الرابط أو استبدله برابط HTTPS عام وموثوق.",
                severity = if (isSsrf) RiskLevel.CRITICAL else RiskLevel.HIGH,
                confidence = 0.96,
                location = EvidenceLocation(tweetIndex = idx, startOffset = range.first, endOffset = range.last + 1, snippet = rawUrl),
              )
            )
            continue
          }

          val uri = (syntaxCheck as UrlSafetyCheckResult.Safe).uri
          val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.")
          val isShortener = host in SHORTENER_DOMAINS
          val isIpHost = ssrfGuard.isIpLiteralHost(host)
          val hasPunycode = host.contains("xn--")
          val lookalike = findLookalikeTarget(host)
          val tld = host.substringAfterLast('.', "")
          val lowerFull = normalized.lowercase(Locale.ROOT)
          val phishingToken = PHISHING_KEYWORDS.firstOrNull { lowerFull.contains(it) }

          if (isShortener) {
            riskNotes.add("رابط مختصر يخفي النطاق النهائي.")
            findings.add(
              Finding(
                id = "url_short_${idx}_${range.first}",
                dedupKey = "URL_SHORTENER:$idx:$normalized",
                engineId = EngineId.URL_INTELLIGENCE,
                category = "روابط مختصرة",
                titleAr = "رابط مختصر في التغريدة ${idx + 1} ($host)",
                explanationAr = "الروابط المختصرة مثل $rawUrl تخفي الوجهة النهائية عن القارئ وقد تقلل الثقة.",
                suggestionAr = "استخدم الرابط الكامل المباشر بدلاً من الرابط المختصر كلما أمكن.",
                severity = RiskLevel.LOW,
                confidence = 0.92,
                location = EvidenceLocation(tweetIndex = idx, startOffset = range.first, endOffset = range.last + 1, snippet = rawUrl),
              )
            )
          }

          if (isIpHost) {
            riskNotes.add("الرابط يستخدم عنوان IP مباشراً بدلاً من اسم نطاق.")
            findings.add(
              Finding(
                id = "url_ip_${idx}_${range.first}",
                dedupKey = "URL_IP_LITERAL:$idx:$normalized",
                engineId = EngineId.URL_INTELLIGENCE,
                category = "عنوان IP مباشر",
                titleAr = "رابط يشير لعنوان IP مباشر في التغريدة ${idx + 1}",
                explanationAr = "استخدام عنوان IP خام في الروابط ($rawUrl) يعد مؤشراً غير معتاد وقد يرتبط بالتصيد أو الشبكات الداخلية.",
                suggestionAr = "استخدم اسم نطاق رسمي مزود بشهادة HTTPS.",
                severity = RiskLevel.HIGH,
                confidence = 0.90,
                location = EvidenceLocation(tweetIndex = idx, startOffset = range.first, endOffset = range.last + 1, snippet = rawUrl),
              )
            )
          }

          if (lookalike != null) {
            riskNotes.add("نطاق متشابه بصرياً مع ($lookalike) — احتمال انتحال.")
            findings.add(
              Finding(
                id = "url_lookalike_${idx}_${range.first}",
                dedupKey = "URL_LOOKALIKE:$idx:$host",
                engineId = EngineId.URL_INTELLIGENCE,
                category = "انتحال نطاقات",
                titleAr = "نطاق شبيه بنطاق معروف ($lookalike) في التغريدة ${idx + 1}",
                explanationAr = "النطاق «$host» قريب إملائياً من «$lookalike»، ما قد يدل على رابط تصيد أو انتحال.",
                suggestionAr = "تحقق من تهجئة النطاق بعناية قبل نشره.",
                severity = RiskLevel.HIGH,
                confidence = 0.89,
                location = EvidenceLocation(tweetIndex = idx, startOffset = range.first, endOffset = range.last + 1, snippet = rawUrl),
              )
            )
          }

          if (hasPunycode || phishingToken != null || (tld in SUSPICIOUS_TLDS && isShortener.not())) {
            val detail =
              listOfNotNull(
                  if (hasPunycode) "ترميز Punycode (xn--)" else null,
                  phishingToken?.let { "مسار مريب ($it)" },
                  if (tld in SUSPICIOUS_TLDS) "امتداد نطاق عالي الضجيج (.$tld)" else null,
                )
                .joinToString("، ")
            riskNotes.add("مؤشر تصيد أو اشتباه: $detail")
            findings.add(
              Finding(
                id = "url_phish_${idx}_${range.first}",
                dedupKey = "URL_PHISHING_HEURISTIC:$idx:$normalized",
                engineId = EngineId.URL_INTELLIGENCE,
                category = "مؤشرات التصيد",
                titleAr = "مؤشر اشتباه في بنية الرابط بالتغريدة ${idx + 1}",
                explanationAr = "الرابط $rawUrl يحمل مؤشرات تستحق المراجعة ($detail).",
                suggestionAr = "تأكد من موثوقية النطاق وأوضح غرض الرابط للقارئ.",
                severity = if (phishingToken != null || hasPunycode) RiskLevel.HIGH else RiskLevel.MEDIUM,
                confidence = 0.81,
                location = EvidenceLocation(tweetIndex = idx, startOffset = range.first, endOffset = range.last + 1, snippet = rawUrl),
              )
            )
          }

          val prevIdx = seenUrls.putIfAbsent(normalized.lowercase(Locale.ROOT), idx)
          if (prevIdx != null && prevIdx != idx) {
            riskNotes.add("رابط مكرر سبق ذكره في التغريدة ${prevIdx + 1}.")
            findings.add(
              Finding(
                id = "url_dup_${prevIdx}_$idx",
                dedupKey = "DUPLICATE_URL:${minOf(prevIdx, idx)}:${maxOf(prevIdx, idx)}:$normalized",
                engineId = EngineId.URL_INTELLIGENCE,
                category = "تكرار الروابط",
                titleAr = "تكرار الرابط نفسه في التغريدة ${idx + 1} والتغريدة ${prevIdx + 1}",
                explanationAr = "الرابط $rawUrl مكرر عبر عدة تغريدات في الثريد نفسه.",
                suggestionAr = "اذكر الرابط مرة واحدة في التغريدة الأنسب بدلاً من تكراره.",
                severity = RiskLevel.MEDIUM,
                confidence = 0.93,
                location = EvidenceLocation(tweetIndex = idx, startOffset = range.first, endOffset = range.last + 1, snippet = rawUrl),
              )
            )
          }

          // Optional live SSRF-guarded inspection (never auto-executes JS, never assumes safe if uninspected!)
          var finalUrl: String? = null
          var finalDomain = host
          var redirectCount = 0
          var status: UrlCheckStatus

          if (config.enableLiveUrlInspection) {
            val trace = redirectTracer.trace(normalized)
            redirectCount = trace.redirectCount
            finalUrl = trace.finalUrl
            if (trace.finalUrl != null) {
              runCatching { URI(trace.finalUrl).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull()?.let {
                finalDomain = it
              }
            }
            status =
              when {
                trace.blockedBySsrf -> {
                  riskNotes.add(trace.errorNoteAr ?: "حُظر التحويل الأمني (SSRF).")
                  findings.add(
                    Finding(
                      id = "url_ssrf_redir_${idx}_${range.first}",
                      dedupKey = "URL_SSRF_REDIRECT:$idx:$normalized",
                      engineId = EngineId.URL_INTELLIGENCE,
                      category = "تحويل محظور أمنياً",
                      titleAr = "تحويل الرابط إلى عنوان داخلي محظور في التغريدة ${idx + 1}",
                      explanationAr = trace.errorNoteAr ?: "حُظر فحص التحويل لمنع الوصول لعناوين داخلية.",
                      suggestionAr = "احذف هذا الرابط فوراً ولا تشاركه.",
                      severity = RiskLevel.CRITICAL,
                      confidence = 0.95,
                      location = EvidenceLocation(tweetIndex = idx, startOffset = range.first, endOffset = range.last + 1, snippet = rawUrl),
                    )
                  )
                  UrlCheckStatus.BLOCKED_SSRF
                }
                !trace.inspected -> {
                  riskNotes.add(trace.errorNoteAr ?: "تعذر فحص الوجهة النهائية للرابط؛ الحالة غير مفحوصة.")
                  UrlCheckStatus.UNINSPECTED
                }
                lookalike != null || isIpHost || phishingToken != null -> UrlCheckStatus.UNSAFE_PATTERN
                isShortener || riskNotes.isNotEmpty() -> UrlCheckStatus.SUSPICIOUS
                else -> UrlCheckStatus.INSPECTED_OK
              }
          } else {
            // Live check disabled: ALWAYS mark as UNINSPECTED (never claim verified safe without inspection!)
            status =
              when {
                lookalike != null || isIpHost || phishingToken != null -> UrlCheckStatus.UNSAFE_PATTERN
                isShortener || riskNotes.isNotEmpty() -> UrlCheckStatus.SUSPICIOUS
                else -> UrlCheckStatus.UNINSPECTED
              }
            if (status == UrlCheckStatus.UNINSPECTED) {
              riskNotes.add("لم يُفحص الاتصال المباشر (الفحص المباشر للروابط معطّل في الإعدادات).")
            }
          }

          summaries.add(
            UrlInspectionSummary(
              rawUrl = rawUrl,
              normalizedUrl = normalized,
              finalUrl = finalUrl,
              finalDomain = finalDomain,
              tweetIndex = idx,
              isShortener = isShortener,
              redirectCount = redirectCount,
              status = status,
              riskNotesAr = riskNotes,
            )
          )
        }
      }

      // Thread-wide link density check
      val nonBlankCount = tweets.count { it.isNotBlank() }.coerceAtLeast(1)
      if (totalLinks >= 4 && totalLinks.toDouble() / nonBlankCount > 0.75) {
        findings.add(
          Finding(
            id = "url_thread_density",
            dedupKey = "THREAD_LINK_DENSITY",
            engineId = EngineId.URL_INTELLIGENCE,
            category = "كثافة الروابط بالثريد",
            titleAr = "نسبة روابط مرتفعة مقارنة بعدد التغريدات ($totalLinks روابط في $nonBlankCount تغريدات)",
            explanationAr = "الاعتماد الكثيف على الروابط في معظم تغريدات الثريد يقلل جودة التصفح ويرفع مؤشرات الإزعاج.",
            suggestionAr = "اجعل معظم التغريدات غنية بالشرح النصي واجمع المراجع في تغريدة واحدة ختامية.",
            severity = RiskLevel.MEDIUM,
            confidence = 0.85,
            location = EvidenceLocation(tweetIndex = -1, snippet = "$totalLinks روابط"),
          )
        )
      }

      val maxRisk = findings.maxByOrNull { it.severity.weightOrder }?.severity ?: RiskLevel.LOW
      val summary =
        when {
          summaries.isEmpty() -> "لا يحتوي الثريد على روابط خارجية."
          findings.isEmpty() -> "رُصد ${summaries.size} رابط(روابط) دون مؤشرات اشتباه هيكلية، مع الإشارة إلى أن الروابط غير المفحوصة مباشراً تُعرض كـ«غير مفحوص»."
          else -> "تم تحليل ${summaries.size} رابط(روابط) ورصد ${findings.size} ملاحظة تتعلق بالاختصار أو التكرار أو الأمان."
        }

      UrlEngineOutput(
        engineResult =
          EngineResult(
            engineId = EngineId.URL_INTELLIGENCE,
            nameAr = EngineId.URL_INTELLIGENCE.displayNameAr,
            status = EngineStatus.SUCCESS,
            riskLevel = maxRisk,
            confidence = if (summaries.isEmpty()) 0.90 else 0.84,
            summaryAr = summary,
            findings = findings,
            metadata =
              mapOf(
                "عدد الروابط" to summaries.size.toString(),
                "روابط مختصرة" to summaries.count { it.isShortener }.toString(),
                "روابط غير مفحوصة مباشراً" to summaries.count { it.status == UrlCheckStatus.UNINSPECTED }.toString(),
              ),
            durationMs = System.currentTimeMillis() - start,
          ),
        summaries = summaries,
      )
    } catch (e: Exception) {
      UrlEngineOutput(
        engineResult =
          EngineResult(
            engineId = EngineId.URL_INTELLIGENCE,
            nameAr = EngineId.URL_INTELLIGENCE.displayNameAr,
            status = EngineStatus.FAILED,
            riskLevel = RiskLevel.INSUFFICIENT_DATA,
            confidence = 0.0,
            summaryAr = "تعذر تشغيل محرك فحص الروابط.",
            errorMessage = e.message,
            durationMs = System.currentTimeMillis() - start,
          ),
        summaries = emptyList(),
      )
    }
  }
}

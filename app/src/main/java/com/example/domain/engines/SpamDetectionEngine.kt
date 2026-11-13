package com.example.domain.engines

import com.example.domain.model.AnalysisConfig
import com.example.domain.model.ContentContext
import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.EvidenceLocation
import com.example.domain.model.Finding
import com.example.domain.model.RiskLevel

class SpamDetectionEngine(
  private val arabicEngine: ArabicTextIntelligenceEngine = ArabicTextIntelligenceEngine(),
) {

  companion object {
    private val URL_REGEX = Regex("""https?://\S+|www\.\S+|\b(?:bit\.ly|t\.co|tinyurl\.com|cutt\.ly|rb\.gy)/\S+""", RegexOption.IGNORE_CASE)

    private val TECHNICAL_MARKERS =
      setOf(
        "بروتوكول", "hls", "m3u8", "rtsp", "mpeg", "udp", "tcp", "dns", "cdn", "codec",
        "bitrate", "تقنيه", "مقارنه", "اعدادات", "تشغيل", "تشفير", "ذاكره", "تخزين", "شبكه",
        "خادم", "مشغل", "جوده", "تأخير", "كمون", "latency", "buffer", "شرح", "معماري",
        "واجهه", "api", "json", "توافق", "اداء", "استقرار", "توجيه", "نطاق", "شهاده",
      )

    private val EDUCATIONAL_MARKERS =
      setOf(
        "خطوات", "دليل", "تعلم", "كيف", "ملاحظه", "نصيحه", "توضيح", "مثال", "مفهوم",
        "الفرق", "متى", "لماذا", "مقدمه", "خلاصه", "فائده", "تجربه", "درس",
      )

    private val COMMERCIAL_NEUTRAL_MARKERS =
      setOf("اعلان", "خدمه", "منتج", "اصدار", "تحديث", "متوفر", "تفاصيل", "باقه", "سعر")

    private val CTA_PHRASES =
      listOf(
        "اشترك الان", "اشترك معنا", "سجل الان", "تواصل معنا", "راسلنا الان",
        "راسلنا خاص", "اضغط هنا", "انقر هنا", "تواصل واتساب", "رابط الاشتراك",
        "اطلب الان", "احصل عليه الان", "انضم الينا الان", "لا تفوت الفرصه",
      )

    private val HYPE_PHRASES =
      listOf(
        "حصريا", "عرض لا يعوض", "فرصه العمر", "مضمون 100", "ضمان 100",
        "ارباح مضمونه", "ثراء سريع", "مجاني بالكامل", "سارع قبل النفاذ",
        "اقوى عرض", "افضل في العالم", "بلا منازع", "مفاجاه كبرى", "عرض لفتره محدوده جدا",
      )

    private val FILLER_PHRASES =
      listOf("اقرأ حتى النهايه", "تابع الثريد", "لا تتجاهل", "شاركها مع الجميع", "ريتويت للجميع")
  }

  fun classifyThreadContext(tweets: List<String>, analyses: List<ArabicTextAnalysis>): ContentContext {
    val allNorm = analyses.joinToString(" ") { it.normalizedText }
    val allStems = analyses.flatMap { it.normalizedTokens.map(arabicEngine::softStemArabic) }.toSet()

    val techHits = TECHNICAL_MARKERS.count { marker ->
      val mNorm = arabicEngine.normalize(marker)
      allNorm.contains(mNorm) || allStems.contains(arabicEngine.softStemArabic(mNorm))
    }
    val eduHits = EDUCATIONAL_MARKERS.count { marker ->
      val mNorm = arabicEngine.normalize(marker)
      allNorm.contains(mNorm) || allStems.contains(arabicEngine.softStemArabic(mNorm))
    }
    val hypeHits = HYPE_PHRASES.count { allNorm.contains(arabicEngine.normalize(it)) }
    val ctaCountTotal =
      analyses.sumOf { a -> CTA_PHRASES.count { a.normalizedText.contains(arabicEngine.normalize(it)) } }
    val evasionCount = analyses.sumOf { it.obfuscationMatches.size }
    val commercialHits = COMMERCIAL_NEUTRAL_MARKERS.count { allNorm.contains(arabicEngine.normalize(it)) }

    return when {
      evasionCount >= 2 || (evasionCount >= 1 && (hypeHits >= 1 || ctaCountTotal >= 2)) ->
        ContentContext.EVASION_ATTEMPT
      (hypeHits >= 2 && ctaCountTotal >= 2) || ctaCountTotal >= 4 ->
        ContentContext.PROMOTIONAL_SPAM
      ctaCountTotal >= 3 || (hypeHits >= 2 && techHits < 2) ->
        ContentContext.REPETITIVE_MARKETING
      techHits >= 3 && hypeHits <= 1 && ctaCountTotal <= 1 ->
        ContentContext.TECHNICAL_EXPLANATION
      eduHits >= 3 && hypeHits == 0 && ctaCountTotal <= 1 ->
        ContentContext.EDUCATIONAL
      commercialHits >= 2 && hypeHits == 0 && ctaCountTotal <= 1 && evasionCount == 0 ->
        ContentContext.NORMAL_COMMERCIAL
      techHits >= 2 && eduHits >= 1 ->
        ContentContext.TECHNICAL_EXPLANATION
      else -> ContentContext.GENERAL
    }
  }

  fun runEngine(tweets: List<String>, config: AnalysisConfig = AnalysisConfig()): EngineResult {
    val start = System.currentTimeMillis()
    return try {
      val nonBlankTweets = tweets.mapIndexed { i, t -> i to t }.filter { it.second.isNotBlank() }
      if (nonBlankTweets.isEmpty()) {
        return EngineResult(
          engineId = EngineId.SPAM_DETECTION,
          nameAr = EngineId.SPAM_DETECTION.displayNameAr,
          status = EngineStatus.SUCCESS,
          riskLevel = RiskLevel.INSUFFICIENT_DATA,
          confidence = 0.25,
          summaryAr = "لا يوجد نص كافٍ لفحص مؤشرات السبام.",
          durationMs = System.currentTimeMillis() - start,
        )
      }

      val analyses = tweets.map { arabicEngine.analyzeTweet(it) }
      val context = classifyThreadContext(tweets, analyses)
      val findings = mutableListOf<Finding>()

      // A.1 Exact & near-duplicate tweets across the thread
      for (i in tweets.indices) {
        if (tweets[i].isBlank()) continue
        for (j in (i + 1) until tweets.size) {
          if (tweets[j].isBlank()) continue
          val sim = arabicEngine.computeSemanticSimilarity(tweets[i], tweets[j])
          val exactNorm = analyses[i].normalizedText == analyses[j].normalizedText && analyses[i].normalizedText.length >= 12
          if (exactNorm || sim >= 0.78) {
            val isExact = exactNorm || sim >= 0.96
            findings.add(
              Finding(
                id = "spam_dup_${i}_$j",
                dedupKey = "CROSS_TWEET_SIMILARITY:${minOf(i, j)}:${maxOf(i, j)}",
                engineId = EngineId.SPAM_DETECTION,
                category = "تكرار التغريدات",
                titleAr = if (isExact) "تطابق شبه كامل بين التغريدة ${j + 1} والتغريدة ${i + 1}" else "تشابه نصي مرتفع بين التغريدة ${j + 1} والتغريدة ${i + 1}",
                explanationAr =
                  "التغريدة رقم ${j + 1} شديدة التشابه مع التغريدة رقم ${i + 1} (نسبة التشابه الهيكلية ${(sim * 100).toInt()}%)، مما يرفع مؤشرات التكرار داخل الثريد.",
                suggestionAr = "ادمج الفكرة أو أعد صياغة التغريدة رقم ${j + 1} لتقديم معلومة جديدة ومستقلة.",
                severity = if (isExact) RiskLevel.HIGH else RiskLevel.MEDIUM,
                confidence = if (isExact) 0.95 else 0.84,
                location = EvidenceLocation(tweetIndex = j, snippet = tweets[j].take(80)),
              )
            )
          }
        }
      }

      // A.2 Repeated sentences inside single tweet or across thread
      val sentenceFirstSeen = mutableMapOf<String, Int>()
      analyses.forEachIndexed { idx, analysis ->
        val seenLocal = mutableSetOf<String>()
        for (sentence in analysis.sentences) {
          val normSent = arabicEngine.normalize(sentence)
          if (normSent.length < 18) continue
          if (!seenLocal.add(normSent)) {
            findings.add(
              Finding(
                id = "spam_sent_local_$idx",
                dedupKey = "INTRA_TWEET_SENT_REPEAT:$idx:$normSent",
                engineId = EngineId.SPAM_DETECTION,
                category = "تكرار داخل التغريدة",
                titleAr = "تكرار الجملة نفسها داخل التغريدة ${idx + 1}",
                explanationAr = "الجملة «${sentence.take(60)}» تكررت أكثر من مرة في التغريدة ذاتها.",
                suggestionAr = "احذف التكرار للحفاظ على سلاسة القراءة.",
                severity = RiskLevel.MEDIUM,
                confidence = 0.90,
                location = EvidenceLocation(tweetIndex = idx, snippet = sentence.take(70)),
              )
            )
          } else {
            val prevIdx = sentenceFirstSeen.putIfAbsent(normSent, idx)
            if (prevIdx != null && prevIdx != idx) {
              findings.add(
                Finding(
                  id = "spam_sent_cross_${prevIdx}_$idx",
                  dedupKey = "CROSS_SENTENCE_REPEAT:${minOf(prevIdx, idx)}:${maxOf(prevIdx, idx)}:$normSent",
                  engineId = EngineId.SPAM_DETECTION,
                  category = "تكرار العبارات",
                  titleAr = "تكرار عبارة مطابقة في التغريدة ${idx + 1} من التغريدة ${prevIdx + 1}",
                  explanationAr = "العبارة «${sentence.take(60)}» وردت سابقاً في التغريدة رقم ${prevIdx + 1}.",
                  suggestionAr = "تجنب لصق العبارة نفسها في تغريدات متتالية.",
                  severity = RiskLevel.MEDIUM,
                  confidence = 0.87,
                  location = EvidenceLocation(tweetIndex = idx, snippet = sentence.take(70)),
                )
              )
            }
          }
        }
      }

      // B.1 Repeated CTAs & Marketing Hype across thread (context-aware!)
      val ctaMatchesByTweet = mutableMapOf<Int, List<String>>()
      val hypeMatchesByTweet = mutableMapOf<Int, List<String>>()

      analyses.forEachIndexed { idx, analysis ->
        val ctas = CTA_PHRASES.filter { analysis.normalizedText.contains(arabicEngine.normalize(it)) }
        if (ctas.isNotEmpty()) ctaMatchesByTweet[idx] = ctas
        val hypes = HYPE_PHRASES.filter { analysis.normalizedText.contains(arabicEngine.normalize(it)) }
        if (hypes.isNotEmpty()) hypeMatchesByTweet[idx] = hypes
      }

      if (ctaMatchesByTweet.size >= 2 || (config.strictMarketingMode && ctaMatchesByTweet.isNotEmpty() && context == ContentContext.PROMOTIONAL_SPAM)) {
        val tweetNums = ctaMatchesByTweet.keys.sorted().map { it + 1 }.joinToString("، ")
        val firstIdx = ctaMatchesByTweet.keys.min()
        val sampleCta = ctaMatchesByTweet[firstIdx]?.firstOrNull().orEmpty()
        findings.add(
          Finding(
            id = "spam_cta_repeat",
            dedupKey = "REPEATED_CTA_THREAD",
            engineId = EngineId.SPAM_DETECTION,
            category = "تكرار دعوات الاشتراك",
            titleAr = "تكرار دعوات الاشتراك أو التواصل في ${ctaMatchesByTweet.size} تغريدات",
            explanationAr =
              "تكررت صيغ الدعوة المباشرة (مثل «$sampleCta») في التغريدات ($tweetNums). تكرار النداءات الترويجية قد يُقرأ كترويج مزعج خصوصاً إذا طغى على القيمة المعرفية.",
            suggestionAr = "اكتفِ بدعوة واحدة واضحة ومهذبة في التغريدة الختامية فقط.",
            severity = if (ctaMatchesByTweet.size >= 3) RiskLevel.HIGH else RiskLevel.MEDIUM,
            confidence = 0.86,
            location = EvidenceLocation(tweetIndex = firstIdx, snippet = sampleCta),
          )
        )
      }

      if (hypeMatchesByTweet.isNotEmpty() && context != ContentContext.OPINION_CRITICISM && context != ContentContext.WARNING_REPORTING) {
        val totalHype = hypeMatchesByTweet.values.flatten().size
        if (totalHype >= 2 || context in setOf(ContentContext.PROMOTIONAL_SPAM, ContentContext.REPETITIVE_MARKETING, ContentContext.EVASION_ATTEMPT)) {
          val firstIdx = hypeMatchesByTweet.keys.min()
          val sampleHype = hypeMatchesByTweet[firstIdx]?.firstOrNull().orEmpty()
          findings.add(
            Finding(
              id = "spam_hype_cluster",
              dedupKey = "PROMO_HYPE_CLUSTER",
              engineId = EngineId.SPAM_DETECTION,
              category = "مبالغات تسويقية",
              titleAr = "مبالغات وعبارات جذب انتباه متكررة",
              explanationAr =
                "رُصدت عبارات مبالغة تسويقية ($totalHype مواضع، مثل «$sampleHype»). الأسلوب الإعلاني المفرط يقلل ثقة القارئ ويرفع مؤشرات الترويج المزعج.",
              suggestionAr = "استبدل الوعود المطلقة بوصف واقعي ومحدد للمزايا.",
              severity = if (totalHype >= 3) RiskLevel.HIGH else RiskLevel.MEDIUM,
              confidence = 0.82,
              location = EvidenceLocation(tweetIndex = firstIdx, snippet = sampleHype),
            )
          )
        }
      }

      // B.2 Link density, emoji/symbol density, obfuscation evasion per tweet
      analyses.forEachIndexed { idx, analysis ->
        val raw = tweets[idx]
        if (raw.isBlank()) return@forEachIndexed
        val urls = URL_REGEX.findAll(raw).map { it.value }.toList()
        if (urls.size >= 3 || (urls.size >= 2 && analysis.tokens.size <= 10)) {
          findings.add(
            Finding(
              id = "spam_links_$idx",
              dedupKey = "HIGH_LINK_DENSITY:$idx",
              engineId = EngineId.SPAM_DETECTION,
              category = "كثافة الروابط",
              titleAr = "كثافة روابط مرتفعة في التغريدة ${idx + 1}",
              explanationAr = "التغريدة تحتوي على ${urls.size} روابط مع نص توضيحي محدود، وهو مؤشر شائع للرسائل المزعجة.",
              suggestionAr = "قلل الروابط إلى رابط واحد موثق وأضف شرحاً واضحاً لوجهته.",
              severity = RiskLevel.MEDIUM,
              confidence = 0.85,
              location = EvidenceLocation(tweetIndex = idx, snippet = urls.first()),
            )
          )
        }

        val tokenCount = analysis.tokens.size.coerceAtLeast(1)
        val emojiRatio = analysis.languageProfile.emojiCount.toDouble() / tokenCount
        if (analysis.languageProfile.emojiCount >= 7 || (analysis.languageProfile.emojiCount >= 4 && emojiRatio > 0.35)) {
          findings.add(
            Finding(
              id = "spam_emoji_$idx",
              dedupKey = "EXCESSIVE_SYMBOLS:$idx",
              engineId = EngineId.SPAM_DETECTION,
              category = "كثافة الرموز",
              titleAr = "كثافة مفرطة في الرموز التعبيرية بالتغريدة ${idx + 1}",
              explanationAr = "استخدام ${analysis.languageProfile.emojiCount} رموز تعبيرية في تغريدة واحدة يشتت القارئ ويشبه الأنماط الترويجية المزعجة.",
              suggestionAr = "خفف الرموز إلى 1–2 رمز دلالي عند الحاجة فقط.",
              severity = RiskLevel.LOW,
              confidence = 0.81,
              location = EvidenceLocation(tweetIndex = idx, snippet = raw.take(60)),
            )
          )
        }

        if (analysis.obfuscationMatches.isNotEmpty() && context != ContentContext.TECHNICAL_EXPLANATION) {
          val firstObf = analysis.obfuscationMatches.first()
          findings.add(
            Finding(
              id = "spam_evasion_$idx",
              dedupKey = "OBFUSCATION:$idx:${firstObf.reconstructed}",
              engineId = EngineId.SPAM_DETECTION,
              category = "تحايل على الفلاتر",
              titleAr = "اشتباه محاولة التفاف نصي في التغريدة ${idx + 1}",
              explanationAr = "التمويه داخل «${firstObf.snippet}» يشير لاحتمال التحايل على الفلاتر النصية.",
              suggestionAr = "اكتب الكلمة بصورتها القياسية: «${firstObf.reconstructed}».",
              severity = RiskLevel.HIGH,
              confidence = 0.88,
              location =
                EvidenceLocation(
                  tweetIndex = idx,
                  startOffset = firstObf.startOffset,
                  endOffset = firstObf.endOffset,
                  snippet = firstObf.snippet,
                ),
            )
          )
        }

        // Fluff detection
        val fillerHits = FILLER_PHRASES.count { analysis.normalizedText.contains(arabicEngine.normalize(it)) }
        if (fillerHits >= 2 && analysis.normalizedTokens.size <= 14) {
          findings.add(
            Finding(
              id = "spam_fluff_$idx",
              dedupKey = "FILLER_PADDING:$idx",
              engineId = EngineId.SPAM_DETECTION,
              category = "حشو نصي",
              titleAr = "عبارات حشو وجذب انتباه دون معلومة في التغريدة ${idx + 1}",
              explanationAr = "تعتمد التغريدة على عبارات تحفيزية عامة دون إضافة محتوى حقيقي للقارئ.",
              suggestionAr = "أضف فكرة ملموسة أو مبرراً واضحاً للقراءة بدلاً من الحشو.",
              severity = RiskLevel.LOW,
              confidence = 0.78,
              location = EvidenceLocation(tweetIndex = idx, snippet = raw.take(60)),
            )
          )
        }
      }

      // C. Coordinated template patterns across >= 3 tweets (never labels user a bot from 1 tweet!)
      if (nonBlankTweets.size >= 3) {
        val skeletons =
          analyses.mapIndexedNotNull { idx, a ->
            if (a.normalizedTokens.size >= 4) {
              idx to a.normalizedTokens.take(3).joinToString(" ")
            } else null
          }
        val grouped = skeletons.groupBy { it.second }.filter { it.value.size >= 3 }
        for ((prefix, group) in grouped) {
          val indicesStr = group.map { it.first + 1 }.joinToString("، ")
          findings.add(
            Finding(
              id = "spam_template_${prefix.hashCode()}",
              dedupKey = "COORDINATED_TEMPLATE:$prefix",
              engineId = EngineId.SPAM_DETECTION,
              category = "أنماط منسقة",
              titleAr = "تكرار قالب نصي موحد في ${group.size} تغريدات",
              explanationAr =
                "التغريدات ($indicesStr) تبدأ بالبنية النمطية نفسها («$prefix...»). ملاحظة: هذا مؤشر تكرار أسلوبي في الثريد ولا يعني تصنيف الحساب كآلي.",
              suggestionAr = "نوّع بدايات التغريدات لتبدو طبيعية ومترابطة بشرياً.",
              severity = RiskLevel.MEDIUM,
              confidence = 0.79,
              location = EvidenceLocation(tweetIndex = group.first().first, snippet = prefix),
            )
          )
        }
      }

      val maxSeverity = findings.maxByOrNull { it.severity.weightOrder }?.severity ?: RiskLevel.LOW
      val summary =
        if (findings.isEmpty()) {
          "السياق المصنف: ${context.labelAr} — لم تظهر مؤشرات سبام أو ترويج مزعج."
        } else {
          "السياق المصنف: ${context.labelAr} — رُصد ${findings.size} مؤشر(ات) تتعلق بالتكرار أو السلوك الترويجي."
        }

      EngineResult(
        engineId = EngineId.SPAM_DETECTION,
        nameAr = EngineId.SPAM_DETECTION.displayNameAr,
        status = EngineStatus.SUCCESS,
        riskLevel = maxSeverity,
        confidence = if (findings.isEmpty()) 0.85 else findings.map { it.confidence }.average(),
        summaryAr = summary,
        findings = findings,
        metadata = mapOf("السياق المكتشف" to context.labelAr),
        durationMs = System.currentTimeMillis() - start,
      )
    } catch (e: Exception) {
      EngineResult(
        engineId = EngineId.SPAM_DETECTION,
        nameAr = EngineId.SPAM_DETECTION.displayNameAr,
        status = EngineStatus.FAILED,
        riskLevel = RiskLevel.INSUFFICIENT_DATA,
        confidence = 0.0,
        summaryAr = "تعذر تشغيل محرك اكتشاف السبام بسبب خطأ تقني.",
        errorMessage = e.message,
        durationMs = System.currentTimeMillis() - start,
      )
    }
  }
}

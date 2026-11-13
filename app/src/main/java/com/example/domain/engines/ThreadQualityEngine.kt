package com.example.domain.engines

import com.example.domain.model.AnalysisConfig
import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.EvidenceLocation
import com.example.domain.model.Finding
import com.example.domain.model.RiskLevel

class ThreadQualityEngine(
  private val arabicEngine: ArabicTextIntelligenceEngine = ArabicTextIntelligenceEngine(),
) {

  companion object {
    private val TRANSITION_MARKERS =
      listOf("أولا", "ثانيا", "ثالثا", "رابعا", "اخيرا", "خلاصه", "بالإضافه", "اما", "لذلك", "بينما", "في المقابل", "بالتالي", "هنا", "الخطوه")

    private val FILLER_ONLY_PATTERNS =
      listOf("يتبع", "تابع", "تابع الثريد", "شاهد التالي", "المزيد بالاسفل", "اقرا التالي")

    private val STOP_WORDS =
      setOf(
        "في", "من", "على", "الى", "عن", "مع", "هذا", "هذه", "ذلك", "التي", "الذي",
        "ان", "انه", "انها", "هو", "هي", "هم", "كان", "يكون", "لا", "ما", "لم", "لن",
        "او", "ثم", "بل", "كل", "قد", "بعد", "قبل", "عند", "بين", "حتى", "كما", "ايضا",
      )

    private val POSITIVE_POLARITY = listOf("مجاني", "بدون رسوم", "لا يحتاج", "سهل جدا", "سريع", "امن تماما")
    private val NEGATIVE_POLARITY = listOf("مدفوع", "باشتراك شهري", "يتطلب دفع", "معقد", "بطيء", "خطير")
  }

  fun runEngine(tweets: List<String>, config: AnalysisConfig = AnalysisConfig()): EngineResult {
    val start = System.currentTimeMillis()
    return try {
      val nonBlankIndices = tweets.indices.filter { tweets[it].isNotBlank() }
      if (nonBlankIndices.isEmpty()) {
        return EngineResult(
          engineId = EngineId.THREAD_QUALITY,
          nameAr = EngineId.THREAD_QUALITY.displayNameAr,
          status = EngineStatus.SUCCESS,
          riskLevel = RiskLevel.INSUFFICIENT_DATA,
          confidence = 0.25,
          summaryAr = "أضف تغريدة واحدة على الأقل لتقييم جودة البنية والترابط.",
          durationMs = System.currentTimeMillis() - start,
        )
      }

      val analyses = tweets.map { arabicEngine.analyzeTweet(it) }
      val findings = mutableListOf<Finding>()

      // 1. Individual tweet evaluation
      for (idx in nonBlankIndices) {
        val raw = tweets[idx]
        val analysis = analyses[idx]
        val tweetNum = idx + 1

        // Configurable character limit check
        if (raw.length > config.maxTweetChars) {
          findings.add(
            Finding(
              id = "tq_len_over_$idx",
              dedupKey = "CHAR_LIMIT_EXCEEDED:$idx",
              engineId = EngineId.THREAD_QUALITY,
              category = "تجاوز حد الأحرف",
              titleAr = "التغريدة رقم $tweetNum تتجاوز الحد المضبوط (${raw.length}/${config.maxTweetChars})",
              explanationAr =
                "طول التغريدة رقم $tweetNum (${raw.length} حرفاً) يتجاوز حد الأحرف المختار في الإعدادات (${config.maxTweetChars}).",
              suggestionAr = "قسّم التغريدة إلى تغريدتين أو فعّل حد المنشورات الطويلة في الإعدادات إن كان حسابك يدعم ذلك.",
              severity = RiskLevel.MEDIUM,
              confidence = 0.98,
              location = EvidenceLocation(tweetIndex = idx, snippet = raw.take(60)),
            )
          )
        }

        // Zero-value filler tweet check
        val normClean = analysis.normalizedText.replace(Regex("""^\d+[/.\-]\s*"""), "").trim()
        val isFillerOnly =
          FILLER_ONLY_PATTERNS.any { normClean == arabicEngine.normalize(it) } ||
            (analysis.normalizedTokens.size <= 2 && raw.length < 15 && tweets.size > 1)
        if (isFillerOnly) {
          findings.add(
            Finding(
              id = "tq_filler_$idx",
              dedupKey = "ZERO_VALUE_TWEET:$idx",
              engineId = EngineId.THREAD_QUALITY,
              category = "تغريدة قليلة القيمة",
              titleAr = "التغريدة رقم $tweetNum قصيرة جداً ولا تضيف معلومة مستقلة",
              explanationAr = "التغريدة رقم $tweetNum («${raw.trim()}») تفتقر لمعلومة واضحة وقد تقطع تدفق القارئ.",
              suggestionAr = "ادمجها مع التغريدة السابقة أو أضف فكرة جوهرية واضحة.",
              severity = RiskLevel.MEDIUM,
              confidence = 0.86,
              location = EvidenceLocation(tweetIndex = idx, snippet = raw.trim()),
            )
          )
        }
      }

      // 2. Collective thread evaluation (for multi-tweet threads >= 2)
      if (nonBlankIndices.size >= 2) {
        val contentStemsByTweet =
          analyses.map { a ->
            a.normalizedTokens
              .map(arabicEngine::softStemArabic)
              .filter { it.length >= 3 && it !in STOP_WORDS }
              .toSet()
          }

        // Info repetition between non-adjacent or adjacent tweets (moderate similarity 0.56..0.77; >=0.78 aligns dedupKey with Spam)
        for (i in nonBlankIndices.indices) {
          val idxA = nonBlankIndices[i]
          for (j in (i + 1) until nonBlankIndices.size) {
            val idxB = nonBlankIndices[j]
            val sim = arabicEngine.computeSemanticSimilarity(tweets[idxA], tweets[idxB])
            if (sim in 0.56..0.779) {
              findings.add(
                Finding(
                  id = "tq_info_repeat_${idxA}_$idxB",
                  dedupKey = "CROSS_TWEET_SIMILARITY:${minOf(idxA, idxB)}:${maxOf(idxA, idxB)}",
                  engineId = EngineId.THREAD_QUALITY,
                  category = "تكرار المعلومات",
                  titleAr = "التغريدة رقم ${idxB + 1} تعيد طرح فكرة مشابهة للتغريدة ${idxA + 1}",
                  explanationAr =
                    "يوجد تداخل ملحوظ في الأفكار بين التغريدة رقم ${idxB + 1} والتغريدة رقم ${idxA + 1} (تشابه ${(sim * 100).toInt()}%) دون إضافة زاوية جديدة كافية.",
                  suggestionAr = "لخّص الفكرة في تغريدة واحدة وخصّص التغريدة رقم ${idxB + 1} لمثال عملي أو خطوة جديدة.",
                  severity = RiskLevel.MEDIUM,
                  confidence = 0.80,
                  location = EvidenceLocation(tweetIndex = idxB, snippet = tweets[idxB].take(75)),
                )
              )
            }
          }
        }

        // Transition coherence between adjacent tweets
        var weakTransitions = 0
        for (k in 0 until nonBlankIndices.size - 1) {
          val currIdx = nonBlankIndices[k]
          val nextIdx = nonBlankIndices[k + 1]
          val stemsA = contentStemsByTweet[currIdx]
          val stemsB = contentStemsByTweet[nextIdx]
          val overlap = stemsA.intersect(stemsB).size
          val hasTransition =
            TRANSITION_MARKERS.any { m -> analyses[nextIdx].normalizedText.contains(arabicEngine.normalize(m)) }
          if (stemsA.size >= 5 && stemsB.size >= 5 && overlap == 0 && !hasTransition) {
            weakTransitions++
            if (weakTransitions <= 2) {
              findings.add(
                Finding(
                  id = "tq_jump_${currIdx}_$nextIdx",
                  dedupKey = "TOPIC_JUMP:$nextIdx",
                  engineId = EngineId.THREAD_QUALITY,
                  category = "ترابط الأفكار",
                  titleAr = "انتقال مفاجئ في الموضوع عند التغريدة رقم ${nextIdx + 1}",
                  explanationAr =
                    "لم يُرصد رابط لفظي أو أداة انتقال واضحة بين التغريدة رقم ${currIdx + 1} والتغريدة رقم ${nextIdx + 1}.",
                  suggestionAr = "أضف جملة ربط تمهيدية في بداية التغريدة رقم ${nextIdx + 1} لتوضيح التسلسل.",
                  severity = RiskLevel.LOW,
                  confidence = 0.72,
                  location = EvidenceLocation(tweetIndex = nextIdx, snippet = tweets[nextIdx].take(65)),
                )
              )
            }
          }
        }

        // Potential contradiction check on shared topic
        for (i in nonBlankIndices.indices) {
          val idxA = nonBlankIndices[i]
          for (j in (i + 1) until nonBlankIndices.size) {
            val idxB = nonBlankIndices[j]
            val shared = contentStemsByTweet[idxA].intersect(contentStemsByTweet[idxB])
            if (shared.size >= 2) {
              val normA = analyses[idxA].normalizedText
              val normB = analyses[idxB].normalizedText
              val aPos = POSITIVE_POLARITY.any { normA.contains(arabicEngine.normalize(it)) }
              val bNeg = NEGATIVE_POLARITY.any { normB.contains(arabicEngine.normalize(it)) }
              val aNeg = NEGATIVE_POLARITY.any { normA.contains(arabicEngine.normalize(it)) }
              val bPos = POSITIVE_POLARITY.any { normB.contains(arabicEngine.normalize(it)) }
              if ((aPos && bNeg) || (aNeg && bPos)) {
                findings.add(
                  Finding(
                    id = "tq_contra_${idxA}_$idxB",
                    dedupKey = "POTENTIAL_CONTRADICTION:${minOf(idxA, idxB)}:${maxOf(idxA, idxB)}",
                    engineId = EngineId.THREAD_QUALITY,
                    category = "اتساق المعلومات",
                    titleAr = "تعارض محتمل بين التغريدة ${idxA + 1} والتغريدة ${idxB + 1}",
                    explanationAr =
                      "التغريدتان تتناولان الموضوع نفسه (${shared.take(2).joinToString("، ")}) لكن بإشارات متقابلة (مثل المجانية مقابل الدفع أو السهولة مقابل التعقيد).",
                    suggestionAr = "وضّح الشرط أو الاستثناء بين التغريدتين لمنع التباس القارئ.",
                    severity = RiskLevel.MEDIUM,
                    confidence = 0.71,
                    location = EvidenceLocation(tweetIndex = idxB, snippet = tweets[idxB].take(75)),
                  )
                )
              }
            }
          }
        }

        // Intro & Outro evaluation for threads >= 3 tweets
        if (nonBlankIndices.size >= 3) {
          val firstIdx = nonBlankIndices.first()
          val lastIdx = nonBlankIndices.last()
          if (analyses[firstIdx].normalizedTokens.size < 5) {
            findings.add(
              Finding(
                id = "tq_weak_intro",
                dedupKey = "WEAK_THREAD_INTRO",
                engineId = EngineId.THREAD_QUALITY,
                category = "مقدمة الثريد",
                titleAr = "مقدمة الثريد (التغريدة 1) مقتضبة جداً",
                explanationAr = "التغريدة الأولى هي بوابة القارئ؛ اختصارها المفرط يقلل وضوح فائدة الثريد.",
                suggestionAr = "اذكر موضوع الثريد وما سيتعلمه القارئ في التغريدة الأولى.",
                severity = RiskLevel.LOW,
                confidence = 0.80,
                location = EvidenceLocation(tweetIndex = firstIdx, snippet = tweets[firstIdx].take(60)),
              )
            )
          }

          val lastNorm = analyses[lastIdx].normalizedText
          val hasOutroSignal =
            listOf("خلاصه", "ختام", "اخيرا", "باختصار", "شاركنا", "رأيك", "تجربتك", "سؤال", "النتيجه")
              .any { lastNorm.contains(arabicEngine.normalize(it)) } || tweets[lastIdx].contains("؟")
          if (!hasOutroSignal && analyses[lastIdx].normalizedTokens.size < 10) {
            findings.add(
              Finding(
                id = "tq_weak_outro",
                dedupKey = "WEAK_THREAD_OUTRO",
                engineId = EngineId.THREAD_QUALITY,
                category = "خاتمة الثريد",
                titleAr = "الخاتمة في التغريدة رقم ${lastIdx + 1} تحتاج تلخيصاً أو سؤال تفاعل",
                explanationAr = "ينتهي الثريد فجأة دون خلاصة جامعة أو دعوة طبيعية للحوار.",
                suggestionAr = "اختم التغريدة الأخيرة بخلاصة سريعة وسؤال يفتح نقاشاً مفيداً.",
                severity = RiskLevel.LOW,
                confidence = 0.74,
                location = EvidenceLocation(tweetIndex = lastIdx, snippet = tweets[lastIdx].take(60)),
              )
            )
          }
        }
      }

      val maxRisk = findings.maxByOrNull { it.severity.weightOrder }?.severity ?: RiskLevel.LOW
      val summary =
        if (findings.isEmpty()) {
          "بنية الثريد متماسكة ومتسلسلة والأطوال مناسبة دون حشو أو تكرار ملحوظ."
        } else {
          "رُصد ${findings.size} ملاحظة حول جودة البنية أو التسلسل أو الحدود المضبوطة للتغريدات."
        }

      EngineResult(
        engineId = EngineId.THREAD_QUALITY,
        nameAr = EngineId.THREAD_QUALITY.displayNameAr,
        status = EngineStatus.SUCCESS,
        riskLevel = maxRisk,
        confidence = if (findings.isEmpty()) 0.86 else findings.map { it.confidence }.average(),
        summaryAr = summary,
        findings = findings,
        metadata =
          mapOf(
            "عدد التغريدات الفعالة" to nonBlankIndices.size.toString(),
            "متوسط طول التغريدة" to "${nonBlankIndices.map { tweets[it].length }.average().toInt()} حرف",
          ),
        durationMs = System.currentTimeMillis() - start,
      )
    } catch (e: Exception) {
      EngineResult(
        engineId = EngineId.THREAD_QUALITY,
        nameAr = EngineId.THREAD_QUALITY.displayNameAr,
        status = EngineStatus.FAILED,
        riskLevel = RiskLevel.INSUFFICIENT_DATA,
        confidence = 0.0,
        summaryAr = "تعذر تشغيل محرك جودة الثريد.",
        errorMessage = e.message,
        durationMs = System.currentTimeMillis() - start,
      )
    }
  }
}

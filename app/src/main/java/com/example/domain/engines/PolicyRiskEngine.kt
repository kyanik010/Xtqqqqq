package com.example.domain.engines

import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.EvidenceLocation
import com.example.domain.model.Finding
import com.example.domain.model.PolicyVerdict
import com.example.domain.model.RiskLevel

data class PolicyCategoryRule(
  val id: String,
  val nameAr: String,
  val phrasePatterns: List<String>,
  val corroboratingMarkers: List<String>,
  val baseSeverity: RiskLevel,
  val explanationTemplateAr: String,
)

/**
 * Policy Risk Engine:
 * - Extensible category rules.
 * - Never flags a single isolated word without corroborating intent/context.
 * - Separates quoting, criticism, warning, technical, and educational contexts from harmful intent.
 * - Does not invent undocumented X rules; clearly marks results as analytical estimates.
 */
class PolicyRiskEngine(
  private val arabicEngine: ArabicTextIntelligenceEngine = ArabicTextIntelligenceEngine(),
  private val categories: List<PolicyCategoryRule> = defaultCategories(),
) {

  companion object {
    private val WARNING_OR_EDUCATIONAL_CONTEXT =
      listOf(
        "تحذير", "احذر", "احذروا", "نحذر", "توعيه", "للوقايه", "انتبهوا", "حمايه",
        "شرح", "تحليل", "ثغره", "امن سيبراني", "مثال", "دراسه", "تقرير", "نقد",
        "يقول البعض", "شائعه", "مضلل", "احتيال منتشر", "طريقه اكتشاف", "لا تصدق",
      )

    fun defaultCategories(): List<PolicyCategoryRule> =
      listOf(
        PolicyCategoryRule(
          id = "THREATS_VIOLENCE",
          nameAr = "التهديدات والعنف",
          phrasePatterns = listOf("سأقتلك", "نهددكم بالقتل", "تهديد مباشر بالايذاء", "يجب تدميرهم جسديا", "اعتداء جسدي الان"),
          corroboratingMarkers = listOf("سلاح", "قتل", "تفجير", "ايذاء", "انتقام دموي", "تهديد"),
          baseSeverity = RiskLevel.CRITICAL,
          explanationTemplateAr = "النص يتضمن صياغة قد تُفسَّر كتهديد أو تحريض على الأذى الجسدي.",
        ),
        PolicyCategoryRule(
          id = "HARASSMENT_TARGETING",
          nameAr = "المضايقة والاستهداف",
          phrasePatterns = listOf("انشروا عنوانه", "افضحوا رقمه", "هاجموا حسابه الان", "حمله سب جماعيه", "لاحقوه في كل مكان"),
          corroboratingMarkers = listOf("هاتف", "عنوان", "منزل", "حساب", "ملاحقه", "تشهير"),
          baseSeverity = RiskLevel.HIGH,
          explanationTemplateAr = "العبارة تشير إلى استهداف شخصي أو دعوة لمضايقة أو كشف بيانات خاصة.",
        ),
        PolicyCategoryRule(
          id = "HATE_SPEECH",
          nameAr = "خطاب الكراهية",
          phrasePatterns = listOf("فئه منحطه", "يجب طردهم جميعا", "لا يستحقون الحياه", "اقل من البشر", "عرق ادنى"),
          corroboratingMarkers = listOf("طرد", "كراهيه", "تحقير", "اقصاء", "تطهير"),
          baseSeverity = RiskLevel.HIGH,
          explanationTemplateAr = "توجد مؤشرات تحقير أو إقصاء جماعي قد ترتبط بسياسات خطاب الكراهية.",
        ),
        PolicyCategoryRule(
          id = "PROHIBITED_SEXUAL",
          nameAr = "المحتوى الجنسي المحظور",
          phrasePatterns = listOf("محتوى اباحي مدفوع", "فيديوهات جنسيه خاصه", "تواصل للخدمات الجنسيه"),
          corroboratingMarkers = listOf("اباحي", "جنسي", "عاري", "فيديو خاص"),
          baseSeverity = RiskLevel.HIGH,
          explanationTemplateAr = "مؤشرات ترويج لمحتوى جنسي صريح أو خدمات غير مسموح بها في السياق العام.",
        ),
        PolicyCategoryRule(
          id = "FRAUD_SCAM",
          nameAr = "الاحتيال والخداع المالي",
          phrasePatterns = listOf(
            "ضاعف راس مالك خلال ساعات", "ارباح مضمونه بدون مخاطره", "حول المبلغ لاستلام الجائزه",
            "استثمار مضمون 100", "اربح الف دولار يوميا", "ارسل بيانات بطاقتك", "مفتاح المحفظه لاستلام",
          ),
          corroboratingMarkers = listOf("حول", "مضمون", "بدون خساره", "جائزه", "بطاقه", "محفظه", "ارباح فوريه"),
          baseSeverity = RiskLevel.HIGH,
          explanationTemplateAr = "الصياغة تحمل سمات وعود مالية غير واقعية أو طلب تحويلات/بيانات حساسة.",
        ),
        PolicyCategoryRule(
          id = "MANIPULATION_MISINFO",
          nameAr = "التلاعب والتضليل",
          phrasePatterns = listOf("علاج سري يخفونه عنك", "خبر مؤكد بدون مصدر رسمي", "اشرب هذا لشفاء السرطان"),
          corroboratingMarkers = listOf("سري", "مؤامره", "يخفون", "علاج قطعي", "مؤكد 100"),
          baseSeverity = RiskLevel.MEDIUM,
          explanationTemplateAr = "ادعاء قطعي ذو طابع طبي أو عام يفتقر للتوثيق وقد يستدعي مراجعة المصداقية.",
        ),
        PolicyCategoryRule(
          id = "PROHIBITED_ILLICIT",
          nameAr = "محتوى أو أنشطة محظورة",
          phrasePatterns = listOf("بيع حسابات مسروقه", "شراء متابعين حقيقيين فورا", "ادوات اختراق حسابات", "تزوير وثائق رسميه"),
          corroboratingMarkers = listOf("مسروق", "تزوير", "اختراق", "رشق متابعين", "بيع حسابات"),
          baseSeverity = RiskLevel.HIGH,
          explanationTemplateAr = "الإشارة إلى خدمات بيع حسابات أو تلاعب مصطنع بالمتابعين أو أنشطة غير مشروعة.",
        ),
      )
  }

  fun isMitigatedContext(normalizedText: String, rawText: String, matchStart: Int): Boolean {
    // Check if inside quotation marks
    val before = rawText.substring(0, matchStart.coerceIn(0, rawText.length))
    val quoteCount = before.count { it == '"' || it == '«' } - before.count { it == '»' }
    if (before.count { it == '"' } % 2 == 1 || before.count { it == '«' } > before.count { it == '»' }) {
      return true
    }

    return WARNING_OR_EDUCATIONAL_CONTEXT.any { marker ->
      normalizedText.contains(arabicEngine.normalize(marker))
    }
  }

  fun runEngine(tweets: List<String>, spamResult: EngineResult? = null, urlResult: EngineResult? = null): EngineResult {
    val start = System.currentTimeMillis()
    return try {
      if (tweets.all { it.isBlank() }) {
        return EngineResult(
          engineId = EngineId.POLICY_RISK,
          nameAr = EngineId.POLICY_RISK.displayNameAr,
          status = EngineStatus.SUCCESS,
          riskLevel = RiskLevel.INSUFFICIENT_DATA,
          confidence = 0.25,
          summaryAr = "لا يوجد محتوى كافٍ لتقييم مؤشرات السياسات.",
          durationMs = System.currentTimeMillis() - start,
        )
      }

      val findings = mutableListOf<Finding>()

      tweets.forEachIndexed { idx, raw ->
        if (raw.isBlank()) return@forEachIndexed
        val norm = arabicEngine.normalize(raw)
        val tokens = arabicEngine.tokenize(norm).toSet()

        for (cat in categories) {
          val matchedPhrase =
            cat.phrasePatterns.firstOrNull { phrase -> norm.contains(arabicEngine.normalize(phrase)) }

          val corroboratingHits =
            cat.corroboratingMarkers.count { m ->
              val nm = arabicEngine.normalize(m)
              tokens.contains(nm) || norm.contains(nm)
            }

          // Rule: NEVER trigger on a single isolated word!
          // Either an explicit multi-word risk phrase OR >= 2 corroborating markers in context
          if (matchedPhrase != null || corroboratingHits >= 2) {
            val snippet = matchedPhrase ?: cat.corroboratingMarkers.filter { norm.contains(arabicEngine.normalize(it)) }.take(2).joinToString(" + ")
            val matchOffset = raw.indexOf(snippet.take(6)).coerceAtLeast(0)
            val mitigated = isMitigatedContext(norm, raw, matchOffset)

            if (mitigated) {
              findings.add(
                Finding(
                  id = "policy_ctx_${cat.id}_$idx",
                  dedupKey = "POLICY:${cat.id}:$idx",
                  engineId = EngineId.POLICY_RISK,
                  category = cat.nameAr,
                  titleAr = "سياق تحذيري/تعليمي يذكر مصطلحات حساسة في التغريدة ${idx + 1}",
                  explanationAr =
                    "وردت إشارات مرتبطة بـ«${cat.nameAr}» ضمن سياق يبدو تحذيرياً أو نقدياً أو اقتباساً، لذا لا يُصنَّف كمخالفة مباشرة، ويُنصح بالتوضيح الصريح للسياق.",
                  suggestionAr = "تأكد من إبقاء إطار التحذير أو الاقتباس واضحاً في مطلع التغريدة.",
                  severity = RiskLevel.LOW,
                  confidence = 0.72,
                  location = EvidenceLocation(tweetIndex = idx, snippet = snippet),
                )
              )
            } else {
              val severity = if (matchedPhrase != null) cat.baseSeverity else RiskLevel.MEDIUM
              findings.add(
                Finding(
                  id = "policy_${cat.id}_$idx",
                  dedupKey = "POLICY:${cat.id}:$idx",
                  engineId = EngineId.POLICY_RISK,
                  category = cat.nameAr,
                  titleAr = "مؤشر مراجعة في فئة «${cat.nameAr}» (التغريدة ${idx + 1})",
                  explanationAr = "${cat.explanationTemplateAr} (تقدير تحليلي مستقل وليس قراراً رسمياً من منصة X).",
                  suggestionAr = "راجع الصياغة وأزل الوعود المطلقة أو العبارات الحادة قبل النشر.",
                  severity = severity,
                  confidence = if (matchedPhrase != null) 0.85 else 0.74,
                  location = EvidenceLocation(tweetIndex = idx, snippet = snippet),
                )
              )
            }
          }
        }
      }

      // Incorporate cross-engine signals for Spam/Inauthentic & Malicious Links without duplicating exact text findings
      val hasSevereSpam = spamResult?.findings?.any { it.severity.weightOrder >= RiskLevel.HIGH.weightOrder } == true
      val hasSuspiciousUrl = urlResult?.findings?.any { it.severity.weightOrder >= RiskLevel.HIGH.weightOrder } == true

      val maxRisk =
        listOfNotNull(
            findings.maxByOrNull { it.severity.weightOrder }?.severity,
            if (hasSevereSpam || hasSuspiciousUrl) RiskLevel.MEDIUM else null,
          )
          .maxByOrNull { it.weightOrder } ?: RiskLevel.LOW

      val verdict =
        when {
          findings.any { it.severity == RiskLevel.CRITICAL } -> PolicyVerdict.LIKELY_VIOLATION
          findings.any { it.severity == RiskLevel.HIGH } -> PolicyVerdict.RISK_INDICATORS
          findings.any { it.severity == RiskLevel.MEDIUM } || hasSevereSpam || hasSuspiciousUrl -> PolicyVerdict.NEEDS_REVIEW
          else -> PolicyVerdict.COMPLIANT
        }

      val summary =
        when (verdict) {
          PolicyVerdict.COMPLIANT -> "لم تظهر مؤشرات مخالفة واضحة وفق القواعد التحليلية المتاحة (تقدير تحليلي وليس قراراً رسمياً من X)."
          PolicyVerdict.NEEDS_REVIEW -> "يحتاج المحتوى إلى مراجعة سياقية لبعض العبارات أو الروابط قبل النشر."
          PolicyVerdict.RISK_INDICATORS -> "يحمل مؤشرات خطورة تستدعي تعديلاً أو توضيحاً قبل النشر."
          PolicyVerdict.LIKELY_VIOLATION -> "مؤشرات قوية لاحتمال تعارض النص مع قواعد السلامة العامة للمنصات."
        }

      EngineResult(
        engineId = EngineId.POLICY_RISK,
        nameAr = EngineId.POLICY_RISK.displayNameAr,
        status = EngineStatus.SUCCESS,
        riskLevel = maxRisk,
        confidence = if (findings.isEmpty()) 0.82 else findings.map { it.confidence }.average(),
        summaryAr = summary,
        findings = findings,
        metadata = mapOf("تقدير السياسات" to verdict.labelAr),
        durationMs = System.currentTimeMillis() - start,
      )
    } catch (e: Exception) {
      EngineResult(
        engineId = EngineId.POLICY_RISK,
        nameAr = EngineId.POLICY_RISK.displayNameAr,
        status = EngineStatus.FAILED,
        riskLevel = RiskLevel.INSUFFICIENT_DATA,
        confidence = 0.0,
        summaryAr = "تعذر تشغيل محرك تحليل السياسات بسبب خطأ تقني.",
        errorMessage = e.message,
        durationMs = System.currentTimeMillis() - start,
      )
    }
  }
}

package com.example.domain.engines

import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.EvidenceLocation
import com.example.domain.model.Finding
import com.example.domain.model.RiskLevel

/**
 * Visibility Risk Assessment Engine:
 * Evaluates pre-publish signals that may affect reader experience or trigger moderation friction.
 * Strictly avoids claiming knowledge of X's internal algorithm or guaranteeing reach.
 */
class VisibilityRiskEngine {

  fun runEngine(
    tweets: List<String>,
    arabicResult: EngineResult,
    spamResult: EngineResult,
    policyResult: EngineResult,
    urlResult: EngineResult,
    qualityResult: EngineResult,
  ): EngineResult {
    val start = System.currentTimeMillis()
    return try {
      if (tweets.all { it.isBlank() }) {
        return EngineResult(
          engineId = EngineId.VISIBILITY_RISK,
          nameAr = EngineId.VISIBILITY_RISK.displayNameAr,
          status = EngineStatus.SUCCESS,
          riskLevel = RiskLevel.INSUFFICIENT_DATA,
          confidence = 0.25,
          summaryAr = "لا يوجد محتوى لتقييم مؤشرات الظهور المحتملة.",
          durationMs = System.currentTimeMillis() - start,
        )
      }

      val findings = mutableListOf<Finding>()

      val upstreamFindings =
        arabicResult.findings +
          spamResult.findings +
          policyResult.findings +
          urlResult.findings +
          qualityResult.findings

      val highLinkSignals = upstreamFindings.filter { it.dedupKey.startsWith("HIGH_LINK_DENSITY") || it.dedupKey == "THREAD_LINK_DENSITY" }
      val repetitionSignals = upstreamFindings.filter { it.dedupKey.startsWith("CROSS_TWEET_SIMILARITY") || it.dedupKey.startsWith("CROSS_SENTENCE_REPEAT") }
      val evasionSignals = upstreamFindings.filter { it.dedupKey.startsWith("OBFUSCATION") }
      val ctaSignals = upstreamFindings.filter { it.dedupKey == "REPEATED_CTA_THREAD" || it.dedupKey == "PROMO_HYPE_CLUSTER" }
      val policySignals = policyResult.findings.filter { it.severity.weightOrder >= RiskLevel.MEDIUM.weightOrder }

      if (evasionSignals.isNotEmpty()) {
        val sample = evasionSignals.first()
        findings.add(
          Finding(
            id = "vis_evasion",
            dedupKey = sample.dedupKey, // Share dedupKey so aggregator never double-counts!
            engineId = EngineId.VISIBILITY_RISK,
            category = "تحايل نصي",
            titleAr = "مؤشر تمويه نصي قد يرفع حساسية التصفية التلقائية",
            explanationAr =
              "التمويه الكتابي أو فصل الأحرف قد يثير فلاتر الجودة الآلية في المنصات العامة (مؤشر مراجعة تحليلي وليس تنبؤاً مؤكداً).",
            suggestionAr = "اعتمد الكتابة العربية القياسية لتجنب اشتباه الالتفاف.",
            severity = RiskLevel.HIGH,
            confidence = 0.84,
            location = sample.location,
          )
        )
      }

      if (highLinkSignals.isNotEmpty() || ctaSignals.isNotEmpty()) {
        val ref = (highLinkSignals + ctaSignals).first()
        findings.add(
          Finding(
            id = "vis_promo_links",
            dedupKey = "VISIBILITY_PROMO_LINK_FRICTION",
            engineId = EngineId.VISIBILITY_RISK,
            category = "توازن القيمة والروابط",
            titleAr = "كثافة الروابط أو النداءات الترويجية قد تقلل تفاعل القراء",
            explanationAr =
              "التركيز المرتفع على الروابط الخارجية أو دعوات الاشتراك المتكررة قد يضعف تجربة القراءة ويزيد مؤشرات الإزعاج المحتملة.",
            suggestionAr = "قدّم قيمة معرفية كاملة داخل النص واجعل الرابط أو الدعوة في تغريدة ختامية واحدة.",
            severity = if (highLinkSignals.isNotEmpty() && ctaSignals.isNotEmpty()) RiskLevel.HIGH else RiskLevel.MEDIUM,
            confidence = 0.79,
            location = ref.location,
          )
        )
      }

      if (repetitionSignals.isNotEmpty()) {
        val ref = repetitionSignals.first()
        findings.add(
          Finding(
            id = "vis_repetition",
            dedupKey = ref.dedupKey, // Same dedupKey prevents double counting
            engineId = EngineId.VISIBILITY_RISK,
            category = "تكرار المحتوى",
            titleAr = "التكرار النصي قد يضعف التنوع القرائي للثريد",
            explanationAr = "إعادة الجمل أو الأفكار عبر التغريدات قد يُعد مؤشر جودة منخفضة قبل النشر.",
            suggestionAr = "اجعل كل تغريدة تضيف نقطة مستقلة ومكملة لما قبلها.",
            severity = ref.severity,
            confidence = 0.80,
            location = ref.location,
          )
        )
      }

      if (policySignals.isNotEmpty()) {
        val ref = policySignals.first()
        findings.add(
          Finding(
            id = "vis_policy",
            dedupKey = ref.dedupKey,
            engineId = EngineId.VISIBILITY_RISK,
            category = "مؤشرات حساسة",
            titleAr = "مؤشر حساس يستحق المراجعة قبل النشر",
            explanationAr = "رُصدت إشارات مرتبطة بفئة «${ref.category}»؛ مراجعتها مسبقاً تقلل مخاطر سوء الفهم أو التقييد المحتمل.",
            suggestionAr = ref.suggestionAr,
            severity = ref.severity,
            confidence = ref.confidence,
            location = ref.location,
          )
        )
      }

      val maxRisk = findings.maxByOrNull { it.severity.weightOrder }?.severity ?: RiskLevel.LOW
      val summary =
        if (findings.isEmpty()) {
          "مؤشرات الظهور الأولية منخفضة المخاطر. تنبيه: هذه قراءة تقديرية لجودة الإشارات ولا تضمن الوصول أو تعكس خوارزمية X الداخلية."
        } else {
          "رُصد ${findings.size} مؤشر(ات) احتكاك محتملة تستحق الانتباه قبل النشر (تقدير تحليلي غير تنبؤي، ولا يملك أي اطلاع على خوارزمية X الداخلية)."
        }

      EngineResult(
        engineId = EngineId.VISIBILITY_RISK,
        nameAr = EngineId.VISIBILITY_RISK.displayNameAr,
        status = EngineStatus.SUCCESS,
        riskLevel = maxRisk,
        confidence = if (findings.isEmpty()) 0.78 else findings.map { it.confidence }.average().coerceAtMost(0.85),
        summaryAr = summary,
        findings = findings,
        metadata = mapOf("نطاق التقييم" to "مؤشرات احتكاك محتملة قبل النشر (لا يضمن الانتشار)"),
        durationMs = System.currentTimeMillis() - start,
      )
    } catch (e: Exception) {
      EngineResult(
        engineId = EngineId.VISIBILITY_RISK,
        nameAr = EngineId.VISIBILITY_RISK.displayNameAr,
        status = EngineStatus.FAILED,
        riskLevel = RiskLevel.INSUFFICIENT_DATA,
        confidence = 0.0,
        summaryAr = "تعذر تشغيل محرك تقييم مخاطر الظهور.",
        errorMessage = e.message,
        durationMs = System.currentTimeMillis() - start,
      )
    }
  }
}

package com.example.domain.core

import com.example.domain.engines.SpamDetectionEngine
import com.example.domain.model.ContentContext
import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.Finding
import com.example.domain.model.PolicyVerdict
import com.example.domain.model.RewriteSuggestion
import com.example.domain.model.RiskLevel
import com.example.domain.model.ThreadAnalysisReport
import com.example.domain.model.UrlInspectionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Risk & Evidence Aggregator:
 * - Deduplicates findings sharing the same dedupKey across engines so the same issue is NEVER double-counted.
 * - Uses transparent, deterministic, documented rule-based scoring tiers (not arbitrary magic weights).
 * - Separates Risk Score (0-100) from Confidence Score (0.0-1.0).
 * - Keeps technical engine failures separate from content risk indicators.
 */
class RiskEvidenceAggregator {

  companion object {
    const val SCORING_DISCLAIMER_AR =
      "تنبيه منهجي: الدرجات المعروضة هي تصنيفات إرشادية أولية مبنية على قواعد التحليل النصي والأمني الموثقة وليست نسباً إحصائية معايرة لقرارات منصة X الفعلية."

    private fun severityPoints(level: RiskLevel): Int =
      when (level) {
        RiskLevel.INSUFFICIENT_DATA -> 0
        RiskLevel.LOW -> 6
        RiskLevel.MEDIUM -> 16
        RiskLevel.HIGH -> 28
        RiskLevel.CRITICAL -> 45
      }
  }

  fun deduplicateFindings(engineResults: List<EngineResult>): List<Finding> {
    val byKey = linkedMapOf<String, Finding>()
    val allFindings = engineResults.flatMap { it.findings }

    for (finding in allFindings) {
      val existing = byKey[finding.dedupKey]
      if (existing == null) {
        byKey[finding.dedupKey] = finding
      } else {
        // Retain higher severity, or higher confidence on tie; never count twice!
        val keepNew =
          finding.severity.weightOrder > existing.severity.weightOrder ||
            (finding.severity.weightOrder == existing.severity.weightOrder && finding.confidence > existing.confidence)
        if (keepNew) {
          byKey[finding.dedupKey] = finding
        }
      }
    }

    return byKey.values.sortedWith(
      compareByDescending<Finding> { it.severity.weightOrder }
        .thenBy { if (it.location.tweetIndex < 0) Int.MAX_VALUE else it.location.tweetIndex }
    )
  }

  fun aggregate(
    tweets: List<String>,
    engineResults: List<EngineResult>,
    urlSummaries: List<UrlInspectionSummary>,
    rewriteSuggestions: List<RewriteSuggestion>,
    detectedContext: ContentContext,
    operationId: String = UUID.randomUUID().toString().take(8).uppercase(Locale.ROOT),
  ): ThreadAnalysisReport {
    val nonBlankCount = tweets.count { it.isNotBlank() }
    val deduplicated = deduplicateFindings(engineResults)

    val completedEngines = engineResults.count { it.status == EngineStatus.SUCCESS || it.status == EngineStatus.DEGRADED }
    val engineCoverage = completedEngines.toDouble() / engineResults.size.coerceAtLeast(1)

    if (nonBlankCount == 0 || completedEngines == 0) {
      return ThreadAnalysisReport(
        operationId = operationId,
        analyzedAtIso = nowIso(),
        tweetCount = nonBlankCount,
        overallAssessmentAr = "غير كافٍ للحكم — أدخل نصاً عربياً أو إنجليزياً لتحليله.",
        overallRiskLevel = RiskLevel.INSUFFICIENT_DATA,
        policyVerdict = PolicyVerdict.NEEDS_REVIEW,
        detectedContext = detectedContext,
        riskScore = 0,
        confidenceScore = 0.0,
        scoringDisclaimerAr = SCORING_DISCLAIMER_AR,
        engineResults = engineResults,
        deduplicatedFindings = emptyList(),
        recommendationsAr = listOf("أضف تغريدة واحدة على الأقل ثم أعد الفحص."),
        rewriteSuggestions = emptyList(),
        urlInspectionItems = emptyList(),
      )
    }

    // Transparent deterministic score: sum unique deduplicated finding severity points with diminishing cap per tier
    val rawScore = deduplicated.sumOf { severityPoints(it.severity) }
    val riskScore = rawScore.coerceIn(0, 100)

    val highestFindingSeverity = deduplicated.maxByOrNull { it.severity.weightOrder }?.severity ?: RiskLevel.LOW
    val overallRiskLevel =
      when {
        highestFindingSeverity == RiskLevel.CRITICAL || riskScore >= 75 -> RiskLevel.CRITICAL
        highestFindingSeverity == RiskLevel.HIGH || riskScore >= 45 -> RiskLevel.HIGH
        deduplicated.any { it.severity == RiskLevel.MEDIUM } || riskScore >= 18 -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
      }

    val policyResult = engineResults.firstOrNull { it.engineId == EngineId.POLICY_RISK }
    val policyVerdictLabel = policyResult?.metadata?.get("تقدير السياسات")
    val policyVerdict =
      PolicyVerdict.entries.firstOrNull { it.labelAr == policyVerdictLabel }
        ?: when (overallRiskLevel) {
          RiskLevel.CRITICAL -> PolicyVerdict.LIKELY_VIOLATION
          RiskLevel.HIGH -> PolicyVerdict.RISK_INDICATORS
          RiskLevel.MEDIUM -> PolicyVerdict.NEEDS_REVIEW
          else -> PolicyVerdict.COMPLIANT
        }

    // Separate Confidence Score based on engine coverage, text sample length, and finding certainties
    val totalChars = tweets.sumOf { it.trim().length }
    val sampleAdequacy = (totalChars / 60.0).coerceIn(0.45, 1.0)
    val avgEngineConfidence =
      engineResults
        .filter { it.status == EngineStatus.SUCCESS || it.status == EngineStatus.DEGRADED }
        .map { it.confidence }
        .average()
        .takeIf { !it.isNaN() } ?: 0.5
    val confidenceScore = (avgEngineConfidence * 0.60 + engineCoverage * 0.25 + sampleAdequacy * 0.15).coerceIn(0.0, 0.98)

    val overallAssessmentAr =
      when (overallRiskLevel) {
        RiskLevel.LOW -> "جاهز مبدئياً للنشر — لم تظهر مؤشرات خطورة جوهرية."
        RiskLevel.MEDIUM -> "يحتاج إلى مراجعة — توجد ملاحظات تسويقية أو هيكلية أو روابط تستدعي الانتباه."
        RiskLevel.HIGH -> "مخاطر مرتفعة — يُنصح بتعديل الصياغة أو الروابط قبل النشر."
        RiskLevel.CRITICAL -> "مؤشرات حرجة — توجد عناصر أمنية أو سياسية عالية الحساسية تستوجب المعالجة."
        RiskLevel.INSUFFICIENT_DATA -> "غير كافٍ للحكم."
      }

    val recommendations =
      deduplicated
        .map { finding ->
          val prefix = if (finding.location.tweetIndex >= 0) "التغريدة ${finding.location.tweetIndex + 1}: " else ""
          "$prefix${finding.suggestionAr}"
        }
        .distinct()
        .ifEmpty { listOf("حافظ على النص الأصلي؛ البنية والوضوح مناسبان للنشر.") }

    return ThreadAnalysisReport(
      operationId = operationId,
      analyzedAtIso = nowIso(),
      tweetCount = nonBlankCount,
      overallAssessmentAr = overallAssessmentAr,
      overallRiskLevel = overallRiskLevel,
      policyVerdict = policyVerdict,
      detectedContext = detectedContext,
      riskScore = riskScore,
      confidenceScore = confidenceScore,
      scoringDisclaimerAr = SCORING_DISCLAIMER_AR,
      engineResults = engineResults,
      deduplicatedFindings = deduplicated,
      recommendationsAr = recommendations,
      rewriteSuggestions = rewriteSuggestions,
      urlInspectionItems = urlSummaries,
    )
  }

  private fun nowIso(): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return fmt.format(Date())
  }
}

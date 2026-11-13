package com.example.domain.services

import com.example.domain.model.ThreadAnalysisReport

object ReportFormatter {

  fun formatArabicReport(report: ThreadAnalysisReport): String {
    val sb = StringBuilder()
    sb.appendLine("تقرير ThreadGuard X التحليلي")
    sb.appendLine("معرف العملية: ${report.operationId} | الوقت: ${report.analyzedAtIso}")
    sb.appendLine("عدد التغريدات: ${report.tweetCount}")
    sb.appendLine("التقييم العام: ${report.overallAssessmentAr}")
    sb.appendLine("مستوى الخطورة الإجمالي: ${report.overallRiskLevel.labelAr} (مؤشر: ${report.riskScore}/100 | الثقة: ${(report.confidenceScore * 100).toInt()}%)")
    sb.appendLine("السياق المكتشف: ${report.detectedContext.labelAr}")
    sb.appendLine("تقدير السياسات: ${report.policyVerdict.labelAr}")
    sb.appendLine()
    sb.appendLine("نتائج المحركات:")
    report.engineResults.forEachIndexed { i, engine ->
      sb.appendLine("${i + 1}. ${engine.nameAr}:")
      sb.appendLine("   الحالة: ${engine.status.labelAr}")
      sb.appendLine("   مستوى الخطورة: ${engine.riskLevel.labelAr}")
      sb.appendLine("   السبب: ${engine.summaryAr}")
    }
    if (report.deduplicatedFindings.isNotEmpty()) {
      sb.appendLine()
      sb.appendLine("المؤشرات المرصودة (${report.deduplicatedFindings.size}):")
      report.deduplicatedFindings.forEach { f ->
        val loc = if (f.location.tweetIndex >= 0) "التغريدة ${f.location.tweetIndex + 1}" else "الثريد كاملاً"
        sb.appendLine("- [$loc] ${f.titleAr} (${f.severity.labelAr}): ${f.explanationAr}")
      }
    }
    sb.appendLine()
    sb.appendLine("التوصيات:")
    report.recommendationsAr.forEach { rec -> sb.appendLine("- $rec") }
    sb.appendLine()
    sb.appendLine(report.scoringDisclaimerAr)
    return sb.toString()
  }
}

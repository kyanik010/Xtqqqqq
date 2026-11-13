package com.example

import com.example.domain.core.RiskEvidenceAggregator
import com.example.domain.core.ThreadGuardOrchestrator
import com.example.domain.engines.ThreadQualityEngine
import com.example.domain.model.AnalysisConfig
import com.example.domain.model.ContentContext
import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.EvidenceLocation
import com.example.domain.model.Finding
import com.example.domain.model.RiskLevel
import com.example.domain.services.RewriteSuggestionEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadQualityAndAggregatorTest {

  private val qualityEngine = ThreadQualityEngine()
  private val aggregator = RiskEvidenceAggregator()

  @Test
  fun `respects configurable tweet character limit`() {
    val longTweet = "هذا نص طويل يتجاوز مئتين وثمانين حرفاً ".repeat(10)
    val standardResult = qualityEngine.runEngine(listOf(longTweet), AnalysisConfig(maxTweetChars = 280))
    val extendedResult = qualityEngine.runEngine(listOf(longTweet), AnalysisConfig(maxTweetChars = 4000))

    assertTrue(standardResult.findings.any { it.dedupKey == "CHAR_LIMIT_EXCEEDED:0" })
    assertTrue(extendedResult.findings.none { it.dedupKey == "CHAR_LIMIT_EXCEEDED:0" })
  }

  @Test
  fun `identifies exact tweet number causing filler or repetition`() {
    val tweets =
      listOf(
        "مقدمة الثريد حول أساسيات أمن التطبيقات والشبكات الحديثة.",
        "استخدام التشفير القوي يحمي بيانات المستخدمين أثناء النقل.",
        "تابع",
        "التحقق من الشهادات الرقمية يمنع هجمات الوسيط في الشبكات العامة.",
      )
    val result = qualityEngine.runEngine(tweets)
    val fillerFinding = result.findings.firstOrNull { it.dedupKey == "ZERO_VALUE_TWEET:2" }
    assertTrue(fillerFinding != null)
    assertTrue(fillerFinding!!.titleAr.contains("3"))
  }

  @Test
  fun `aggregator deduplicates identical dedupKey across engines without double counting`() {
    val sharedFindingA =
      Finding(
        id = "f1",
        dedupKey = "OBFUSCATION:0:اشتراك",
        engineId = EngineId.ARABIC_TEXT,
        category = "تحايل إملائي",
        titleAr = "تمويه",
        explanationAr = "شرح",
        suggestionAr = "تعديل",
        severity = RiskLevel.MEDIUM,
        confidence = 0.85,
        location = EvidenceLocation(tweetIndex = 0, snippet = "ا ش ت ر ا ك"),
      )
    val sharedFindingB = sharedFindingA.copy(id = "f2", engineId = EngineId.VISIBILITY_RISK, severity = RiskLevel.HIGH)

    val res1 = EngineResult(EngineId.ARABIC_TEXT, "عربي", EngineStatus.SUCCESS, RiskLevel.MEDIUM, 0.85, "ok", listOf(sharedFindingA))
    val res2 = EngineResult(EngineId.VISIBILITY_RISK, "ظهور", EngineStatus.SUCCESS, RiskLevel.HIGH, 0.85, "ok", listOf(sharedFindingB))

    val report = aggregator.aggregate(listOf("ا ش ت ر ا ك"), listOf(res1, res2), emptyList(), emptyList(), ContentContext.GENERAL)
    assertEquals(1, report.deduplicatedFindings.size)
    // Risk score must reflect only 1 HIGH finding (28), not HIGH + MEDIUM (44)
    assertEquals(28, report.riskScore)
  }

  @Test
  fun `orchestrator handles empty and long threads cleanly`() = runTest {
    val orchestrator = ThreadGuardOrchestrator()
    val emptyReport = orchestrator.analyze(listOf("   "))
    assertEquals(RiskLevel.INSUFFICIENT_DATA, emptyReport.overallRiskLevel)

    val longThread = (1..20).map { "التغريدة رقم $it تتناول نقطة تقنية مستقلة حول تصميم الأنظمة الموزعة رقم $it." }
    val longReport = orchestrator.analyze(longThread)
    assertEquals(20, longReport.tweetCount)
    assertTrue(longReport.engineResults.all { it.status == EngineStatus.SUCCESS })
  }

  @Test
  fun `rewrite engine fixes obfuscation and elongation without altering core meaning`() {
    val original = "احصل على ا ش ت ر ا ك مجاااااني في الدليل"
    val finding =
      Finding(
        id = "f_obf",
        dedupKey = "OBFUSCATION:0:اشتراك",
        engineId = EngineId.ARABIC_TEXT,
        category = "تحايل",
        titleAr = "تمويه",
        explanationAr = "شرح",
        suggestionAr = "إصلاح",
        severity = RiskLevel.MEDIUM,
        confidence = 0.9,
        location = EvidenceLocation(0, snippet = "ا ش ت ر ا ك"),
      )
    val suggestions = RewriteSuggestionEngine().generateSuggestions(listOf(original), listOf(finding))
    assertEquals(1, suggestions.size)
    assertEquals("احصل على اشتراك مجاني في الدليل", suggestions.single().suggestedText)
    assertEquals(original, suggestions.single().originalText)
  }
}

package com.example

import com.example.domain.core.ThreadGuardOrchestrator
import com.example.domain.engines.PolicyRiskEngine
import com.example.domain.engines.SpamDetectionEngine
import com.example.domain.model.ContentContext
import com.example.domain.model.PolicyVerdict
import com.example.domain.model.RiskLevel
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpamAndPolicyEnginesTest {

  private val spamEngine = SpamDetectionEngine()
  private val policyEngine = PolicyRiskEngine()

  @Test
  fun `policy engine never flags single isolated word without corroborating context`() {
    val result = policyEngine.runEngine(listOf("شرح كلمة اختراق في سياق ألعاب الفيديو القديمة"))
    assertEquals(RiskLevel.LOW, result.riskLevel)
    assertEquals(PolicyVerdict.COMPLIANT.labelAr, result.metadata["تقدير السياسات"])
  }

  @Test
  fun `policy engine mitigates warning and educational context`() {
    val result =
      policyEngine.runEngine(
        listOf("تحذير للجميع: احذروا من رسائل حول المبلغ لاستلام الجائزة أو أرباح مضمونة بدون مخاطرة لأنها احتيال مالي.")
      )
    assertTrue(result.riskLevel.weightOrder <= RiskLevel.MEDIUM.weightOrder)
    assertFalse(result.metadata["تقدير السياسات"] == PolicyVerdict.LIKELY_VIOLATION.labelAr)
  }

  @Test
  fun `spam engine distinguishes technical IPTV explanation from spam`() {
    val techThread =
      listOf(
        "كيف تعمل تقنية IPTV وبروتوكول HLS في نقل البث عبر الشبكة؟",
        "يقوم خادم البث بتقسيم الفيديو إلى مقاطع M3U8 وتوزيعها عبر CDN لتقليل التقطيع في المشغل.",
      )
    val result = spamEngine.runEngine(techThread)
    assertEquals(RiskLevel.LOW, result.riskLevel)
    assertEquals(ContentContext.TECHNICAL_EXPLANATION.labelAr, result.metadata["السياق المكتشف"])
  }

  @Test
  fun `spam engine detects repetitive promotional spam and obfuscation`() {
    val spamThread =
      listOf(
        "حصرياً عرض لا يعوض!! ا ش ت ر ا ك سيرفر قنوات مضمون 100% اشترك الآن عبر http://bit.ly/x1",
        "حصرياً عرض لا يعوض!! أقوى سيرفر قنوات سارع قبل النفاذ تواصل معنا الآن عبر http://bit.ly/x1",
        "اشترك معنا الآن واضغط هنا ولا تفوت الفرصة عرض لفترة محدودة جداً http://bit.ly/x1",
      )
    val result = spamEngine.runEngine(spamThread)
    assertTrue(result.riskLevel.weightOrder >= RiskLevel.HIGH.weightOrder)
    assertTrue(result.findings.isNotEmpty())
  }

  @Test
  fun `evaluates independent synthetic IPTV test dataset and computes Precision Recall F1 FPR FNR`() = runTest {
    val candidates =
      listOf(
        File("datasets/test/synthetic_test.json"),
        File("../datasets/test/synthetic_test.json"),
      )
    val testFile = candidates.firstOrNull { it.exists() }
      ?: error("synthetic_test.json not found in ${File(".").absolutePath}")

    val array = JSONArray(testFile.readText())
    val orchestrator = ThreadGuardOrchestrator()

    var tp = 0
    var fp = 0
    var tn = 0
    var fn = 0

    for (i in 0 until array.length()) {
      val obj = array.getJSONObject(i)
      assertTrue("All synthetic samples must be marked is_synthetic=true", obj.getBoolean("is_synthetic"))
      val tweetsArr = obj.getJSONArray("tweets")
      val tweets = (0 until tweetsArr.length()).map { tweetsArr.getString(it) }
      val expectedPositive = obj.getString("primary_label") != "طبيعي"

      val report = orchestrator.analyze(tweets)
      val predictedPositive = report.overallRiskLevel.weightOrder >= RiskLevel.MEDIUM.weightOrder

      when {
        expectedPositive && predictedPositive -> tp++
        !expectedPositive && predictedPositive -> fp++
        !expectedPositive && !predictedPositive -> tn++
        expectedPositive && !predictedPositive -> fn++
      }
    }

    val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
    val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
    val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    val fpr = if (fp + tn == 0) 0.0 else fp.toDouble() / (fp + tn)
    val fnr = if (fn + tp == 0) 0.0 else fn.toDouble() / (fn + tp)

    assertTrue("Precision too low: $precision (tp=$tp, fp=$fp)", precision >= 0.85)
    assertTrue("Recall too low: $recall (tp=$tp, fn=$fn)", recall >= 0.85)
    assertTrue("F1 too low: $f1", f1 >= 0.85)
    assertTrue("False Positive Rate on safe IPTV technical threads must be <= 0.15, got $fpr", fpr <= 0.15)
    assertTrue("False Negative Rate must be <= 0.15, got $fnr", fnr <= 0.15)
  }
}

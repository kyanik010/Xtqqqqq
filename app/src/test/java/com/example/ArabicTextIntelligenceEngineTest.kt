package com.example

import com.example.domain.engines.ArabicTextIntelligenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTextIntelligenceEngineTest {

  private val engine = ArabicTextIntelligenceEngine()

  @Test
  fun `normalizes hamza tashkeel tatweel and digits without modifying original text`() {
    val original = "إِشْـتِرَاكٌ ١٢٣ أَحْمَدُ"
    val normalized = engine.normalize(original)

    assertEquals("اشتراك 123 احمد", normalized)
    // Original must remain untouched
    assertEquals("إِشْـتِرَاكٌ ١٢٣ أَحْمَدُ", original)
  }

  @Test
  fun `preserves morphologically distinct Arabic letters during normalization`() {
    assertEquals("مدرسة", engine.normalize("مدرسة"))
    assertEquals("مدرسه", engine.normalize("مدرسه"))
    assertFalse(engine.normalize("مدرسة") == engine.normalize("مدرسه"))
    assertEquals("مسؤول", engine.normalize("مسؤول"))
    assertEquals("مسول", engine.normalize("مسول"))
    assertFalse(engine.normalize("مسؤول") == engine.normalize("مسول"))
    assertEquals("بيئة", engine.normalize("بيئة"))
    assertEquals("بيه", engine.normalize("بيه"))
    assertFalse(engine.normalize("بيئة") == engine.normalize("بيه"))
  }

  @Test
  fun `reports lexical similarity without claiming semantic understanding`() {
    assertTrue(engine.computeLexicalSimilarity("اشتراك", "اشتراكات") >= 0.45)
    assertTrue(engine.computeLexicalSimilarity("اشتراك", "اشترك") >= 0.45)
    assertTrue(engine.computeLexicalSimilarity("شرح تقنية IPTV", "شرح تقنية IPTV") == 1.0)
    assertTrue(engine.computeLexicalSimilarity("اشتراك", "طائرة") < 0.4)
    assertTrue(engine.computeLexicalSimilarity("مدرسة", "مدرسه") < 1.0)
  }

  @Test
  fun `detects spaced letters symbol interleaving and tatweel obfuscation`() {
    val text = "احصل على ا ش ت ر ا ك أو ا.ش.ت.ر.ا.ك أو اشـتـراك الآن"
    val obf = engine.detectObfuscation(text)

    assertTrue(obf.any { it.kind == "SPACED_LETTERS" && it.reconstructed == "اشتراك" })
    assertTrue(obf.any { it.kind == "SYMBOL_INTERLEAVED" && it.reconstructed == "اشتراك" })
    assertTrue(obf.any { it.kind == "TATWEEL_OBFUSCATION" && it.reconstructed == "اشتراك" })
  }

  @Test
  fun `detects repeated character elongation and collapses cleanly`() {
    val matches = engine.detectRepeatedCharacters("عرض مجاااااني وسيرفر سرررريع")
    assertEquals(2, matches.size)
    assertEquals("مجاني", matches[0].collapsed)
    assertEquals("سريع", matches[1].collapsed)
  }

  @Test
  fun `detects mixed Arabic English and dialect markers`() {
    val profile = engine.detectLanguageProfile("عشان تشغل HLS على المشغل لازم تضبط DNS كويس")
    assertTrue(profile.isMixedArabicEnglish)
    assertTrue(profile.detectedDialectMarkers.any { it.contains("عشان") })
  }

  @Test
  fun `computes high similarity for variants without falsely claiming exact equality`() {
    val forms = listOf("اشتراك", "إشتراك", "اشـتراك", "اشترك", "اشتراكات")
    for (i in forms.indices) {
      for (j in (i + 1) until forms.size) {
        val sim = engine.computeLexicalSimilarity(forms[i], forms[j])
        assertTrue("Expected noticeable similarity between ${forms[i]} and ${forms[j]}, got $sim", sim >= 0.45)
        assertTrue("Variants ${forms[i]} and ${forms[j]} must not score 1.0", sim < 1.0)
      }
    }
    assertFalse(engine.computeLexicalSimilarity("اشتراك", "طائرة") >= 0.4)
  }

  @Test
  fun `splits glued mixed and compound Arabic tokens`() {
    val tokens = engine.tokenize("سيرفرIPTV رابطالموقع")
    val splits = engine.splitConcatenatedWords(tokens)
    assertTrue(splits.containsKey("سيرفرIPTV"))
    assertEquals(listOf("سيرفر", "IPTV"), splits["سيرفرIPTV"])
    assertTrue(splits.containsKey("رابطالموقع"))
  }
}

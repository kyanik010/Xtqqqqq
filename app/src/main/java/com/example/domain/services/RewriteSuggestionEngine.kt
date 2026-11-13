package com.example.domain.services

import com.example.domain.engines.ArabicTextIntelligenceEngine
import com.example.domain.model.Finding
import com.example.domain.model.RewriteSuggestion

/**
 * Generates non-destructive rewrite suggestions without altering original meaning
 * and never applies edits automatically without explicit user approval.
 */
class RewriteSuggestionEngine(
  private val arabicEngine: ArabicTextIntelligenceEngine = ArabicTextIntelligenceEngine(),
) {

  fun generateSuggestions(tweets: List<String>, findings: List<Finding>): List<RewriteSuggestion> {
    val suggestions = mutableListOf<RewriteSuggestion>()
    val byTweet = findings.filter { it.location.tweetIndex in tweets.indices }.groupBy { it.location.tweetIndex }

    for ((idx, tweetFindings) in byTweet) {
      val original = tweets[idx]
      if (original.isBlank()) continue

      var candidate = original
      val reasons = mutableListOf<String>()

      // 1. Clean obfuscated snippets (spaced letters, symbol-interleaved, tatweel obfuscation, zero-width chars)
      val analysis = arabicEngine.analyzeTweet(original)
      if (analysis.obfuscationMatches.isNotEmpty()) {
        for (obf in analysis.obfuscationMatches) {
          if (candidate.contains(obf.snippet)) {
            candidate = candidate.replace(obf.snippet, obf.reconstructed)
          }
        }
        reasons.add("استبدال التمويه الكتابي وفصل الأحرف بالصيغة القياسية دون تغيير المعنى.")
      }

      // 2. Collapse unnatural repeated characters (3+ identical letters -> 1)
      if (analysis.repeatedCharMatches.isNotEmpty()) {
        for (rep in analysis.repeatedCharMatches) {
          if (candidate.contains(rep.snippet)) {
            candidate = candidate.replace(rep.snippet, rep.collapsed)
          }
        }
        reasons.add("إزالة التمديد المفرط للحروف لتحسين احترافية النص.")
      }

      // 3. Trim excessive emojis (> 4) while keeping at most 2
      if (analysis.languageProfile.emojiCount >= 6) {
        val emojiRegex = Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]+""")
        var kept = 0
        candidate =
          emojiRegex.replace(candidate) { m ->
            kept++
            if (kept <= 2) m.value else ""
          }.replace(Regex(" {2,}"), " ").trim()
        reasons.add("تقليل الرموز التعبيرية المزدحمة مع الإبقاء على رمز دلالي واضح.")
      }

      // 4. Trim duplicated CTA if this tweet is not the final tweet and CTA repeat was flagged
      val hasCtaRepeat = tweetFindings.any { it.dedupKey == "REPEATED_CTA_THREAD" }
      if (hasCtaRepeat && idx < tweets.lastIndex) {
        val ctaRegex = Regex("""(اشترك الآن|اشترك معنا|تواصل معنا الآن|راسلنا خاص|اضغط هنا)[^.!؟\n]*""", RegexOption.IGNORE_CASE)
        val trimmed = ctaRegex.replace(candidate, "").replace(Regex(" {2,}"), " ").trim()
        if (trimmed.isNotBlank() && trimmed != candidate) {
          candidate = trimmed
          reasons.add("نقل الدعوة المتكررة للاشتراك خارج التغريدة الوسطية لتخفيف الطابع الترويجي.")
        }
      }

      if (candidate.trim() != original.trim() && candidate.isNotBlank()) {
        suggestions.add(
          RewriteSuggestion(
            id = "rew_$idx",
            tweetIndex = idx,
            originalText = original,
            suggestedText = candidate.trim(),
            reasonAr = reasons.distinct().joinToString(" "),
            findingId = tweetFindings.first().id,
          )
        )
      }
    }

    return suggestions
  }
}

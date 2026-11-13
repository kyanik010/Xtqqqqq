package com.example.domain.engines

import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.EvidenceLocation
import com.example.domain.model.Finding
import com.example.domain.model.RiskLevel
import java.util.Locale
import kotlin.math.sqrt

data class LanguageProfile(
  val arabicCharCount: Int,
  val latinCharCount: Int,
  val digitCount: Int,
  val emojiCount: Int,
  val punctuationCount: Int,
  val arabicRatio: Double,
  val isMixedArabicEnglish: Boolean,
  val detectedDialectMarkers: List<String>,
  val primaryLabelAr: String,
)

data class ObfuscationMatch(
  val kind: String,
  val snippet: String,
  val reconstructed: String,
  val startOffset: Int,
  val endOffset: Int,
)

data class RepeatedCharMatch(
  val snippet: String,
  val collapsed: String,
  val maxRepeatRun: Int,
  val startOffset: Int,
  val endOffset: Int,
)

data class ArabicTextAnalysis(
  val originalText: String,
  val normalizedText: String,
  val tokens: List<String>,
  val normalizedTokens: List<String>,
  val sentences: List<String>,
  val languageProfile: LanguageProfile,
  val characterVariations: List<String>,
  val obfuscationMatches: List<ObfuscationMatch>,
  val repeatedCharMatches: List<RepeatedCharMatch>,
  val concatenatedSplits: Map<String, List<String>>,
)

/**
 * Arabic Text Intelligence Engine:
 * Performs internal-only normalization while preserving original text.
 * Supports MSA, dialects, colloquial, mixed Arabic/English, technical/marketing terms,
 * Arabic/Western digits, Tashkeel, Hamza, Tatweel, homoglyphs, obfuscation, and elongation.
 */
class ArabicTextIntelligenceEngine {

  companion object {
    private val TASHKEEL_REGEX = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]")
    private val ZERO_WIDTH_REGEX = Regex("[\u200B-\u200F\u202A-\u202E\uFEFF]")
    private val TATWEEL_CHAR = '\u0640'
    private val URL_REGEX = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
    private val TOKEN_REGEX = Regex("""https?://\S+|[#@]?[\p{L}\p{N}_\-]+""")
    private val SENTENCE_SPLIT_REGEX = Regex("""(?<=[.!؟?!\n؛])\s+|\n+""")

    // Spaced-out single Arabic letters: e.g. "ا ش ت ر ا ك" (at least 3 isolated letters)
    private val SPACED_ARABIC_LETTERS_REGEX =
      Regex("""(?:^|(?<=\s))([\u0621-\u064A](?:\s+[\u0621-\u064A]){2,})(?=$|\s|[.!؟،])""")

    // Symbol-interleaved Arabic words: e.g. "ا.ش.ت.ر.ا.ك" or "اش*تراك"
    private val SYMBOL_INTERLEAVED_REGEX =
      Regex("""[\u0621-\u064A]+(?:[.\-*_/\\|~]+[\u0621-\u064A]+){2,}""")

    // Elongated characters (3+ identical letters in a row, Arabic or Latin)
    private val REPEATED_CHAR_REGEX = Regex("""([\p{L}])\1{2,}""")

    // Persian / Urdu / lookalike variants inside Arabic words
    private val VARIANT_CHARS =
      mapOf(
        '\u06A9' to 'ك', // Keheh
        '\u06CC' to 'ي', // Farsi Yeh
        '\u06C1' to 'ه', // Heh Goal
        '\u06D2' to 'ي', // Yeh Barree
        '\u0629' to 'ة',
      )

    private val DIALECT_MARKERS =
      mapOf(
        "عشان" to "عامية مصرية/خليجية",
        "علشان" to "عامية مصرية/خليجية",
        "كده" to "عامية مصرية",
        "ازاي" to "عامية مصرية",
        "دلوقتي" to "عامية مصرية",
        "وش" to "عامية خليجية",
        "ابي" to "عامية خليجية",
        "ابغى" to "عامية خليجية",
        "حق" to "عامية خليجية",
        "هسا" to "عامية شامية",
        "شلون" to "عامية خليجية/عراقية",
        "شو" to "عامية شامية",
        "مو" to "عامية خليجية/عراقية",
        "مره" to "عامية خليجية",
        "واجد" to "عامية خليجية",
        "بزاف" to "عامية مغاربية",
        "برشا" to "عامية مغاربية",
      )

    private val KNOWN_PREFIXES = listOf("ال", "وال", "بال", "كال", "فال", "لل", "وبال")
    private val KNOWN_COMPOUND_ROOTS =
      listOf(
        "اشتراك",
        "سيرفر",
        "رابط",
        "موقع",
        "تطبيق",
        "قناة",
        "قنوات",
        "مشغل",
        "تحميل",
        "مباشر",
        "حصري",
        "مجاني",
        "تواصل",
        "خدمة",
        "سعر",
        "عرض",
      )
  }

  /**
   * Normalizes text strictly for internal analysis (original text is never overwritten).
   */
  fun normalize(text: String, collapseRepeats: Boolean = true): String {
    if (text.isEmpty()) return ""
    val sb = StringBuilder(text.length)
    for (ch in text) {
      when {
        ch == TATWEEL_CHAR -> continue
        ch in '\u0610'..'\u061A' || ch in '\u064B'..'\u065F' || ch == '\u0670' || ch in '\u06D6'..'\u06ED' -> continue
        ch in '\u200B'..'\u200F' || ch in '\u202A'..'\u202E' || ch == '\uFEFF' -> continue
        ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
        ch == 'ى' -> sb.append('ي')
        // Preserve morphologically meaningful Arabic letters. Ta Marbuta (ة), Hamza-on-Waw
        // (ؤ), and Hamza-on-Ya (ئ) are not interchangeable with ه/و/ي.
        ch == '\u06A9' -> sb.append('ك')
        ch == '\u06CC' || ch == '\u06D2' -> sb.append('ي')
        ch == '\u06C1' -> sb.append('ه')
        ch in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
        ch in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
        else -> sb.append(ch.lowercaseChar())
      }
    }
    var result = sb.toString()
    if (collapseRepeats) {
      result = result.replace(REPEATED_CHAR_REGEX) { match ->
        match.groupValues[1]
      }
    }
    return result.replace(Regex("\\s+"), " ").trim()
  }

  /**
   * Produces a soft Arabic stem/lemma approximation for comparing variants such as
   * اشتراك / إشتراك / اشـتراك / اشترك / اشتراكات without falsely treating them as identical.
   */
  fun softStemArabic(token: String): String {
    var s = normalize(token)
    if (s.length <= 3) return s
    for (prefix in listOf("وال", "بال", "كال", "فال", "لل", "ال")) {
      if (s.startsWith(prefix) && s.length - prefix.length >= 3) {
        s = s.removePrefix(prefix)
        break
      }
    }
    for (suffix in listOf("ات", "ون", "ين", "ان", "هم", "كم", "نا", "ها", "ه")) {
      if (s.endsWith(suffix) && s.length - suffix.length >= 3) {
        s = s.removeSuffix(suffix)
        break
      }
    }
    return s
  }

  /**
   * Extracts a lightweight Arabic consonant skeleton for morphological family matching
   * (e.g. اشتراك / إشتراك / اشترك / اشتراكات -> شترك), while leaving short or non-Arabic words intact.
   */
  fun arabicMorphSkeleton(token: String): String {
    val stem = softStemArabic(token)
    if (stem.length < 4 || !stem.all { it in '\u0621'..'\u064A' }) return stem
    // Strip leading prosthetic alef (اشتراك -> شتراك, اشترك -> شترك) and internal long vowel ا/ي/و
    val withoutLeadingAlef = if (stem.startsWith("ا") && stem.length >= 4) stem.substring(1) else stem
    val consonants = StringBuilder()
    withoutLeadingAlef.forEachIndexed { i, ch ->
      if (i == 0 || (ch != 'ا' && ch != 'ي' && ch != 'و')) {
        consonants.append(ch)
      }
    }
    return if (consonants.length >= 3) consonants.toString() else withoutLeadingAlef
  }

  fun tokenize(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return TOKEN_REGEX.findAll(text).map { it.value }.toList()
  }

  fun segmentSentences(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return text.split(SENTENCE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
  }

  fun detectLanguageProfile(text: String): LanguageProfile {
    var arabic = 0
    var latin = 0
    var digits = 0
    var emojis = 0
    var punctuation = 0

    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val charCount = Character.charCount(cp)
      when {
        cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF -> arabic++
        (cp in 'A'.code..'Z'.code) || (cp in 'a'.code..'z'.code) -> latin++
        (cp in '0'.code..'9'.code) || (cp in 0x0660..0x0669) || (cp in 0x06F0..0x06F9) -> digits++
        cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF -> emojis++
        Character.getType(cp) in
          setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
          ) -> punctuation++
      }
      i += charCount
    }

    val letterTotal = (arabic + latin).coerceAtLeast(1)
    val arabicRatio = arabic.toDouble() / letterTotal
    val isMixed = arabic >= 3 && latin >= 3

    val normalizedTokens = tokenize(normalize(URL_REGEX.replace(text, " ")))
    val dialectHits =
      normalizedTokens.mapNotNull { tok -> DIALECT_MARKERS[tok]?.let { "$tok ($it)" } }.distinct()

    val label =
      when {
        arabic == 0 && latin == 0 -> "رموز أو أرقام فقط"
        isMixed -> "عربي مختلط مع مصطلحات إنجليزية"
        dialectHits.isNotEmpty() && arabic > latin -> "عربي بلهجة عامية"
        arabic >= latin -> "عربي فصيح / قياسي"
        else -> "إنجليزي / لاتيني غالب"
      }

    return LanguageProfile(
      arabicCharCount = arabic,
      latinCharCount = latin,
      digitCount = digits,
      emojiCount = emojis,
      punctuationCount = punctuation,
      arabicRatio = arabicRatio,
      isMixedArabicEnglish = isMixed,
      detectedDialectMarkers = dialectHits,
      primaryLabelAr = label,
    )
  }

  fun detectCharacterVariations(text: String): List<String> {
    val variations = mutableSetOf<String>()
    if (text.contains(TATWEEL_CHAR)) {
      variations.add("تطويل (كشيدة) داخل الكلمات")
    }
    if (TASHKEEL_REGEX.containsMatchIn(text)) {
      variations.add("وجود تشكيل (حركات)")
    }
    if (ZERO_WIDTH_REGEX.containsMatchIn(text)) {
      variations.add("أحرف تحكم غير مرئية (Zero-Width)")
    }
    if (text.any { it in '\u0660'..'\u0669' } && text.any { it in '0'..'9' }) {
      variations.add("خلط الأرقام العربية والإنجليزية")
    }
    for ((variantChar, standardChar) in VARIANT_CHARS) {
      if (variantChar != standardChar && text.contains(variantChar)) {
        variations.add("حرف بديل غير قياسي ($variantChar بدل $standardChar)")
      }
    }
    // Check mixed Cyrillic/Latin lookalikes inside Arabic-heavy text
    val hasCyrillic = text.any { it in '\u0400'..'\u04FF' }
    if (hasCyrillic) {
      variations.add("أحرف سيريلية متشابهة شكلياً")
    }
    return variations.toList()
  }

  fun detectObfuscation(text: String): List<ObfuscationMatch> {
    val matches = mutableListOf<ObfuscationMatch>()

    SPACED_ARABIC_LETTERS_REGEX.findAll(text).forEach { m ->
      val snippet = m.value
      val reconstructed = snippet.replace(Regex("\\s+"), "")
      if (reconstructed.length >= 3) {
        matches.add(
          ObfuscationMatch(
            kind = "SPACED_LETTERS",
            snippet = snippet,
            reconstructed = normalize(reconstructed),
            startOffset = m.range.first,
            endOffset = m.range.last + 1,
          )
        )
      }
    }

    SYMBOL_INTERLEAVED_REGEX.findAll(text).forEach { m ->
      val snippet = m.value
      val reconstructed = snippet.replace(Regex("[.\\-*_/\\\\|~]+"), "")
      matches.add(
        ObfuscationMatch(
          kind = "SYMBOL_INTERLEAVED",
          snippet = snippet,
          reconstructed = normalize(reconstructed),
          startOffset = m.range.first,
          endOffset = m.range.last + 1,
        )
      )
    }

    // Tatweel-heavy obfuscation (>= 2 tatweels in a single word)
    Regex("""[\u0621-\u064A]*\u0640[\u0621-\u064A\u0640]*\u0640[\u0621-\u064A]*""").findAll(text).forEach { m ->
      matches.add(
        ObfuscationMatch(
          kind = "TATWEEL_OBFUSCATION",
          snippet = m.value,
          reconstructed = normalize(m.value),
          startOffset = m.range.first,
          endOffset = m.range.last + 1,
        )
      )
    }

    // Zero-width character insertion inside words
    Regex("""\S*[\u200B-\u200F\uFEFF]\S*""").findAll(text).forEach { m ->
      matches.add(
        ObfuscationMatch(
          kind = "ZERO_WIDTH_OBFUSCATION",
          snippet = m.value,
          reconstructed = normalize(m.value),
          startOffset = m.range.first,
          endOffset = m.range.last + 1,
        )
      )
    }

    return matches.distinctBy { it.startOffset to it.endOffset }
  }

  fun detectRepeatedCharacters(text: String): List<RepeatedCharMatch> {
    val results = mutableListOf<RepeatedCharMatch>()
    val wordRegex = Regex("""[\p{L}]+""")
    wordRegex.findAll(text).forEach { wordMatch ->
      val word = wordMatch.value
      val match = REPEATED_CHAR_REGEX.find(word)
      if (match != null) {
        val maxRun = REPEATED_CHAR_REGEX.findAll(word).maxOf { it.value.length }
        val collapsed = word.replace(REPEATED_CHAR_REGEX) { it.groupValues[1] }
        results.add(
          RepeatedCharMatch(
            snippet = word,
            collapsed = collapsed,
            maxRepeatRun = maxRun,
            startOffset = wordMatch.range.first,
            endOffset = wordMatch.range.last + 1,
          )
        )
      }
    }
    return results
  }

  fun splitConcatenatedWords(tokens: List<String>): Map<String, List<String>> {
    val splits = linkedMapOf<String, List<String>>()
    for (tok in tokens) {
      if (tok.startsWith("http", ignoreCase = true)) continue
      // Mixed Arabic-Latin glued token like "IPTVسيرفر" or "سيرفرIPTV"
      val mixedSplit =
        Regex("""([A-Za-z0-9]+)|([\u0600-\u06FF]+)""").findAll(tok).map { it.value }.toList()
      if (mixedSplit.size >= 2) {
        splits[tok] = mixedSplit
        continue
      }
      // Glued Arabic compound words (length >= 9) containing 2 known roots
      val norm = normalize(tok)
      if (norm.length >= 9 && norm.all { it in '\u0621'..'\u064A' }) {
        val foundParts = splitArabicCompound(norm)
        if (foundParts.size >= 2) {
          splits[tok] = foundParts
        }
      }
    }
    return splits
  }

  private fun splitArabicCompound(normWord: String): List<String> {
    val clean =
      if (normWord.length >= 10 && (normWord.startsWith("و") || normWord.startsWith("ف") || normWord.startsWith("ب"))) {
        normWord.substring(1)
      } else normWord

    for (root in KNOWN_COMPOUND_ROOTS) {
      val idx = clean.indexOf(root)
      if (idx >= 2 && idx + root.length <= clean.length) {
        val left = clean.substring(0, idx)
        val right = clean.substring(idx)
        if (left.length >= 3 && right.length >= 3) {
          return listOf(left, right)
        }
      } else if (idx == 0 && clean.length - root.length >= 4) {
        val rest = clean.substring(root.length)
        if (KNOWN_COMPOUND_ROOTS.any { rest.contains(it) }) {
          return listOf(root, rest)
        }
      }
    }
    return emptyList()
  }

  /**
   * Computes lexical/structural similarity, NOT semantic meaning similarity.
   *
   * Uses conservative normalization, one-to-one token matching, a bounded Arabic
   * morphology hint, edit similarity, and character n-gram overlap. It can identify
   * near-duplicates and inflectional variants, but it cannot infer that unrelated wording
   * expresses the same idea. A trained sentence-embedding model is required for that.
   */
  fun computeLexicalSimilarity(textA: String, textB: String): Double {
    val normA = normalize(URL_REGEX.replace(textA, " "))
    val normB = normalize(URL_REGEX.replace(textB, " "))
    if (normA.isBlank() || normB.isBlank()) return 0.0
    if (normA == normB) return if (textA == textB) 1.0 else 0.98

    val tokensA = tokenize(normA).map(::softStemArabic).filter { it.length >= 3 }
    val tokensB = tokenize(normB).map(::softStemArabic).filter { it.length >= 3 }
    if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

    val unmatched = tokensB.toMutableList()
    var matchedScore = 0.0
    for (tokenA in tokensA) {
      if (unmatched.isEmpty()) break
      val best = unmatched.mapIndexed { index, tokenB ->
        index to tokenSimilarity(tokenA, tokenB)
      }.maxByOrNull { it.second } ?: break
      if (best.second >= 0.62) {
        matchedScore += best.second
        unmatched.removeAt(best.first)
      }
    }
    val tokenCoverage = (2.0 * matchedScore) / (tokensA.size + tokensB.size).coerceAtLeast(1)

    val gramsA = charNGrams(normA, 3)
    val gramsB = charNGrams(normB, 3)
    val ngramCosine = cosineSimilarity(gramsA, gramsB)

    // Token overlap dominates; character overlap is only supporting evidence.
    return (0.80 * tokenCoverage + 0.20 * ngramCosine).coerceIn(0.0, 1.0)
  }

  private fun tokenSimilarity(tokenA: String, tokenB: String): Double {
    if (tokenA == tokenB) return 1.0

    val skeletonA = arabicMorphSkeleton(tokenA)
    val skeletonB = arabicMorphSkeleton(tokenB)
    if (skeletonA.length >= 3 && skeletonA == skeletonB) return 0.88

    val maxLength = maxOf(tokenA.length, tokenB.length).coerceAtLeast(1)
    val editSimilarity = 1.0 - levenshteinDistance(tokenA, tokenB).toDouble() / maxLength
    if (editSimilarity < 0.62) return 0.0
    return editSimilarity
  }

  private fun levenshteinDistance(a: String, b: String): Int {
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      val current = IntArray(b.length + 1)
      current[0] = i
      for (j in 1..b.length) {
        current[j] = minOf(
          previous[j] + 1,
          current[j - 1] + 1,
          previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
        )
      }
      previous = current
    }
    return previous[b.length]
  }

  /**
   * Backward-compatible alias. This method has never used a semantic model;
   * callers should migrate to computeLexicalSimilarity().
   */
  @Deprecated("This is lexical similarity, not semantic similarity. Use computeLexicalSimilarity().")
  fun computeSemanticSimilarity(textA: String, textB: String): Double =
    computeLexicalSimilarity(textA, textB)

  private fun charNGrams(text: String, n: Int): Map<String, Int> {
    val compact = text.replace(Regex("\\s+"), " ").trim()
    if (compact.length < n) return if (compact.isEmpty()) emptyMap() else mapOf(compact to 1)
    val freq = HashMap<String, Int>()
    for (i in 0..(compact.length - n)) {
      val gram = compact.substring(i, i + n)
      freq[gram] = (freq[gram] ?: 0) + 1
    }
    return freq
  }

  private fun cosineSimilarity(a: Map<String, Int>, b: Map<String, Int>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for ((k, v) in a) {
      normA += v * v
      val bv = b[k] ?: 0
      dot += v * bv
    }
    for (v in b.values) {
      normB += v * v
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom == 0.0) 0.0 else dot / denom
  }

  fun analyzeTweet(text: String): ArabicTextAnalysis {
    val normalized = normalize(text)
    val tokens = tokenize(text)
    val normalizedTokens = tokenize(normalized)
    val sentences = segmentSentences(text)
    val profile = detectLanguageProfile(text)
    val variations = detectCharacterVariations(text)
    val obfuscations = detectObfuscation(text)
    val repeatedChars = detectRepeatedCharacters(text)
    val splits = splitConcatenatedWords(tokens)

    return ArabicTextAnalysis(
      originalText = text,
      normalizedText = normalized,
      tokens = tokens,
      normalizedTokens = normalizedTokens,
      sentences = sentences,
      languageProfile = profile,
      characterVariations = variations,
      obfuscationMatches = obfuscations,
      repeatedCharMatches = repeatedChars,
      concatenatedSplits = splits,
    )
  }

  fun runEngine(tweets: List<String>): EngineResult {
    val start = System.currentTimeMillis()
    return try {
      val findings = mutableListOf<Finding>()
      val analyses = tweets.map { analyzeTweet(it) }

      analyses.forEachIndexed { idx, analysis ->
        val tweetNum = idx + 1
        analysis.obfuscationMatches.forEach { obf ->
          findings.add(
            Finding(
              id = "arabic_obf_${idx}_${obf.startOffset}",
              dedupKey = "OBFUSCATION:$idx:${obf.reconstructed}",
              engineId = EngineId.ARABIC_TEXT,
              category = "تحايل إملائي",
              titleAr = "تمويه كتابي أو فصل أحرف في التغريدة $tweetNum",
              explanationAr =
                "العبارة «${obf.snippet}» تستخدم فصلاً أو رموزاً أو أحرف تحكم غير مرئية قد تبدو كمحاولة التفاف على الفلاتر النصية (الأصل المقدر: ${obf.reconstructed}).",
              suggestionAr = "استبدل «${obf.snippet}» بالكتابة العربية الطبيعية «${obf.reconstructed}».",
              severity = RiskLevel.MEDIUM,
              confidence = 0.88,
              location =
                EvidenceLocation(
                  tweetIndex = idx,
                  startOffset = obf.startOffset,
                  endOffset = obf.endOffset,
                  snippet = obf.snippet,
                ),
            )
          )
        }

        analysis.repeatedCharMatches.forEach { rep ->
          if (rep.maxRepeatRun >= 3) {
            findings.add(
              Finding(
                id = "arabic_rep_${idx}_${rep.startOffset}",
                dedupKey = "ELONGATION:$idx:${normalize(rep.collapsed)}",
                engineId = EngineId.ARABIC_TEXT,
                category = "تمديد حروف غير طبيعي",
                titleAr = "تكرار مفرط للحروف في التغريدة $tweetNum",
                explanationAr =
                  "الكلمة «${rep.snippet}» تحتوي على تكرار غير طبيعي للحرف (${rep.maxRepeatRun} مرات)، مما يقلل من احترافية الصياغة وقد يرفع مؤشرات الضجيج النصي.",
                suggestionAr = "اكتب الكلمة بشكل طبيعي: «${rep.collapsed}».",
                severity = RiskLevel.LOW,
                confidence = 0.90,
                location =
                  EvidenceLocation(
                    tweetIndex = idx,
                    startOffset = rep.startOffset,
                    endOffset = rep.endOffset,
                    snippet = rep.snippet,
                  ),
              )
            )
          }
        }

        if (analysis.characterVariations.any { it.contains("غير مرئية") || it.contains("سيريلية") || it.contains("غير قياسي") }) {
          findings.add(
            Finding(
              id = "arabic_var_$idx",
              dedupKey = "CHAR_VARIATION:$idx",
              engineId = EngineId.ARABIC_TEXT,
              category = "تباين أحرف غير قياسي",
              titleAr = "استخدام أحرف بديلة أو غير مرئية في التغريدة $tweetNum",
              explanationAr =
                "رُصدت تباينات في المحارف (${analysis.characterVariations.joinToString("، ")})؛ قد تُفسَّر أحياناً كتمويه نصي.",
              suggestionAr = "وحّد الأحرف العربية إلى المحارف القياسية وتجنب الأحرف الخفية.",
              severity = RiskLevel.MEDIUM,
              confidence = 0.82,
              location = EvidenceLocation(tweetIndex = idx, snippet = analysis.originalText.take(60)),
            )
          )
        }
      }

      val maxRisk = findings.maxByOrNull { it.severity.weightOrder }?.severity ?: RiskLevel.LOW
      val mixedCount = analyses.count { it.languageProfile.isMixedArabicEnglish }
      val primaryLang = analyses.firstOrNull()?.languageProfile?.primaryLabelAr ?: "غير محدد"
      val summary =
        if (findings.isEmpty()) {
          "النص العربي سليم ومقروء ($primaryLang) دون مؤشرات تحايل أو تمديد مزعج."
        } else {
          "تم رصد ${findings.size} ملاحظات لغوية (تمويه أو تمديد حروف أو محارف بديلة) مع الحفاظ على النص الأصلي دون تعديل."
        }

      EngineResult(
        engineId = EngineId.ARABIC_TEXT,
        nameAr = EngineId.ARABIC_TEXT.displayNameAr,
        status = EngineStatus.SUCCESS,
        riskLevel = maxRisk,
        confidence = if (tweets.any { it.isNotBlank() }) 0.90 else 0.30,
        summaryAr = summary,
        findings = findings,
        metadata =
          mapOf(
            "اللغة الغالبة" to primaryLang,
            "تغريدات مختلطة (عربي/إنجليزي)" to mixedCount.toString(),
          ),
        durationMs = System.currentTimeMillis() - start,
      )
    } catch (e: Exception) {
      EngineResult(
        engineId = EngineId.ARABIC_TEXT,
        nameAr = EngineId.ARABIC_TEXT.displayNameAr,
        status = EngineStatus.FAILED,
        riskLevel = RiskLevel.INSUFFICIENT_DATA,
        confidence = 0.0,
        summaryAr = "تعذر إكمال التحليل اللغوي بسبب خطأ تقني.",
        errorMessage = e.message,
        durationMs = System.currentTimeMillis() - start,
      )
    }
  }
}

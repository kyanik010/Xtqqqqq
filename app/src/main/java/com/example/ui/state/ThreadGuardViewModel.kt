package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.core.ThreadGuardOrchestrator
import com.example.domain.engines.ArabicTextIntelligenceEngine
import com.example.domain.model.AnalysisConfig
import com.example.domain.model.Finding
import com.example.domain.model.RewriteSuggestion
import com.example.domain.model.SuggestionStatus
import com.example.domain.model.ThreadAnalysisReport
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab(val titleAr: String) {
  EDITOR("المحرر"),
  RESULTS("التقرير"),
  REWRITE("التحسين"),
  SETTINGS("الإعدادات"),
}

data class TweetDraft(
  val id: String = UUID.randomUUID().toString(),
  val text: String = "",
  val preRewriteBackup: String? = null,
)

data class ThreadGuardUiState(
  val tweets: List<TweetDraft> = listOf(TweetDraft()),
  val activeTab: MainTab = MainTab.EDITOR,
  val config: AnalysisConfig = AnalysisConfig(),
  val isAnalyzing: Boolean = false,
  val report: ThreadAnalysisReport? = null,
  val suggestions: List<RewriteSuggestion> = emptyList(),
  val highlightedTweetIndex: Int? = null,
  val highlightedSnippet: String? = null,
  val pasteDialogOpen: Boolean = false,
  val statusToastMessage: String? = null,
)

class ThreadGuardViewModel(
  private val orchestrator: ThreadGuardOrchestrator = ThreadGuardOrchestrator(),
  private val arabicEngine: ArabicTextIntelligenceEngine = ArabicTextIntelligenceEngine(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(ThreadGuardUiState())
  val uiState: StateFlow<ThreadGuardUiState> = _uiState.asStateFlow()

  fun selectTab(tab: MainTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun openPasteDialog(open: Boolean) {
    _uiState.update { it.copy(pasteDialogOpen = open) }
  }

  fun clearStatusToast() {
    _uiState.update { it.copy(statusToastMessage = null) }
  }

  fun showToast(message: String) {
    _uiState.update { it.copy(statusToastMessage = message) }
  }

  fun addTweet() {
    _uiState.update { state ->
      if (state.tweets.size >= state.config.maxThreadSize) {
        state.copy(statusToastMessage = "الحد الأقصى للثريد حالياً ${state.config.maxThreadSize} تغريدة.")
      } else {
        state.copy(tweets = state.tweets + TweetDraft())
      }
    }
  }

  fun deleteTweet(index: Int) {
    _uiState.update { state ->
      if (state.tweets.size <= 1) {
        state.copy(tweets = listOf(TweetDraft()), highlightedTweetIndex = null, highlightedSnippet = null)
      } else if (index in state.tweets.indices) {
        val updated = state.tweets.toMutableList().apply { removeAt(index) }
        state.copy(tweets = updated, highlightedTweetIndex = null, highlightedSnippet = null)
      } else state
    }
  }

  fun updateTweetText(index: Int, newText: String) {
    _uiState.update { state ->
      if (index !in state.tweets.indices) return@update state
      val updated = state.tweets.toMutableList()
      updated[index] = updated[index].copy(text = newText)
      state.copy(
        tweets = updated,
        highlightedTweetIndex = if (state.highlightedTweetIndex == index) null else state.highlightedTweetIndex,
        highlightedSnippet = if (state.highlightedTweetIndex == index) null else state.highlightedSnippet,
      )
    }
  }

  fun clearTweetText(index: Int) {
    updateTweetText(index, "")
  }

  fun moveTweetUp(index: Int) {
    if (index <= 0) return
    _uiState.update { state ->
      val list = state.tweets.toMutableList()
      val tmp = list[index - 1]
      list[index - 1] = list[index]
      list[index] = tmp
      state.copy(tweets = list, highlightedTweetIndex = null)
    }
  }

  fun moveTweetDown(index: Int) {
    _uiState.update { state ->
      if (index >= state.tweets.lastIndex) return@update state
      val list = state.tweets.toMutableList()
      val tmp = list[index + 1]
      list[index + 1] = list[index]
      list[index] = tmp
      state.copy(tweets = list, highlightedTweetIndex = null)
    }
  }

  fun pasteFullThread(rawThreadText: String) {
    val trimmed = rawThreadText.trim()
    if (trimmed.isEmpty()) {
      _uiState.update { it.copy(pasteDialogOpen = false) }
      return
    }

    // Split by explicit --- separators, double blank lines, or numbered prefixes like "1/" or "١/"
    val parts =
      when {
        trimmed.contains("\n---\n") -> trimmed.split(Regex("""\n\s*---+\s*\n"""))
        Regex("""(^|\n)\s*[0-9\u0660-\u0669]+[/).\-]\s+""").findAll(trimmed).count() >= 2 -> {
          trimmed
            .split(Regex("""(?=(?:^|\n)\s*[0-9\u0660-\u0669]+[/).\-]\s+)"""))
            .map { it.replace(Regex("""^\s*[0-9\u0660-\u0669]+[/).\-]\s+"""), "").trim() }
        }
        trimmed.contains("\n\n") -> trimmed.split(Regex("""\n\s*\n+"""))
        else -> listOf(trimmed)
      }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(_uiState.value.config.maxThreadSize)

    val drafts = if (parts.isEmpty()) listOf(TweetDraft(text = trimmed)) else parts.map { TweetDraft(text = it) }
    _uiState.update {
      it.copy(
        tweets = drafts,
        pasteDialogOpen = false,
        highlightedTweetIndex = null,
        highlightedSnippet = null,
        statusToastMessage = "تم تقسيم النص إلى ${drafts.size} تغريدة(تغريدات) جاهزة للفحص.",
      )
    }
  }

  fun loadDemoPreset(techPreset: Boolean) {
    val drafts =
      if (techPreset) {
        listOf(
          TweetDraft(text = "كيف تعمل تقنية IPTV تقنياً؟ إليكم شرحاً مبسطاً لبروتوكولات البث والمشغلات دون تعقيد 1/3"),
          TweetDraft(text = "تعتمد معظم مشغلات IPTV الحديثة على قوائم M3U8 وبروتوكول HLS لتقسيم الفيديو إلى مقاطع صغيرة عبر CDN لتقليل التقطيع أثناء البث."),
          TweetDraft(text = "المقارنة التقنية: HLS يتفوق في التوافق والاستقرار عبر الشبكات المتغيرة، بينما RTSP يقدم كموناً أقل في الشبكات المحلية. ما تجربتكم مع إعدادات المشغل؟"),
        )
      } else {
        listOf(
          TweetDraft(text = "حصرياً عرض لا يعوض!! ا ش ت ر ا ك سيرفر IPTV مجاااااني ومضمون 100% سارع قبل النفاذ اشترك الآن عبر http://bit.ly/promo-x1"),
          TweetDraft(text = "حصريا عرض لا يعوض!! اشتراك سيرفر قنوات مضمون 100% تواصل معنا الآن عبر http://bit.ly/promo-x1 ولا تفوت الفرصة 🔥🔥🔥🔥"),
          TweetDraft(text = "تواصل معنا الآن اشترك الآن واضغط هنا http://192.168.1.50/login-update للحصول على الباقة الذهبية."),
        )
      }
    _uiState.update {
      it.copy(
        tweets = drafts,
        activeTab = MainTab.EDITOR,
        highlightedTweetIndex = null,
        highlightedSnippet = null,
        statusToastMessage = if (techPreset) "تم تحميل نموذج شرح تقني للمعاينة." else "تم تحميل نموذج ترويجي لاختبار اكتشاف السبام والروابط.",
      )
    }
  }

  fun computeInlineWarnings(tweetText: String): List<String> {
    if (tweetText.isBlank()) return emptyList()
    val warnings = mutableListOf<String>()
    val limit = _uiState.value.config.maxTweetChars
    if (tweetText.length > limit) {
      warnings.add("تجاوز حد الأحرف (${tweetText.length}/$limit)")
    }
    val obf = arabicEngine.detectObfuscation(tweetText)
    if (obf.isNotEmpty()) {
      warnings.add("تمويه أو فصل أحرف: «${obf.first().snippet}»")
    }
    val rep = arabicEngine.detectRepeatedCharacters(tweetText)
    if (rep.isNotEmpty()) {
      warnings.add("تمديد حروف مفرط: «${rep.first().snippet}»")
    }
    return warnings
  }

  fun analyzeThread() {
    val rawTweets = _uiState.value.tweets.map { it.text }
    if (rawTweets.all { it.isBlank() }) {
      _uiState.update { it.copy(statusToastMessage = "اكتب تغريدة واحدة على الأقل قبل بدء الفحص.") }
      return
    }
    val config = _uiState.value.config
    viewModelScope.launch {
      _uiState.update { it.copy(isAnalyzing = true) }
      val report = orchestrator.analyze(rawTweets, config)
      _uiState.update {
        it.copy(
          isAnalyzing = false,
          report = report,
          suggestions = report.rewriteSuggestions,
          activeTab = MainTab.RESULTS,
        )
      }
    }
  }

  fun jumpToFinding(finding: Finding) {
    val targetIdx = if (finding.location.tweetIndex in _uiState.value.tweets.indices) finding.location.tweetIndex else 0
    _uiState.update {
      it.copy(
        activeTab = MainTab.EDITOR,
        highlightedTweetIndex = targetIdx,
        highlightedSnippet = finding.location.snippet.takeIf { s -> s.isNotBlank() },
      )
    }
  }

  fun acceptSuggestion(suggestionId: String) {
    _uiState.update { state ->
      val target = state.suggestions.firstOrNull { it.id == suggestionId } ?: return@update state
      if (target.tweetIndex !in state.tweets.indices) return@update state

      val updatedTweets = state.tweets.toMutableList()
      val currentDraft = updatedTweets[target.tweetIndex]
      updatedTweets[target.tweetIndex] =
        currentDraft.copy(
          text = target.suggestedText,
          preRewriteBackup = currentDraft.preRewriteBackup ?: currentDraft.text,
        )

      val updatedSuggestions =
        state.suggestions.map { s ->
          if (s.id == suggestionId) s.copy(status = SuggestionStatus.ACCEPTED) else s
        }

      state.copy(
        tweets = updatedTweets,
        suggestions = updatedSuggestions,
        statusToastMessage = "تم تطبيق الاقتراح على التغريدة ${target.tweetIndex + 1}؛ يمكنك استعادة الأصل في أي وقت.",
      )
    }
  }

  fun rejectSuggestion(suggestionId: String) {
    _uiState.update { state ->
      val updatedSuggestions =
        state.suggestions.map { s ->
          if (s.id == suggestionId) s.copy(status = SuggestionStatus.REJECTED) else s
        }
      state.copy(suggestions = updatedSuggestions)
    }
  }

  fun restoreOriginal(tweetIndex: Int) {
    _uiState.update { state ->
      if (tweetIndex !in state.tweets.indices) return@update state
      val draft = state.tweets[tweetIndex]
      val backup = draft.preRewriteBackup ?: return@update state
      val updatedTweets = state.tweets.toMutableList()
      updatedTweets[tweetIndex] = draft.copy(text = backup, preRewriteBackup = null)

      val updatedSuggestions =
        state.suggestions.map { s ->
          if (s.tweetIndex == tweetIndex) s.copy(status = SuggestionStatus.PENDING) else s
        }

      state.copy(
        tweets = updatedTweets,
        suggestions = updatedSuggestions,
        statusToastMessage = "تمت استعادة النص الأصلي للتغريدة ${tweetIndex + 1}.",
      )
    }
  }

  fun updateMaxChars(newLimit: Int) {
    val clamped = newLimit.coerceIn(100, 25000)
    _uiState.update { it.copy(config = it.config.copy(maxTweetChars = clamped)) }
  }

  fun toggleLiveUrlInspection(enabled: Boolean) {
    _uiState.update { it.copy(config = it.config.copy(enableLiveUrlInspection = enabled)) }
  }

  fun toggleStrictMarketing(enabled: Boolean) {
    _uiState.update { it.copy(config = it.config.copy(strictMarketingMode = enabled)) }
  }
}

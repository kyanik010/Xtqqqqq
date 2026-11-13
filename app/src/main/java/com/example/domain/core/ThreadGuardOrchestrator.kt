package com.example.domain.core

import com.example.domain.engines.ArabicTextIntelligenceEngine
import com.example.domain.engines.PolicyRiskEngine
import com.example.domain.engines.SpamDetectionEngine
import com.example.domain.engines.ThreadQualityEngine
import com.example.domain.engines.UrlIntelligenceEngine
import com.example.domain.engines.VisibilityRiskEngine
import com.example.domain.model.AnalysisConfig
import com.example.domain.model.ContentContext
import com.example.domain.model.ThreadAnalysisReport
import com.example.domain.services.RewriteSuggestionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Coordinates all independent ThreadGuard X analysis engines cleanly:
 * - Parallel execution of independent phases on Dispatchers.Default/IO.
 * - Isolated failure handling per engine (one failing engine never crashes the pipeline).
 * - Strict deduplication and transparent scoring via RiskEvidenceAggregator.
 */
class ThreadGuardOrchestrator(
  private val arabicEngine: ArabicTextIntelligenceEngine = ArabicTextIntelligenceEngine(),
  private val spamEngine: SpamDetectionEngine = SpamDetectionEngine(arabicEngine),
  private val urlEngine: UrlIntelligenceEngine = UrlIntelligenceEngine(),
  private val policyEngine: PolicyRiskEngine = PolicyRiskEngine(arabicEngine),
  private val qualityEngine: ThreadQualityEngine = ThreadQualityEngine(arabicEngine),
  private val visibilityEngine: VisibilityRiskEngine = VisibilityRiskEngine(),
  private val aggregator: RiskEvidenceAggregator = RiskEvidenceAggregator(),
  private val rewriteEngine: RewriteSuggestionEngine = RewriteSuggestionEngine(arabicEngine),
) {

  suspend fun analyze(tweets: List<String>, config: AnalysisConfig = AnalysisConfig()): ThreadAnalysisReport =
    withContext(Dispatchers.Default) {
      val boundedTweets = tweets.take(config.maxThreadSize)

      // Phase 1: Parallel independent engines (Arabic, Spam, Quality, URL on IO)
      val arabicDeferred = async { arabicEngine.runEngine(boundedTweets) }
      val spamDeferred = async { spamEngine.runEngine(boundedTweets, config) }
      val qualityDeferred = async { qualityEngine.runEngine(boundedTweets, config) }
      val urlDeferred = async(Dispatchers.IO) { urlEngine.runEngine(boundedTweets, config) }

      val arabicRes = arabicDeferred.await()
      val spamRes = spamDeferred.await()
      val qualityRes = qualityDeferred.await()
      val urlOut = urlDeferred.await()

      // Phase 2: Contextual Policy engine incorporating Spam & URL evidence
      val policyRes = policyEngine.runEngine(boundedTweets, spamRes, urlOut.engineResult)

      // Phase 3: Visibility Risk Assessment synthesizing upstream friction signals
      val visibilityRes =
        visibilityEngine.runEngine(
          tweets = boundedTweets,
          arabicResult = arabicRes,
          spamResult = spamRes,
          policyResult = policyRes,
          urlResult = urlOut.engineResult,
          qualityResult = qualityRes,
        )

      val allResults = listOf(arabicRes, spamRes, policyRes, urlOut.engineResult, qualityRes, visibilityRes)
      val dedupedFindings = aggregator.deduplicateFindings(allResults)
      val suggestions = rewriteEngine.generateSuggestions(boundedTweets, dedupedFindings)

      val contextLabel = spamRes.metadata["السياق المكتشف"]
      val detectedContext = ContentContext.entries.firstOrNull { it.labelAr == contextLabel } ?: ContentContext.GENERAL

      aggregator.aggregate(
        tweets = boundedTweets,
        engineResults = allResults,
        urlSummaries = urlOut.summaries,
        rewriteSuggestions = suggestions,
        detectedContext = detectedContext,
      )
    }
}

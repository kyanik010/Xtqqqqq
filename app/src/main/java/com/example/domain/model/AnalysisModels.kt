package com.example.domain.model

enum class EngineId(val displayNameAr: String) {
  ARABIC_TEXT("محرك اللغة العربية"),
  SPAM_DETECTION("محرك اكتشاف السبام"),
  POLICY_RISK("محرك تحليل سياسات المحتوى"),
  URL_INTELLIGENCE("محرك تحليل الروابط"),
  THREAD_QUALITY("محرك جودة الثريد"),
  VISIBILITY_RISK("محرك تقييم مخاطر الظهور"),
}

enum class EngineStatus(val labelAr: String) {
  SUCCESS("مكتمل"),
  DEGRADED("مكتمل جزئياً"),
  FAILED("تعذر الفحص"),
  SKIPPED("متجاوز"),
}

enum class RiskLevel(val labelAr: String, val weightOrder: Int) {
  LOW("منخفض", 1),
  MEDIUM("متوسط", 2),
  HIGH("مرتفع", 3),
  CRITICAL("حرج", 4),
  INSUFFICIENT_DATA("غير كافٍ للحكم", 0),
}

enum class PolicyVerdict(val labelAr: String) {
  COMPLIANT("غير مخالف"),
  NEEDS_REVIEW("يحتاج مراجعة"),
  RISK_INDICATORS("يحمل مؤشرات خطورة"),
  LIKELY_VIOLATION("مخالفة محتملة وفق القواعد المتاحة"),
}

enum class ContentContext(val labelAr: String) {
  TECHNICAL_EXPLANATION("شرح تقني طبيعي"),
  EDUCATIONAL("محتوى تعليمي"),
  NORMAL_COMMERCIAL("إعلان تجاري طبيعي"),
  OPINION_CRITICISM("نقد أو اقتباس أو رأي"),
  WARNING_REPORTING("تحذير أو توعية"),
  PROMOTIONAL_SPAM("محتوى ترويجي مزعج"),
  REPETITIVE_MARKETING("تسويق متكرر"),
  EVASION_ATTEMPT("محاولة تحايل على الفلاتر"),
  GENERAL("محتوى عام"),
}

enum class UrlCheckStatus(val labelAr: String) {
  INSPECTED_OK("تم الفحص - لم تظهر مؤشرات خطورة واضحة"),
  SUSPICIOUS("مؤشرات اشتباه تستحق المراجعة"),
  UNSAFE_PATTERN("نمط رابط عالي الخطورة"),
  BLOCKED_SSRF("محظور أمنياً (عنوان داخلي/خاص)"),
  UNINSPECTED("غير مفحوص"),
}

enum class SuggestionStatus(val labelAr: String) {
  PENDING("قيد المراجعة"),
  ACCEPTED("مقبول"),
  REJECTED("مرفوض"),
}

data class EvidenceLocation(
  val tweetIndex: Int, // 0-based; -1 indicates thread-wide finding
  val startOffset: Int = 0,
  val endOffset: Int = 0,
  val snippet: String = "",
)

data class Finding(
  val id: String,
  val dedupKey: String,
  val engineId: EngineId,
  val category: String,
  val titleAr: String,
  val explanationAr: String,
  val suggestionAr: String,
  val severity: RiskLevel,
  val confidence: Double,
  val location: EvidenceLocation,
)

data class EngineResult(
  val engineId: EngineId,
  val nameAr: String,
  val status: EngineStatus,
  val riskLevel: RiskLevel,
  val confidence: Double,
  val summaryAr: String,
  val findings: List<Finding> = emptyList(),
  val metadata: Map<String, String> = emptyMap(),
  val errorMessage: String? = null,
  val durationMs: Long = 0L,
)

data class UrlInspectionSummary(
  val rawUrl: String,
  val normalizedUrl: String,
  val finalUrl: String?,
  val finalDomain: String,
  val tweetIndex: Int,
  val isShortener: Boolean,
  val redirectCount: Int,
  val status: UrlCheckStatus,
  val riskNotesAr: List<String>,
)

data class RewriteSuggestion(
  val id: String,
  val tweetIndex: Int,
  val originalText: String,
  val suggestedText: String,
  val reasonAr: String,
  val findingId: String,
  val status: SuggestionStatus = SuggestionStatus.PENDING,
)

data class AnalysisConfig(
  val maxTweetChars: Int = 280,
  val enableLiveUrlInspection: Boolean = false,
  val strictMarketingMode: Boolean = false,
  val maxThreadSize: Int = 50,
)

data class ThreadAnalysisReport(
  val operationId: String,
  val analyzedAtIso: String,
  val tweetCount: Int,
  val overallAssessmentAr: String,
  val overallRiskLevel: RiskLevel,
  val policyVerdict: PolicyVerdict,
  val detectedContext: ContentContext,
  val riskScore: Int,
  val confidenceScore: Double,
  val scoringDisclaimerAr: String,
  val engineResults: List<EngineResult>,
  val deduplicatedFindings: List<Finding>,
  val recommendationsAr: List<String>,
  val rewriteSuggestions: List<RewriteSuggestion>,
  val urlInspectionItems: List<UrlInspectionSummary>,
)

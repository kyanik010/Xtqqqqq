# معمارية ThreadGuard X

## الطبقات البرمجية
- **`domain/model/`**: نماذج النتائج والأدلة (`Finding`, `EngineResult`, `ThreadAnalysisReport`, `AnalysisConfig`).
- **`domain/security/`**: حارس أمان الروابط `SsrfUrlGuard` للتحقق من DNS ورفض الشبكات الداخلية.
- **`domain/engines/`**: ستة محركات مستقلة تعمل دون حالة مشتركة ويمكن اختبار كل منها منفرداً:
  1. `ArabicTextIntelligenceEngine`
  2. `SpamDetectionEngine`
  3. `PolicyRiskEngine`
  4. `UrlIntelligenceEngine`
  5. `ThreadQualityEngine`
  6. `VisibilityRiskEngine`
- **`domain/core/`**:
  - `ThreadGuardOrchestrator`: ينفذ المحركات المستقلة بالتوازي عبر Coroutines ويعزل أي خطأ تقني في محرك واحد دون إسقاط النظام.
  - `RiskEvidenceAggregator`: يزيل تكرار الأدلة عبر `dedupKey` ويحسب التقييم النهائي.
- **`domain/services/`**:
  - `RewriteSuggestionEngine`: يقترح تحسينات غير إلزامية دون تغيير المعنى.
  - `ReportFormatter`: يصدر تقريراً عربياً منظماً.
- **`ui/`**: واجهة Jetpack Compose Material 3 عربية RTL بالكامل بنمط MVVM (`ThreadGuardViewModel`).

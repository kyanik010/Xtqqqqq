package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.EngineId
import com.example.domain.model.EngineResult
import com.example.domain.model.EngineStatus
import com.example.domain.model.Finding
import com.example.domain.model.RiskLevel
import com.example.domain.model.ThreadAnalysisReport
import com.example.domain.model.UrlCheckStatus
import com.example.domain.model.UrlInspectionSummary
import com.example.domain.services.ReportFormatter
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.OrangeHigh
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ResultsScreen(
  report: ThreadAnalysisReport?,
  onGoToEditor: () -> Unit,
  onJumpToFinding: (Finding) -> Unit,
  onGoToRewrite: () -> Unit,
  onShowFeedback: (String) -> Unit,
) {
  if (report == null) {
    EmptyReportState(onGoToEditor = onGoToEditor)
    return
  }

  val clipboard = LocalClipboardManager.current
  var selectedEngineFilter by remember { mutableStateOf<EngineId?>(null) }

  val visibleFindings =
    remember(report, selectedEngineFilter) {
      if (selectedEngineFilter == null) report.deduplicatedFindings
      else report.deduplicatedFindings.filter { it.engineId == selectedEngineFilter }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("results_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 1. Overall Summary & Dual Gauges
    item(key = "summary_header") {
      OverallSummaryCard(
        report = report,
        onCopyFullReport = {
          clipboard.setText(AnnotatedString(ReportFormatter.formatArabicReport(report)))
          onShowFeedback("تم نسخ التقرير العربي الكامل.")
        },
        onOpenRewrite = onGoToRewrite,
      )
    }

    // 2. Transparency Disclaimer
    item(key = "disclaimer_card") {
      Surface(
        color = CyanContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
          Text(
            text = "${report.scoringDisclaimerAr} ولا يدّعي التطبيق الاطلاع على خوارزمية X الداخلية أو التنبؤ المؤكد بالوصول.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
          )
        }
      }
    }

    // 3. Engine Status Cards
    item(key = "engines_section_title") {
      Text(
        text = "نتائج المحركات المستقلة (${report.engineResults.size})",
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
      )
    }

    items(report.engineResults, key = { it.engineId.name }) { engineResult ->
      EngineSummaryCard(engineResult)
    }

    // 4. URL Inspection Breakdown
    if (report.urlInspectionItems.isNotEmpty()) {
      item(key = "urls_section_title") {
        Text(
          text = "تحليل الروابط المستخرجة (${report.urlInspectionItems.size})",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
        )
      }
      items(report.urlInspectionItems, key = { "${it.tweetIndex}_${it.rawUrl}" }) { item ->
        UrlSummaryCard(item)
      }
    }

    // 5. Filterable Clickable Findings
    item(key = "findings_header") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "المؤشرات والأدلة غير المكررة (${report.deduplicatedFindings.size}) — اضغط للانتقال للنص",
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = selectedEngineFilter == null,
            onClick = { selectedEngineFilter = null },
            label = { Text("الكل (${report.deduplicatedFindings.size})") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanContainer, selectedLabelColor = CyanAccent),
          )
          EngineId.entries.forEach { eid ->
            val count = report.deduplicatedFindings.count { it.engineId == eid }
            if (count > 0) {
              FilterChip(
                selected = selectedEngineFilter == eid,
                onClick = { selectedEngineFilter = if (selectedEngineFilter == eid) null else eid },
                label = { Text("${eid.displayNameAr} ($count)") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanContainer, selectedLabelColor = CyanAccent),
              )
            }
          }
        }
      }
    }

    if (visibleFindings.isEmpty()) {
      item(key = "no_findings_card") {
        Surface(
          color = EmeraldContainer,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSafe)
            Text(
              text = "لم تُرصد مؤشرات خطورة ضمن هذا الفلتر.",
              style = MaterialTheme.typography.bodyLarge,
              color = TextPrimary,
            )
          }
        }
      }
    } else {
      items(visibleFindings, key = { it.id }) { finding ->
        ClickableFindingCard(finding = finding, onClick = { onJumpToFinding(finding) })
      }
    }

    // 6. Recommendations
    item(key = "recommendations_card") {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("التوصيات العملية", style = MaterialTheme.typography.titleMedium, color = CyanAccent)
          report.recommendationsAr.forEach { rec ->
            Text("• $rec", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyReportState(onGoToEditor: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(Icons.Default.Analytics, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(14.dp))
    Text("لم يتم إجراء فحص بعد", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    Spacer(Modifier.height(6.dp))
    Text(
      text = "اكتب تغريدة أو ألصق ثريداً في المحرر ثم اضغط «بدء الفحص الشامل» لعرض التقرير التفصيلي.",
      style = MaterialTheme.typography.bodyMedium,
      color = TextSecondary,
    )
    Spacer(Modifier.height(16.dp))
    Button(
      onClick = onGoToEditor,
      colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = MidnightBg),
    ) {
      Text("الانتقال إلى المحرر")
    }
  }
}

@Composable
private fun OverallSummaryCard(
  report: ThreadAnalysisReport,
  onCopyFullReport: () -> Unit,
  onOpenRewrite: () -> Unit,
) {
  val riskColor = riskColor(report.overallRiskLevel)

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = CardSlate),
    border = BorderStroke(1.5.dp, riskColor.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth().testTag("overall_summary_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(color = riskColor.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
          Text(
            text = "مستوى الخطورة: ${report.overallRiskLevel.labelAr}",
            style = MaterialTheme.typography.labelLarge,
            color = riskColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
        Text(
          text = "عملية #${report.operationId} • ${report.tweetCount} تغريدات",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
      }

      Text(report.overallAssessmentAr, style = MaterialTheme.typography.titleLarge, color = TextPrimary)

      // Metadata pills
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = SurfaceNavy, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CardBorder)) {
          Text(
            text = "السياق: ${report.detectedContext.labelAr}",
            style = MaterialTheme.typography.labelSmall,
            color = CyanAccent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Surface(color = SurfaceNavy, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CardBorder)) {
          Text(
            text = "السياسات: ${report.policyVerdict.labelAr}",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      // Separate Risk Score vs Confidence Score
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("مؤشر الخطورة الإرشادي", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Text("${report.riskScore} / 100", style = MaterialTheme.typography.labelLarge, color = riskColor)
        }
        LinearProgressIndicator(
          progress = { (report.riskScore / 100f).coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
          color = riskColor,
          trackColor = SurfaceNavy,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("درجة الثقة التحليلية (منفصلة عن الخطورة)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Text("${(report.confidenceScore * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = CyanAccent)
        }
        LinearProgressIndicator(
          progress = { report.confidenceScore.toFloat().coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
          color = CyanAccent,
          trackColor = SurfaceNavy,
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = onCopyFullReport,
          modifier = Modifier.weight(1f).testTag("copy_report_button"),
          border = BorderStroke(1.dp, CardBorder),
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
          Spacer(Modifier.width(6.dp))
          Text("نسخ التقرير", color = TextPrimary)
        }

        if (report.rewriteSuggestions.isNotEmpty()) {
          Button(
            onClick = onOpenRewrite,
            modifier = Modifier.weight(1f).testTag("open_rewrite_tab_button"),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe, contentColor = MidnightBg),
          ) {
            Text("مراجعة التحسين (${report.rewriteSuggestions.size})", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun EngineSummaryCard(engine: EngineResult) {
  val color = if (engine.status == EngineStatus.FAILED) CrimsonCritical else riskColor(engine.riskLevel)

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
    border = BorderStroke(1.dp, CardBorder),
    modifier = Modifier.fillMaxWidth().testTag("engine_card_${engine.engineId.name}"),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Shield, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
          Text(engine.nameAr, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
          Text(
            text = if (engine.status == EngineStatus.FAILED) engine.status.labelAr else engine.riskLevel.labelAr,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      Text(engine.summaryAr, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = "الأدلة: ${engine.findings.size} • الثقة: ${(engine.confidence * 100).toInt()}%",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
        Text(
          text = "${engine.durationMs} ms",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
      }
    }
  }
}

@Composable
private fun UrlSummaryCard(item: UrlInspectionSummary) {
  val color =
    when (item.status) {
      UrlCheckStatus.INSPECTED_OK -> EmeraldSafe
      UrlCheckStatus.UNINSPECTED -> AmberWarn
      UrlCheckStatus.SUSPICIOUS -> OrangeHigh
      UrlCheckStatus.UNSAFE_PATTERN, UrlCheckStatus.BLOCKED_SSRF -> CrimsonCritical
    }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
    border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Default.Link, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
          Text("التغريدة ${item.tweetIndex + 1} • ${item.finalDomain}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
        Text(item.status.labelAr, style = MaterialTheme.typography.labelSmall, color = color)
      }
      Text(item.rawUrl, style = MaterialTheme.typography.labelSmall, color = CyanAccent)
      item.riskNotesAr.forEach { note ->
        Text("• $note", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      }
    }
  }
}

@Composable
private fun ClickableFindingCard(finding: Finding, onClick: () -> Unit) {
  val sevColor = riskColor(finding.severity)
  val locLabel = if (finding.location.tweetIndex >= 0) "التغريدة ${finding.location.tweetIndex + 1}" else "الثريد كاملاً"

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = CardSlate),
    border = BorderStroke(1.dp, sevColor.copy(alpha = 0.45f)),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag("finding_card_${finding.id}"),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(
            imageVector = if (finding.severity.weightOrder >= 3) Icons.Default.ErrorOutline else Icons.Default.WarningAmber,
            contentDescription = null,
            tint = sevColor,
            modifier = Modifier.size(18.dp),
          )
          Text(locLabel, style = MaterialTheme.typography.labelLarge, color = CyanAccent)
          Text("• ${finding.category}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "الانتقال إلى موضع التحذير", tint = TextSecondary, modifier = Modifier.size(16.dp))
      }

      Text(finding.titleAr, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
      Text(finding.explanationAr, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      if (finding.location.snippet.isNotBlank()) {
        Surface(color = SurfaceNavy, shape = RoundedCornerShape(8.dp)) {
          Text(
            text = "الشاهد: «${finding.location.snippet}»",
            style = MaterialTheme.typography.labelSmall,
            color = sevColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }
      Text(
        text = "الاقتراح: ${finding.suggestionAr} (الثقة ${(finding.confidence * 100).toInt()}%)",
        style = MaterialTheme.typography.labelSmall,
        color = EmeraldSafe,
      )
    }
  }
}

private fun riskColor(level: RiskLevel): Color =
  when (level) {
    RiskLevel.LOW -> EmeraldSafe
    RiskLevel.MEDIUM -> AmberWarn
    RiskLevel.HIGH -> OrangeHigh
    RiskLevel.CRITICAL -> CrimsonCritical
    RiskLevel.INSUFFICIENT_DATA -> TextSecondary
  }

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnalysisConfig
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
  config: AnalysisConfig,
  onMaxCharsChange: (Int) -> Unit,
  onToggleLiveUrl: (Boolean) -> Unit,
  onToggleStrictMarketing: (Boolean) -> Unit,
) {
  val charPresets = listOf(280 to "قياسي (280)", 1000 to "متوسط (1000)", 4000 to "مطول (4000)", 25000 to "موثق (25000)")

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("settings_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "char_limit_card") {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSlate),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("حد الأحرف للتغريدة الواحدة", style = MaterialTheme.typography.titleMedium, color = CyanAccent)
          Text(
            text = "لا يفرض ThreadGuard X حداً ثابتاً؛ اختر الحد الموافق لإعدادات حسابك على منصة X:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            charPresets.take(2).forEach { (limit, label) ->
              FilterChip(
                selected = config.maxTweetChars == limit,
                onClick = { onMaxCharsChange(limit) },
                label = { Text(label) },
                modifier = Modifier.weight(1f).testTag("char_preset_$limit"),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanContainer, selectedLabelColor = CyanAccent),
              )
            }
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            charPresets.drop(2).forEach { (limit, label) ->
              FilterChip(
                selected = config.maxTweetChars == limit,
                onClick = { onMaxCharsChange(limit) },
                label = { Text(label) },
                modifier = Modifier.weight(1f).testTag("char_preset_$limit"),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanContainer, selectedLabelColor = CyanAccent),
              )
            }
          }
        }
      }
    }

    item(key = "url_security_card") {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSlate),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("فحص التحويلات المباشر للروابط", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(
                text = "يتتبع التحويلات بأمان (حتى 5 خطوات) مع حظر localhost والشبكات الخاصة وعناوين SSRF الداخلية.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
            Switch(
              checked = config.enableLiveUrlInspection,
              onCheckedChange = onToggleLiveUrl,
              modifier = Modifier.testTag("live_url_switch"),
              colors = SwitchDefaults.colors(checkedThumbColor = MidnightBg, checkedTrackColor = EmeraldSafe),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("تشديد حساسية الدعوات التسويقية", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
              Text(
                text = "يرفع تنبيهات الدعوات التجارية عند تحليل حملات إعلانية.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
            Switch(
              checked = config.strictMarketingMode,
              onCheckedChange = onToggleStrictMarketing,
              modifier = Modifier.testTag("strict_marketing_switch"),
              colors = SwitchDefaults.colors(checkedThumbColor = MidnightBg, checkedTrackColor = CyanAccent),
            )
          }
        }
      }
    }

    item(key = "privacy_transparency_card") {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("الخصوصية والمنهجية", style = MaterialTheme.typography.titleMedium, color = EmeraldSafe)
          Text("• لا يتطلب التطبيق إنشاء حساب ولا يخزن نصوص التغريدات.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
          Text("• جميع محركات التحليل اللغوي والسبام والسياسات وجودة الثريد تعمل محلياً.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
          Text("• لا تُرسل نصوصك إلى أي خدمة ذكاء اصطناعي خارجية أو طرف ثالث.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
          Text(
            "• لا يدّعي التطبيق امتلاك خوارزمية X الداخلية ولا ينسب تقديراته كقرارات رسمية من المنصة.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }
      }
    }
  }
}

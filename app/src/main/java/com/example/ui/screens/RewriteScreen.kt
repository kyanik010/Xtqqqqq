package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.RewriteSuggestion
import com.example.domain.model.SuggestionStatus
import com.example.ui.state.TweetDraft
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RewriteScreen(
  suggestions: List<RewriteSuggestion>,
  tweets: List<TweetDraft>,
  onAccept: (String) -> Unit,
  onReject: (String) -> Unit,
  onRestoreOriginal: (Int) -> Unit,
  onGoToEditor: () -> Unit,
) {
  if (suggestions.isEmpty()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(64.dp))
      Spacer(Modifier.height(14.dp))
      Text("لا توجد اقتراحات تعديل مطلوبة حالياً", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
      Spacer(Modifier.height(6.dp))
      Text(
        text = "بعد فحص الثريد، ستظهر هنا أي اقتراحات لتحسين الصياغة دون تغيير المعنى الأصلي ودون تطبيق تلقائي.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onGoToEditor,
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = MidnightBg),
      ) {
        Text("العودة إلى المحرر")
      }
    }
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("rewrite_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "rewrite_notice") {
      Surface(
        color = CyanContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "مبدأ التحسين الآمن: لا تُعدَّل أي تغريدة تلقائياً ولا يُغيَّر المعنى الأصلي للكاتب. يمكنك قبول كل اقتراح أو رفضه أو استعادة الأصل في أي وقت.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextPrimary,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    items(suggestions, key = { it.id }) { item ->
      val hasBackup = tweets.getOrNull(item.tweetIndex)?.preRewriteBackup != null
      RewriteSuggestionCard(
        suggestion = item,
        canRestoreOriginal = hasBackup,
        onAccept = { onAccept(item.id) },
        onReject = { onReject(item.id) },
        onRestoreOriginal = { onRestoreOriginal(item.tweetIndex) },
      )
    }
  }
}

@Composable
private fun RewriteSuggestionCard(
  suggestion: RewriteSuggestion,
  canRestoreOriginal: Boolean,
  onAccept: () -> Unit,
  onReject: () -> Unit,
  onRestoreOriginal: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = CardSlate),
    border = BorderStroke(1.dp, CardBorder),
    modifier = Modifier.fillMaxWidth().testTag("rewrite_card_${suggestion.tweetIndex}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("اقتراح للتغريدة ${suggestion.tweetIndex + 1}", style = MaterialTheme.typography.titleMedium, color = CyanAccent)
        Surface(
          color =
            when (suggestion.status) {
              SuggestionStatus.ACCEPTED -> EmeraldContainer
              SuggestionStatus.REJECTED -> SurfaceNavy
              SuggestionStatus.PENDING -> CyanContainer
            },
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = suggestion.status.labelAr,
            style = MaterialTheme.typography.labelSmall,
            color =
              when (suggestion.status) {
                SuggestionStatus.ACCEPTED -> EmeraldSafe
                SuggestionStatus.REJECTED -> CrimsonCritical
                SuggestionStatus.PENDING -> CyanAccent
              },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      Text(suggestion.reasonAr, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

      // Original text comparison block
      Surface(
        color = SurfaceNavy,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("النص الأصلي:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Text(suggestion.originalText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
      }

      // Suggested text comparison block
      Surface(
        color = EmeraldContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("الصياغة المقترحة:", style = MaterialTheme.typography.labelSmall, color = EmeraldSafe)
          Text(suggestion.suggestedText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = onAccept,
          enabled = suggestion.status != SuggestionStatus.ACCEPTED,
          modifier = Modifier.weight(1f).testTag("accept_suggestion_${suggestion.tweetIndex}"),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe, contentColor = MidnightBg),
        ) {
          Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("قبول")
        }

        OutlinedButton(
          onClick = onReject,
          enabled = suggestion.status != SuggestionStatus.REJECTED,
          modifier = Modifier.weight(1f).testTag("reject_suggestion_${suggestion.tweetIndex}"),
          border = BorderStroke(1.dp, CardBorder),
        ) {
          Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = CrimsonCritical)
          Spacer(Modifier.width(4.dp))
          Text("رفض", color = TextPrimary)
        }
      }

      if (canRestoreOriginal) {
        OutlinedButton(
          onClick = onRestoreOriginal,
          modifier = Modifier.fillMaxWidth().testTag("restore_original_${suggestion.tweetIndex}"),
          border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.5f)),
        ) {
          Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldSafe)
          Spacer(Modifier.width(6.dp))
          Text("استعادة النص الأصلي", color = EmeraldSafe)
        }
      }
    }
  }
}

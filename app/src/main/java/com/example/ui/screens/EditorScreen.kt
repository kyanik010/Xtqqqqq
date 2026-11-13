package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.state.ThreadGuardUiState
import com.example.ui.state.TweetDraft
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditorScreen(
  uiState: ThreadGuardUiState,
  inlineWarningsProvider: (String) -> List<String>,
  onAddTweet: () -> Unit,
  onDeleteTweet: (Int) -> Unit,
  onUpdateTweet: (Int, String) -> Unit,
  onClearTweet: (Int) -> Unit,
  onMoveUp: (Int) -> Unit,
  onMoveDown: (Int) -> Unit,
  onOpenPasteDialog: (Boolean) -> Unit,
  onPasteThread: (String) -> Unit,
  onAnalyze: () -> Unit,
  onRestoreOriginal: (Int) -> Unit,
  onLoadDemo: (Boolean) -> Unit,
  onShowFeedback: (String) -> Unit,
) {
  val listState = rememberLazyListState()
  val clipboardManager = LocalClipboardManager.current

  LaunchedEffect(uiState.highlightedTweetIndex) {
    val target = uiState.highlightedTweetIndex
    if (target != null && target in uiState.tweets.indices) {
      // +1 accounts for the Hero/Controls header item at index 0
      listState.animateScrollToItem(target + 1)
    }
  }

  if (uiState.pasteDialogOpen) {
    PasteThreadDialog(
      onDismiss = { onOpenPasteDialog(false) },
      onConfirmPaste = onPasteThread,
    )
  }

  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize().testTag("editor_lazy_column"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "hero_and_toolbar") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroBannerCard(
          tweetCount = uiState.tweets.size,
          maxChars = uiState.config.maxTweetChars,
          isAnalyzing = uiState.isAnalyzing,
          onStartScan = onAnalyze,
        )

        // Action row: Paste Thread, Add Tweet, Demo Presets
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilledTonalButton(
            onClick = { onOpenPasteDialog(true) },
            modifier = Modifier.weight(1f).testTag("paste_thread_button"),
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = CardSlate,
                contentColor = TextPrimary,
              ),
          ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.paste_thread), maxLines = 1)
          }

          FilledTonalButton(
            onClick = onAddTweet,
            modifier = Modifier.weight(1f).testTag("add_tweet_button"),
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = CyanContainer,
                contentColor = CyanAccent,
              ),
          ) {
            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add_tweet), maxLines = 1)
          }
        }

        // Sample presets & Thread counter bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = SurfaceNavy,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CardBorder),
          ) {
            Text(
              text = "${stringResource(R.string.tweet_count_label)}: ${uiState.tweets.size} • الحد: ${uiState.config.maxTweetChars} حرف",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
              onClick = { onLoadDemo(true) },
              label = { Text("مثال تقني", style = MaterialTheme.typography.labelSmall) },
              colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceNavy, labelColor = EmeraldSafe),
              border = BorderStroke(1.dp, EmeraldSafe.copy(alpha = 0.4f)),
              modifier = Modifier.testTag("demo_tech_chip"),
            )
            AssistChip(
              onClick = { onLoadDemo(false) },
              label = { Text("مثال سبام", style = MaterialTheme.typography.labelSmall) },
              colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceNavy, labelColor = AmberWarn),
              border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.4f)),
              modifier = Modifier.testTag("demo_spam_chip"),
            )
          }
        }
      }
    }

    itemsIndexed(uiState.tweets, key = { _, draft -> draft.id }) { index, draft ->
      val inlineWarnings = remember(draft.text, uiState.config.maxTweetChars) { inlineWarningsProvider(draft.text) }
      val isHighlighted = uiState.highlightedTweetIndex == index

      TweetEditorCard(
        index = index,
        totalCount = uiState.tweets.size,
        draft = draft,
        maxChars = uiState.config.maxTweetChars,
        inlineWarnings = inlineWarnings,
        isHighlighted = isHighlighted,
        highlightedSnippet = if (isHighlighted) uiState.highlightedSnippet else null,
        onTextChange = { onUpdateTweet(index, it) },
        onMoveUp = { onMoveUp(index) },
        onMoveDown = { onMoveDown(index) },
        onCopy = {
          clipboardManager.setText(AnnotatedString(draft.text))
          onShowFeedback("تم نسخ نص التغريدة ${index + 1}.")
        },
        onClear = { onClearTweet(index) },
        onDelete = { onDeleteTweet(index) },
        onRestoreOriginal = { onRestoreOriginal(index) },
      )
    }

    item(key = "bottom_scan_cta") {
      Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onAnalyze,
          enabled = !uiState.isAnalyzing,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("analyze_thread_button"),
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = MidnightBg),
          shape = RoundedCornerShape(14.dp),
        ) {
          if (uiState.isAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MidnightBg, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(10.dp))
            Text("جارٍ تشغيل المحركات الستة...", style = MaterialTheme.typography.titleMedium)
          } else {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("فحص الثريد بالكامل", style = MaterialTheme.typography.titleMedium)
          }
        }

        Text(
          text = "يعمل الفحص محلياً بالكامل ولا يخزن نصوص التغريدات.",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          modifier = Modifier.align(Alignment.CenterHorizontally),
        )
      }
    }
  }
}

@Composable
private fun HeroBannerCard(
  tweetCount: Int,
  maxChars: Int,
  isAnalyzing: Boolean,
  onStartScan: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.25f)),
    colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(166.dp)) {
      Image(
        painter = painterResource(id = R.drawable.img_hero_banner_1794593535346),
        contentDescription = "شعار حماية الثريدات ThreadGuard X",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.horizontalGradient(
                colors =
                  listOf(
                    MidnightBg.copy(alpha = 0.94f),
                    MidnightBg.copy(alpha = 0.78f),
                    MidnightBg.copy(alpha = 0.35f),
                  )
              )
            )
      )
      Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "ThreadGuard X",
            style = MaterialTheme.typography.headlineMedium,
            color = CyanAccent,
          )
          Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = onStartScan,
            enabled = !isAnalyzing,
            modifier = Modifier.testTag("hero_start_scan_button"),
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = MidnightBg),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          ) {
            Text(stringResource(R.string.start_analysis), fontWeight = FontWeight.Bold)
          }
          Text(
            text = "$tweetCount تغريدات • حد $maxChars",
            style = MaterialTheme.typography.labelSmall,
            color = EmeraldSafe,
          )
        }
      }
    }
  }
}

@Composable
private fun TweetEditorCard(
  index: Int,
  totalCount: Int,
  draft: TweetDraft,
  maxChars: Int,
  inlineWarnings: List<String>,
  isHighlighted: Boolean,
  highlightedSnippet: String?,
  onTextChange: (String) -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onCopy: () -> Unit,
  onClear: () -> Unit,
  onDelete: () -> Unit,
  onRestoreOriginal: () -> Unit,
) {
  val charCount = draft.text.length
  val overLimit = charCount > maxChars
  val nearLimit = charCount > (maxChars * 0.88).toInt() && !overLimit

  val borderColor =
    when {
      isHighlighted -> CyanAccent
      overLimit -> CrimsonCritical
      inlineWarnings.isNotEmpty() -> AmberWarn
      else -> CardBorder
    }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = CardSlate),
    border = BorderStroke(if (isHighlighted) 2.dp else 1.dp, borderColor),
    modifier = Modifier.fillMaxWidth().testTag("tweet_card_$index"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Card Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            color = CyanContainer,
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "التغريدة ${index + 1}",
              style = MaterialTheme.typography.labelLarge,
              color = CyanAccent,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          Text(
            text = "$charCount / $maxChars",
            style = MaterialTheme.typography.labelSmall,
            color =
              when {
                overLimit -> CrimsonCritical
                nearLimit -> AmberWarn
                else -> TextSecondary
              },
            modifier = Modifier.testTag("tweet_char_count_$index"),
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (draft.preRewriteBackup != null) {
            IconButton(
              onClick = onRestoreOriginal,
              modifier = Modifier.size(48.dp).testTag("restore_tweet_button_$index"),
            ) {
              Icon(Icons.Default.Restore, contentDescription = "استعادة النص الأصلي", tint = EmeraldSafe)
            }
          }
          IconButton(
            onClick = onMoveUp,
            enabled = index > 0,
            modifier = Modifier.size(48.dp).testTag("move_up_button_$index"),
          ) {
            Icon(
              Icons.Default.ArrowUpward,
              contentDescription = stringResource(R.string.move_up),
              tint = if (index > 0) TextPrimary else TextSecondary.copy(alpha = 0.35f),
            )
          }
          IconButton(
            onClick = onMoveDown,
            enabled = index < totalCount - 1,
            modifier = Modifier.size(48.dp).testTag("move_down_button_$index"),
          ) {
            Icon(
              Icons.Default.ArrowDownward,
              contentDescription = stringResource(R.string.move_down),
              tint = if (index < totalCount - 1) TextPrimary else TextSecondary.copy(alpha = 0.35f),
            )
          }
          IconButton(
            onClick = onCopy,
            enabled = draft.text.isNotEmpty(),
            modifier = Modifier.size(48.dp).testTag("copy_tweet_button_$index"),
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_text), tint = TextSecondary)
          }
          IconButton(
            onClick = onClear,
            enabled = draft.text.isNotEmpty(),
            modifier = Modifier.size(48.dp).testTag("clear_tweet_button_$index"),
          ) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_text), tint = TextSecondary)
          }
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp).testTag("delete_tweet_button_$index"),
          ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete_tweet), tint = CrimsonCritical)
          }
        }
      }

      AnimatedVisibility(visible = isHighlighted && !highlightedSnippet.isNullOrBlank()) {
        Surface(
          color = CyanContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "موضع الملاحظة المختارة: «$highlightedSnippet»",
            style = MaterialTheme.typography.labelSmall,
            color = CyanAccent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      OutlinedTextField(
        value = draft.text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth().testTag("tweet_input_$index"),
        placeholder = { Text("اكتب أو الصق نص التغريدة ${index + 1} هنا...", color = TextSecondary) },
        minLines = 3,
        maxLines = 8,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = CardBorder,
            focusedContainerColor = SurfaceNavy,
            unfocusedContainerColor = SurfaceNavy,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
          ),
      )

      if (inlineWarnings.isNotEmpty()) {
        Surface(
          color = if (overLimit) CrimsonContainer else AmberContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            inlineWarnings.forEach { warn ->
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                  Icons.Default.WarningAmber,
                  contentDescription = null,
                  tint = if (overLimit) CrimsonCritical else AmberWarn,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = warn,
                  style = MaterialTheme.typography.labelSmall,
                  color = TextPrimary,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PasteThreadDialog(
  onDismiss: () -> Unit,
  onConfirmPaste: (String) -> Unit,
) {
  var pastedText by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = SurfaceNavy,
    title = { Text("لصق ثريد جاهز", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "ألصق الثريد كاملاً؛ سيتم تقسيمه تلقائياً عند الترقيم (1/ أو ١/) أو السطر الفارغ المزدوج أو علامة ---.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
        OutlinedTextField(
          value = pastedText,
          onValueChange = { pastedText = it },
          modifier = Modifier.fillMaxWidth().height(180.dp).testTag("paste_thread_input"),
          placeholder = { Text("1/ التغريدة الأولى...\n\n2/ التغريدة الثانية...", color = TextSecondary) },
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = CardBorder,
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
            ),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmPaste(pastedText) },
        modifier = Modifier.testTag("confirm_paste_button"),
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = MidnightBg),
      ) {
        Text("تقسيم وإدراج")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text("إلغاء", color = TextSecondary)
      }
    },
  )
}

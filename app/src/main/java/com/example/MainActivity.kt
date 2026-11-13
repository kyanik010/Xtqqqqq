package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.RewriteScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.state.MainTab
import com.example.ui.state.ThreadGuardViewModel
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThreadGuardTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ThreadGuardTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          ThreadGuardRoot()
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadGuardRoot(viewModel: ThreadGuardViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(uiState.statusToastMessage) {
    val msg = uiState.statusToastMessage
    if (msg != null) {
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusToast()
    }
  }

  LaunchedEffect(uiState.activeTab) {
    snackbarHostState.currentSnackbarData?.dismiss()
  }

  // Handle system back press when not on Editor tab
  BackHandler(enabled = uiState.activeTab != MainTab.EDITOR) {
    viewModel.selectTab(MainTab.EDITOR)
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MidnightBg,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceNavy, titleContentColor = TextPrimary),
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
          }
        },
        actions = {
          Surface(
            color = CardSlate,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(end = 12.dp),
          ) {
            Text(
              text = "تحليل محلي خاص",
              style = MaterialTheme.typography.labelSmall,
              color = EmeraldSafe,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        },
      )
    },
    bottomBar = {
      NavigationBar(containerColor = SurfaceNavy) {
        MainTab.entries.forEach { tab ->
          val selected = uiState.activeTab == tab
          val icon =
            when (tab) {
              MainTab.EDITOR -> if (selected) Icons.Filled.EditNote else Icons.Outlined.EditNote
              MainTab.RESULTS -> if (selected) Icons.Filled.Analytics else Icons.Outlined.Analytics
              MainTab.REWRITE -> if (selected) Icons.Filled.AutoFixHigh else Icons.Outlined.AutoFixHigh
              MainTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
            }
          val badgeCount =
            when (tab) {
              MainTab.RESULTS -> uiState.report?.deduplicatedFindings?.size ?: 0
              MainTab.REWRITE -> uiState.suggestions.size
              else -> 0
            }

          NavigationBarItem(
            selected = selected,
            onClick = { viewModel.selectTab(tab) },
            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
            icon = {
              if (badgeCount > 0) {
                BadgedBox(badge = { Badge(containerColor = CyanAccent, contentColor = MidnightBg) { Text("$badgeCount") } }) {
                  Icon(icon, contentDescription = tab.titleAr)
                }
              } else {
                Icon(icon, contentDescription = tab.titleAr)
              }
            },
            label = { Text(tab.titleAr, style = MaterialTheme.typography.labelSmall) },
            colors =
              NavigationBarItemDefaults.colors(
                selectedIconColor = CyanAccent,
                selectedTextColor = CyanAccent,
                indicatorColor = CyanContainer,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
              ),
          )
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
        when (uiState.activeTab) {
          MainTab.EDITOR ->
            EditorScreen(
              uiState = uiState,
              inlineWarningsProvider = viewModel::computeInlineWarnings,
              onAddTweet = viewModel::addTweet,
              onDeleteTweet = viewModel::deleteTweet,
              onUpdateTweet = viewModel::updateTweetText,
              onClearTweet = viewModel::clearTweetText,
              onMoveUp = viewModel::moveTweetUp,
              onMoveDown = viewModel::moveTweetDown,
              onOpenPasteDialog = viewModel::openPasteDialog,
              onPasteThread = viewModel::pasteFullThread,
              onAnalyze = viewModel::analyzeThread,
              onRestoreOriginal = viewModel::restoreOriginal,
              onLoadDemo = viewModel::loadDemoPreset,
              onShowFeedback = viewModel::showToast,
            )
          MainTab.RESULTS ->
            ResultsScreen(
              report = uiState.report,
              onGoToEditor = { viewModel.selectTab(MainTab.EDITOR) },
              onJumpToFinding = viewModel::jumpToFinding,
              onGoToRewrite = { viewModel.selectTab(MainTab.REWRITE) },
              onShowFeedback = viewModel::showToast,
            )
          MainTab.REWRITE ->
            RewriteScreen(
              suggestions = uiState.suggestions,
              tweets = uiState.tweets,
              onAccept = viewModel::acceptSuggestion,
              onReject = viewModel::rejectSuggestion,
              onRestoreOriginal = viewModel::restoreOriginal,
              onGoToEditor = { viewModel.selectTab(MainTab.EDITOR) },
            )
          MainTab.SETTINGS ->
            SettingsScreen(
              config = uiState.config,
              onMaxCharsChange = viewModel::updateMaxChars,
              onToggleLiveUrl = viewModel::toggleLiveUrlInspection,
              onToggleStrictMarketing = viewModel::toggleStrictMarketing,
            )
        }
      }
    }
  }
}

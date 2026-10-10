package io.github.passradar.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.passradar.R
import io.github.passradar.ui.glass.GlassNavItem
import io.github.passradar.ui.glass.GlassNavigationBar

private val NavBarHeight = 68.dp
private val NavBarMargin = 12.dp

/** A soft wash of the primary colour at the top, so the glass has something to catch. */
@Composable
fun screenBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        0f to colors.primaryContainer.copy(alpha = 0.55f),
        0.4f to colors.background,
        1f to colors.background,
    )
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val openOffice by vm.openOffice.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val rootBackdrop = rememberLayerBackdrop()
    val bottomSpace: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + NavBarHeight + NavBarMargin * 2

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val failed = when (message) {
                is UiMessage.CheckDone -> message.result.let { it == null || (it.checked == 0 && it.failed > 0) }
                is UiMessage.RefreshDone -> true
                UiMessage.LocationUnavailable -> false
            }
            val text = when {
                failed && !context.isOnline() -> resources.getString(R.string.offline)
                message is UiMessage.CheckDone -> {
                    val result = message.result
                    when {
                        result == null || (result.checked == 0 && result.failed > 0) -> resources.getString(R.string.check_failed)
                        result.withNewSlots > 0 ->
                            resources.getQuantityString(R.plurals.check_found, result.withNewSlots, result.withNewSlots)
                        else -> resources.getString(R.string.check_nothing_new)
                    }
                }
                message is UiMessage.RefreshDone ->
                    if (message.result.stoppedEarly) resources.getString(R.string.refresh_stopped)
                    else resources.getQuantityString(R.plurals.refresh_failed, message.result.failed, message.result.failed)
                else -> resources.getString(R.string.location_unavailable)
            }
            snackbar.showSnackbar(text)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(rootBackdrop).background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                label = "tabs",
            ) { current ->
                when (current) {
                    Tab.Overview -> OverviewScreen(vm, bottomSpace)
                    Tab.Map -> MapScreen(vm, bottomSpace)
                    Tab.Watches -> WatchesScreen(vm, bottomSpace)
                    Tab.Settings -> SettingsScreen(vm, bottomSpace)
                }
            }
        }

        GlassNavigationBar(
            backdrop = rootBackdrop,
            items = listOf(
                GlassNavItem(stringResource(R.string.tab_overview), Icons.AutoMirrored.Filled.List),
                GlassNavItem(stringResource(R.string.tab_map), Icons.Filled.Place),
                GlassNavItem(stringResource(R.string.tab_watches), Icons.Filled.Notifications, watches.size.takeIf { it > 0 }?.toString()),
                GlassNavItem(stringResource(R.string.tab_settings), Icons.Filled.Settings),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { vm.selectTab(Tab.entries[it]) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = NavBarMargin)
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
        )

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace))

        BackHandler(enabled = openOffice == null && tab != Tab.Overview) { vm.selectTab(Tab.Overview) }

        // Keep the last office on screen while the page slides out.
        var shownOffice by remember { mutableStateOf<String?>(null) }
        if (openOffice != null) shownOffice = openOffice
        AnimatedVisibility(
            visible = openOffice != null,
            enter = slideInHorizontally { it / 4 } + fadeIn(tween(200)),
            exit = slideOutHorizontally { it / 4 } + fadeOut(tween(160)),
        ) {
            shownOffice?.let { officeId ->
                BackHandler { vm.closeOffice() }
                OfficeDetailScreen(vm, officeId, snackbar, onBack = vm::closeOffice)
            }
        }

        KeyboardBackHandler()
        BackgroundPrompt(vm, hasWatches = watches.isNotEmpty())
    }
}

/**
 * Asked once, after the first office is followed: alerts only arrive on time when
 * Android lets the app run in the background.
 */
@Composable
private fun BackgroundPrompt(vm: AppViewModel, hasWatches: Boolean) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(hasWatches) {
        if (hasWatches && Build.VERSION.SDK_INT >= 23 && context.isBatteryOptimized() && !vm.storedSettings().backgroundPromptShown) {
            visible = true
        }
    }
    if (!visible) return
    fun close() {
        visible = false
        vm.markBackgroundPromptShown()
    }
    AlertDialog(
        onDismissRequest = ::close,
        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
        title = { Text(stringResource(R.string.bg_prompt_title)) },
        text = { Text(stringResource(R.string.bg_prompt_body)) },
        confirmButton = {
            TextButton(onClick = {
                close()
                context.requestBackgroundRunning()
            }) { Text(stringResource(R.string.bg_prompt_continue)) }
        },
        dismissButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.bg_prompt_later)) } },
    )
}

/**
 * While the keyboard is open, back only closes it. With predictive back, Android may
 * hand the press to the app instead of the keyboard, which would leave the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardBackHandler() {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = imeVisible) {
        keyboard?.hide()
        focusManager.clearFocus()
    }
}

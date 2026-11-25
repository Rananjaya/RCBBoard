// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_COUNT
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_DONE
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_ENABLE
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_FEATURES
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_LANGUAGES
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_SELECT
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_SWITCH
import helium314.keyboard.settings.onboarding.Onboarding.PAGE_WELCOME
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RCBBoard tutorial, shown on first launch and from the main menu (General → Tutorial), modelled on
 * RCB Music's tutorial. Pages 2 and 3 use Android's own keyboard settings and keyboard picker; the
 * tutorial never asks for permissions. Only features that RCBBoard has (with default settings) are described.
 *
 * @param replay opened from the main menu: "Close" instead of "Skip", "Done" instead of "Get started"
 * @param onSkip Skip / Close
 * @param onFinish Get started / Done
 */
@Composable
fun OnboardingScreen(
    replay: Boolean,
    startPage: Int = PAGE_WELCOME,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, PAGE_COUNT - 1)) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == PAGE_COUNT - 1

    // Live setup state: Android's settings and keyboard picker are separate screens/dialogs, so check regularly.
    var enabled by remember { mutableStateOf(Onboarding.isEnabled(ctx)) }
    var current by remember { mutableStateOf(Onboarding.isCurrent(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            val nowEnabled = Onboarding.isEnabled(ctx)
            val nowCurrent = Onboarding.isCurrent(ctx)
            Onboarding.pageAfterSetupChange(pager.currentPage, enabled, nowEnabled, current, nowCurrent)
                ?.let { pager.animateScrollToPage(it) }
            enabled = nowEnabled
            current = nowCurrent
        }
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val openKeyboardSettings = {
        settingsLauncher.launch(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS).addCategory(Intent.CATEGORY_DEFAULT))
    }
    val chooseKeyboard = { Onboarding.imm(ctx).showInputMethodPicker() }
    val goTo = { target: Int -> scope.launch { pager.animateScrollToPage(target) }; Unit }

    BackHandler(enabled = page > 0 || replay) { if (page > 0) goTo(page - 1) else onSkip() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val counterDescription = stringResource(R.string.onboarding_page_counter_description, page + 1, PAGE_COUNT)
                Text(
                    stringResource(R.string.onboarding_page_counter, page + 1, PAGE_COUNT),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp).weight(1f).semantics { contentDescription = counterDescription },
                )
                if (!last || replay)
                    TextButton(onClick = onSkip) {
                        Text(stringResource(if (replay) R.string.onboarding_close else R.string.onboarding_skip))
                    }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                when (index) {
                    PAGE_WELCOME -> WelcomePage()
                    PAGE_ENABLE -> EnablePage(enabled, openKeyboardSettings)
                    PAGE_SELECT -> SelectPage(enabled, current, chooseKeyboard) { goTo(PAGE_ENABLE) }
                    PAGE_LANGUAGES -> LanguagesPage(current)
                    PAGE_SWITCH -> SwitchPage()
                    PAGE_FEATURES -> FeaturesPage()
                    PAGE_DONE -> DonePage(enabled, current, openKeyboardSettings, chooseKeyboard)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (page > 0) TextButton(onClick = { goTo(page - 1) }) { Text(stringResource(R.string.onboarding_back)) }
                }
                Dots(page)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Button(onClick = { if (last) onFinish() else goTo(page + 1) }) {
                        AnimatedContent(last, label = "cta") { isLast ->
                            Text(stringResource(when {
                                !isLast -> R.string.onboarding_next
                                replay -> R.string.onboarding_done
                                else -> R.string.onboarding_get_started
                            }))
                        }
                    }
                }
            }
        }
    }
}

// ---------- pages ----------

@Composable
private fun WelcomePage() = PageLayout(illustration = { AppIcon() }) {
    Title(R.string.onboarding_welcome_title)
    Body(R.string.onboarding_welcome_body)
    Bullets(R.string.onboarding_welcome_point_private, R.string.onboarding_welcome_point_languages,
        R.string.onboarding_welcome_point_suggestions)
}

@Composable
private fun EnablePage(enabled: Boolean, openSettings: () -> Unit) =
    PageLayout(illustration = { Illustration(R.drawable.ic_onboarding_enable) }) {
        Title(R.string.onboarding_enable_title)
        Body(R.string.onboarding_enable_body)
        Bullets(R.string.onboarding_enable_point_open, R.string.onboarding_enable_point_turn_on,
            R.string.onboarding_enable_point_warning)
        Spacer(Modifier.height(16.dp))
        StatusOrAction(enabled, R.string.onboarding_enable_done, R.string.onboarding_enable_action, openSettings)
    }

@Composable
private fun SelectPage(enabled: Boolean, current: Boolean, choose: () -> Unit, goToEnable: () -> Unit) =
    PageLayout(illustration = { Illustration(R.drawable.ic_onboarding_select) }) {
        Title(R.string.onboarding_select_title)
        Body(R.string.onboarding_select_body)
        Spacer(Modifier.height(16.dp))
        if (!enabled && !current) {
            InfoCard(stringResource(R.string.onboarding_select_needs_enable))
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = goToEnable, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_select_enable_action))
            }
        } else StatusOrAction(current, R.string.onboarding_select_done, R.string.onboarding_select_action, choose)
    }

@Composable
private fun LanguagesPage(current: Boolean) =
    PageLayout(illustration = { Illustration(R.drawable.ic_onboarding_languages) }) {
        Title(R.string.onboarding_languages_title)
        Body(R.string.onboarding_languages_body)
        Spacer(Modifier.height(16.dp))
        LanguageRow(R.string.onboarding_languages_english, R.string.onboarding_languages_english_detail, "Aa")
        Spacer(Modifier.height(8.dp))
        LanguageRow(R.string.onboarding_languages_singlish, R.string.onboarding_languages_singlish_detail, "අ")
        Spacer(Modifier.height(12.dp))
        // examples from the README / docs/singlish.md; they are typed exactly like this with the Singlish layout
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Example("mama", "මම")
            Example("sixhala", "සිංහල")
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_languages_more), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        TryField(current, R.string.onboarding_languages_try)
    }

@Composable
private fun SwitchPage() = PageLayout(illustration = { Illustration(R.drawable.ic_onboarding_switch) }) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.prefs() }
    var showKey by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY))
    }
    Title(R.string.onboarding_switch_title)
    Body(R.string.onboarding_switch_body)
    Bullets(R.string.onboarding_switch_point_comma, R.string.onboarding_switch_point_settings)
    Spacer(Modifier.height(16.dp))
    // the real setting (also in Preferences), so users can choose a visible key right here
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .toggleable(value = showKey, role = Role.Switch) {
                    showKey = it
                    prefs.edit { putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, it) }
                }
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_switch_key), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.onboarding_switch_key_summary), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = showKey, onCheckedChange = null)
        }
    }
}

@Composable
private fun FeaturesPage() = PageLayout(illustration = { Illustration(R.drawable.ic_onboarding_features) }) {
    Title(R.string.onboarding_features_title)
    Body(R.string.onboarding_features_body)
    Bullets(R.string.onboarding_features_toolbar, R.string.onboarding_features_comma, R.string.onboarding_features_emoji,
        R.string.onboarding_features_learning, R.string.onboarding_features_settings)
}

@Composable
private fun DonePage(enabled: Boolean, current: Boolean, openSettings: () -> Unit, choose: () -> Unit) =
    PageLayout(illustration = { Illustration(R.drawable.ic_onboarding_done) }) {
        Title(R.string.onboarding_done_title)
        Body(R.string.onboarding_done_body)
        Spacer(Modifier.height(16.dp))
        StatusRow(R.string.onboarding_status_enabled, enabled, R.string.onboarding_enable_action, openSettings)
        Spacer(Modifier.height(8.dp))
        StatusRow(R.string.onboarding_status_current, current, R.string.onboarding_select_action, choose, actionEnabled = enabled)
        ThemeChooser(current)
    }

/** Choose a look (like RCB Music's last page): quick keyboard themes, plus the full colour setting of Appearance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeChooser(current: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.prefs() }
    var selected by remember { mutableStateOf(Onboarding.quickTheme(prefs)) }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.onboarding_theme_title), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().semantics { heading() })
    Spacer(Modifier.height(8.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Onboarding.availableQuickThemes(Build.VERSION.SDK_INT).forEach { theme ->
            FilterChip(
                selected = theme == selected,
                onClick = { Onboarding.setQuickTheme(prefs, theme); selected = theme },
                label = { Text(stringResource(when (theme) {
                    Onboarding.QuickTheme.SYSTEM -> R.string.onboarding_theme_system
                    Onboarding.QuickTheme.LIGHT -> R.string.onboarding_theme_light
                    Onboarding.QuickTheme.DARK -> R.string.onboarding_theme_dark
                    Onboarding.QuickTheme.DYNAMIC -> R.string.onboarding_theme_dynamic
                })) },
            )
        }
    }
    // the same colour setting as in Appearance, for all other themes
    SettingsActivity.settingsContainer[Settings.PREF_THEME_COLORS]?.let { setting ->
        Spacer(Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            setting.Preference()
        }
        // a theme chosen there may not match a chip
        val changed = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        LaunchedEffect(changed?.value) { selected = Onboarding.quickTheme(prefs) }
    }
    Spacer(Modifier.height(12.dp))
    TryField(current, R.string.onboarding_theme_try)
}

/** A text field to try the keyboard. It works with any keyboard; the hint asks to choose RCBBoard first. */
@Composable
private fun TryField(current: Boolean, @StringRes label: Int) {
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(label)) },
        supportingText = if (current) null else { { Text(stringResource(R.string.onboarding_languages_try_needs_keyboard)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- building blocks ----------

/**
 * Portrait: picture above text. Landscape: picture beside text. Text scrolls for large fonts.
 * The choice uses the window size, not the space left by the keyboard: otherwise opening the keyboard for the
 * "Try it here" field would switch the layout, recreate the field and close the keyboard again.
 */
@Composable
private fun PageLayout(illustration: @Composable () -> Unit, text: @Composable ColumnScope.() -> Unit) {
    val configuration = LocalConfiguration.current
    Box(Modifier.fillMaxSize()) {
        if (configuration.screenWidthDp > configuration.screenHeightDp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(0.4f), contentAlignment = Alignment.Center) { illustration() }
                Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = text)
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    illustration()
                    Spacer(Modifier.height(28.dp))
                    text()
                }
            }
        }
    }
}

@Composable
private fun AppIcon() {
    Icon(painterResource(R.drawable.ic_launcher_rcb_full), null, Modifier.size(120.dp), tint = Color.Unspecified)
}

@Composable
private fun Illustration(@DrawableRes icon: Int) {
    Box(
        Modifier.size(140.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Title(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() })
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Bullets(@StringRes vararg items: Int) {
    Spacer(Modifier.height(16.dp))
    items.forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(item), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A ✓ status once done, otherwise the button that opens Android's screen for it. */
@Composable
private fun StatusOrAction(done: Boolean, @StringRes doneText: Int, @StringRes actionText: Int, action: () -> Unit) {
    AnimatedContent(done, label = "status") { isDone ->
        if (isDone) DoneCard(stringResource(doneText))
        else Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(actionText)) }
    }
}

@Composable
private fun DoneCard(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_onboarding_done), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun StatusRow(@StringRes label: Int, done: Boolean, @StringRes actionText: Int, action: () -> Unit, actionEnabled: Boolean = true) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 56.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                if (!done) Text(stringResource(R.string.onboarding_status_not_yet), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
            if (done) Icon(painterResource(R.drawable.ic_onboarding_done), null, tint = MaterialTheme.colorScheme.primary)
            else FilledTonalButton(onClick = action, enabled = actionEnabled) { Text(stringResource(actionText)) }
        }
    }
}

@Composable
private fun LanguageRow(@StringRes name: Int, @StringRes detail: Int, sample: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(sample, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Example(typed: String, result: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("$typed → $result", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun Dots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { i ->
            val selected = i == page
            val w by animateDpAsState(if (selected) 20.dp else 8.dp, label = "dot")
            val c by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotColor",
            )
            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
        }
    }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        Surface { OnboardingScreen(replay = false, onSkip = {}, onFinish = {}) }
    }
}

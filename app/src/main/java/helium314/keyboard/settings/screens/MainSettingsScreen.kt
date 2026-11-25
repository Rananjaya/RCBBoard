// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.onboarding.Onboarding
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchScreen(
        onClickBack = onClickBack,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_rcb_full),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.english_ime_name))
            }
        },
        filteredItems = { SettingsActivity.settingsContainer.filter(it) },
        itemContent = { it.Preference() },
    ) {
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        val ctx = LocalContext.current
        val showGestureData = JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).then(Modifier.padding(innerPadding)).padding(bottom = 8.dp)
            ) {
                SetupCard()
                MenuGroup(stringResource(R.string.main_menu_group_typing)) {
                    MenuItem(
                        title = stringResource(R.string.language_and_layouts_title),
                        summary = enabledSubtypes.joinToString(", ") { it.displayName() },
                        icon = R.drawable.ic_menu_languages,
                        onClick = onClickLanguage,
                    )
                    MenuItem(
                        title = stringResource(R.string.settings_screen_correction),
                        summary = stringResource(R.string.main_menu_correction_summary),
                        icon = R.drawable.ic_menu_text_correction,
                        onClick = onClickTextCorrection,
                    )
                    MenuItem(
                        title = stringResource(R.string.dictionary_settings_category),
                        summary = stringResource(R.string.main_menu_dictionaries_summary),
                        icon = R.drawable.ic_menu_dictionaries,
                        onClick = onClickDictionaries,
                    )
                    // glide typing: only if enabled in the build and a library is loaded (RcbFeatures.GLIDE_TYPING_ENABLED)
                    if (JniUtils.sHaveGestureLib)
                        MenuItem(
                            title = stringResource(R.string.settings_screen_gesture),
                            icon = R.drawable.ic_menu_gesture_typing,
                            onClick = onClickGestureTyping,
                        )
                    // we don't even show the menu if data gathering phase ended more than 2 weeks ago
                    if (showGestureData)
                        MenuItem(
                            title = stringResource(R.string.gesture_data_screen),
                            icon = R.drawable.ic_menu_gesture_data,
                            onClick = onClickDataGathering,
                        )
                }
                MenuGroup(stringResource(R.string.main_menu_group_look)) {
                    MenuItem(
                        title = stringResource(R.string.settings_screen_appearance),
                        summary = stringResource(R.string.main_menu_appearance_summary),
                        icon = R.drawable.ic_menu_appearance,
                        onClick = onClickAppearance,
                    )
                    MenuItem(
                        title = stringResource(R.string.settings_screen_toolbar),
                        summary = stringResource(R.string.main_menu_toolbar_summary),
                        icon = R.drawable.ic_menu_toolbar,
                        onClick = onClickToolbar,
                    )
                    MenuItem(
                        title = stringResource(R.string.settings_screen_secondary_layouts),
                        summary = stringResource(R.string.main_menu_secondary_layouts_summary),
                        icon = R.drawable.ic_menu_secondary_layouts,
                        onClick = onClickLayouts,
                    )
                }
                MenuGroup(stringResource(R.string.main_menu_group_general)) {
                    MenuItem(
                        title = stringResource(R.string.settings_screen_preferences),
                        summary = stringResource(R.string.main_menu_preferences_summary),
                        icon = R.drawable.ic_menu_preferences,
                        onClick = onClickPreferences,
                    )
                    MenuItem(
                        title = stringResource(R.string.settings_screen_advanced),
                        summary = stringResource(R.string.main_menu_advanced_summary),
                        icon = R.drawable.ic_menu_advanced,
                        onClick = onClickAdvanced,
                    )
                    MenuItem(
                        title = stringResource(R.string.main_menu_tutorial),
                        summary = stringResource(R.string.main_menu_tutorial_summary),
                        icon = R.drawable.ic_menu_tutorial,
                        onClick = { (ctx.getActivity() as? SettingsActivity)?.showOnboarding() },
                    )
                    MenuItem(
                        title = stringResource(R.string.settings_screen_about),
                        summary = stringResource(R.string.main_menu_about_summary),
                        icon = R.drawable.ic_menu_about,
                        onClick = onClickAbout,
                    )
                }
                // app version, below the last group
                Text(
                    text = stringResource(R.string.version_text, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp)
                )
            }
        }
    }
}

/**
 * Shown at the top while RCBBoard is not turned on or not the current keyboard (e.g. after skipping the tutorial).
 * It never blocks the settings; "Set up" opens the tutorial at the missing step.
 */
@Composable
private fun SetupCard() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(Onboarding.isEnabled(ctx)) }
    var current by remember { mutableStateOf(Onboarding.isCurrent(ctx)) }
    LaunchedEffect(Unit) { // Android's keyboard settings and picker are outside the app, so check regularly
        while (true) {
            delay(1000)
            enabled = Onboarding.isEnabled(ctx)
            current = Onboarding.isCurrent(ctx)
        }
    }
    if (!Onboarding.needsSetup(enabled, current)) return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(R.string.setup_card_title), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (!enabled) R.string.setup_card_not_enabled else R.string.setup_card_not_current),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { (ctx.getActivity() as? SettingsActivity)?.showOnboarding(Onboarding.setupStartPage(enabled, current)) }) {
                Text(stringResource(R.string.setup_card_action))
            }
        }
    }
}

/** A titled group of menu items on a rounded card. */
@Composable
private fun MenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() }
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** One menu entry: icon in a tonal container, title and optional summary, and a chevron. At least 72 dp high. */
@Composable
private fun MenuItem(title: String, @DrawableRes icon: Int, onClick: () -> Unit, summary: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null, // the title says what it is
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrEmpty())
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        Spacer(Modifier.width(8.dp))
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Icon(
            painter = painterResource(R.drawable.ic_menu_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).graphicsLayer {
                // point the other way in right-to-left layouts
                scaleX = if (rtl) -1f else 1f
            }
        )
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

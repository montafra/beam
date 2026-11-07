package montafra.beam.ui

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import montafra.beam.BeamTempWidgetProvider
import montafra.beam.R
import montafra.beam.settingsName
import montafra.beam.settingsUpdateInd
import montafra.beam.supportedAppLanguages
import montafra.beam.ui.theme.BeamCard
import montafra.beam.ui.theme.CardGap
import montafra.beam.ui.theme.cardShapeBottom
import montafra.beam.ui.theme.cardShapeMiddle
import montafra.beam.ui.theme.cardShapeTop

/**
 * In-app language picker, reached only below API 33 - from 33 the Language row opens the system's
 * per-app language page instead. The pick is applied per process by appLanguageOverride().
 */
@Composable
fun LanguageSettingsScreen(navController: BeamNavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val haptic = LocalTapHaptics.current
    val prefs = remember { context.getSharedPreferences(settingsName, Context.MODE_PRIVATE) }

    var selected by remember { mutableStateOf(prefs.getString("appLanguage", "") ?: "") }
    // "" is the system default, listed first.
    val tags = remember { listOf("") + supportedAppLanguages(context) }
    val uiLocale = LocalConfiguration.current.locales[0]
    val systemLocale = remember { Resources.getSystem().configuration.locales[0] }
    val systemDefault = stringResource(R.string.languageSystemDefault)

    SettingsScaffold(
        title = stringResource(R.string.language),
        onBack = { navController.popBackStack() },
    ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                tags.forEachIndexed { i, tag ->
                    // Each language is named in itself, with its name in the current UI language
                    // underneath; the system row shows which language "system" resolves to.
                    val (headline, supporting) = if (tag.isEmpty()) {
                        systemDefault to systemLocale.nativeLanguageName()
                    } else {
                        val locale = Locale.forLanguageTag(tag)
                        val native = locale.nativeLanguageName()
                        native to locale.languageNameIn(uiLocale).takeIf { it != native }
                    }
                    if (i > 0) CardGap()
                    BeamCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = when (i) {
                            0 -> cardShapeTop()
                            tags.lastIndex -> cardShapeBottom()
                            else -> cardShapeMiddle()
                        },
                    ) {
                        ListItem(
                            headlineContent = { Text(headline) },
                            supportingContent = if (supporting != null) { { Text(supporting) } } else null,
                            trailingContent = { RadioButton(selected = tag == selected, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (tag == selected) return@clickable
                                selected = tag
                                prefs.edit().putString("appLanguage", tag).commit()
                                // :batteryStatus builds strings the main screen shows and the widget
                                // draws its own label; neither re-reads settings unless told to.
                                context.sendBroadcast(Intent(settingsUpdateInd).setPackage(context.packageName))
                                BeamTempWidgetProvider.requestUpdate(context)
                                // The activity's resources are fixed when it is created. The nav
                                // stack is saveable, so the new instance reopens on this page.
                                activity?.recreate()
                            },
                        )
                    }
                }
            }
    }
}

private fun Locale.nativeLanguageName() = languageNameIn(this)

private fun Locale.languageNameIn(display: Locale) =
    getDisplayLanguage(display).replaceFirstChar { it.titlecase(display) }

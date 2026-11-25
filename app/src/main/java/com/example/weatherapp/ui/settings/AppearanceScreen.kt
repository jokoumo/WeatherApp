package com.example.weatherapp.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.ThemeMode
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.SecondaryScreenScaffold

/** German label of a theme option. */
@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

/** German explanation of what choosing this option does. */
@StringRes
fun ThemeMode.summaryRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system_summary
    ThemeMode.LIGHT -> R.string.theme_mode_light_summary
    ThemeMode.DARK -> R.string.theme_mode_dark_summary
}

/** The options in the order the user sees them; the first one is the default. */
val themeModeOptions: List<ThemeMode> = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

/**
 * Appearance page with exactly three single-choice options.
 *
 * Selecting an option is applied immediately by the caller, which recomposes the whole app
 * without leaving this screen, so the effect of every choice is visible right where it is made.
 */
@Composable
fun AppearanceScreen(
    selectedMode: ThemeMode,
    persistenceFailed: Boolean,
    onSelectMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryScreenScaffold(
        title = stringResource(R.string.appearance_screen_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.appearance_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            themeModeOptions.forEach { mode ->
                ThemeModeRow(
                    mode = mode,
                    selected = mode == selectedMode,
                    onSelect = { onSelectMode(mode) },
                )
            }
        }
        if (persistenceFailed) {
            InfoNote(text = stringResource(R.string.theme_persistence_failed))
        }
    }
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(mode.summaryRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

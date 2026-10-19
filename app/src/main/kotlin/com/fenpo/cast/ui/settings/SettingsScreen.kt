package com.fenpo.cast.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.fenpo.cast.BuildConfig
import com.fenpo.cast.R
import com.fenpo.cast.settings.CastSettings
import com.fenpo.cast.settings.ThemeMode
import com.fenpo.cast.settings.VideoQuality
import com.fenpo.cast.ui.AppLanguage
import com.fenpo.cast.ui.theme.fenpoButtonColors
import com.fenpo.cast.ui.theme.fenpoOutlinedButtonColors

/** Everything Settings can change, passed in by the activity. */
data class SettingsActions(
    val setDeviceName: (String) -> Unit,
    val setRequireCode: (Boolean) -> Unit,
    val setQuality: (VideoQuality) -> Unit,
    val setDirectVideo: (Boolean) -> Unit,
    val setTheme: (ThemeMode) -> Unit,
    val setLanguage: (AppLanguage) -> Unit,
)

private enum class OpenDialog { None, Name, Quality, Theme, Language }

/**
 * Settings, as one focusable list for the remote.
 *
 * @param deviceName Name the iPhone currently sees.
 * @param networkAddress The TV's IPv4 address, or null without a network.
 */
@Composable
fun SettingsScreen(
    settings: CastSettings,
    deviceName: String,
    language: AppLanguage,
    networkAddress: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.None) }
    // The row that had focus survives the activity being recreated (a language change).
    var focusedRow by rememberSaveable { mutableStateOf(ROW_NAME) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }

    fun Modifier.row(key: String): Modifier =
        focusRequester(requesters.getOrPut(key) { FocusRequester() })
            .onFocusChanged { if (it.isFocused) focusedRow = key }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 56.dp, vertical = 36.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 880.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_section_receiver)) }
            item {
                SettingItem(
                    title = stringResource(R.string.settings_name),
                    value = deviceName,
                    onClick = { dialog = OpenDialog.Name },
                    modifier = Modifier.row(ROW_NAME),
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.settings_require_code),
                    summary = stringResource(R.string.settings_require_code_summary),
                    checked = settings.requireCode,
                    onCheckedChange = actions.setRequireCode,
                    modifier = Modifier.row("code"),
                )
            }
            item {
                SettingItem(
                    title = stringResource(R.string.settings_quality),
                    summary = stringResource(R.string.settings_quality_summary),
                    value = qualityLabel(settings.quality),
                    onClick = { dialog = OpenDialog.Quality },
                    modifier = Modifier.row("quality"),
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.settings_direct_video),
                    summary = stringResource(R.string.settings_direct_video_summary),
                    checked = settings.directVideo,
                    onCheckedChange = actions.setDirectVideo,
                    modifier = Modifier.row("video"),
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_appearance)) }
            item {
                SettingItem(
                    title = stringResource(R.string.settings_theme),
                    value = themeLabel(settings.theme),
                    onClick = { dialog = OpenDialog.Theme },
                    modifier = Modifier.row("theme"),
                )
            }
            item {
                SettingItem(
                    title = stringResource(R.string.settings_language),
                    value = languageLabel(language),
                    onClick = { dialog = OpenDialog.Language },
                    modifier = Modifier.row("language"),
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_about)) }
            item {
                SettingItem(
                    title = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME,
                    modifier = Modifier.row("version"),
                )
            }
            item {
                SettingItem(
                    title = stringResource(R.string.settings_address),
                    value = networkAddress ?: stringResource(R.string.settings_address_none),
                    modifier = Modifier.row("address"),
                )
            }
            item {
                SettingItem(
                    title = stringResource(R.string.settings_license),
                    summary = stringResource(R.string.settings_license_value),
                    modifier = Modifier.row("license"),
                )
            }
        }
    }

    // Read before the first frame: the system's default focus would overwrite focusedRow.
    val restoredRow = remember { focusedRow }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters[restoredRow]?.requestFocus() }
    }

    when (dialog) {
        OpenDialog.Name -> {
            NameDialog(
                current = settings.deviceName.orEmpty(),
                placeholder = deviceName,
                onSave = {
                    actions.setDeviceName(it)
                    dialog = OpenDialog.None
                },
                onDismiss = { dialog = OpenDialog.None },
            )
        }

        OpenDialog.Quality -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_quality),
                options = VideoQuality.entries,
                selected = settings.quality,
                label = { qualityLabel(it) },
                onSelect = {
                    actions.setQuality(it)
                    dialog = OpenDialog.None
                },
                onDismiss = { dialog = OpenDialog.None },
            )
        }

        OpenDialog.Theme -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = settings.theme,
                label = { themeLabel(it) },
                onSelect = {
                    actions.setTheme(it)
                    dialog = OpenDialog.None
                },
                onDismiss = { dialog = OpenDialog.None },
            )
        }

        OpenDialog.Language -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_language),
                options = AppLanguage.entries,
                selected = language,
                label = { languageLabel(it) },
                onSelect = {
                    dialog = OpenDialog.None
                    actions.setLanguage(it)
                },
                onDismiss = { dialog = OpenDialog.None },
            )
        }

        OpenDialog.None -> {}
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp, start = 16.dp),
    )
}

@Composable
private fun SettingItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    ListItem(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = value?.let { { Text(it, style = MaterialTheme.typography.titleSmall) } },
        modifier = modifier,
    )
}

@Composable
private fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier,
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            options.forEach { option ->
                ListItem(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    headlineContent = { Text(label(option)) },
                    leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                    modifier = if (option == selected) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { selectedFocus.requestFocus() }
    }
}

@Composable
private fun NameDialog(
    current: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current) }
    val fieldFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(text = stringResource(R.string.settings_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_name_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(2.dp, MaterialTheme.colorScheme.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_NAME_LENGTH) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row {
                OutlinedButton(onClick = onDismiss, colors = fenpoOutlinedButtonColors()) {
                    Text(stringResource(R.string.settings_cancel))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { onSave(text) },
                    colors = fenpoButtonColors(),
                ) { Text(stringResource(R.string.settings_save)) }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }
}

@Composable
private fun DialogSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        colors =
            SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        modifier = Modifier.widthIn(min = 420.dp, max = 560.dp),
    ) {
        Column(modifier = Modifier.padding(32.dp)) { content() }
    }
}

@Composable
private fun qualityLabel(quality: VideoQuality): String =
    when (quality) {
        VideoQuality.Auto -> stringResource(R.string.quality_auto)
        VideoQuality.P1080 -> stringResource(R.string.quality_1080)
        VideoQuality.P720 -> stringResource(R.string.quality_720)
    }

@Composable
private fun themeLabel(theme: ThemeMode): String =
    when (theme) {
        ThemeMode.System -> stringResource(R.string.theme_system)
        ThemeMode.Light -> stringResource(R.string.theme_light)
        ThemeMode.Dark -> stringResource(R.string.theme_dark)
    }

@Composable
private fun languageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.System -> stringResource(R.string.language_system)
        AppLanguage.Turkish -> stringResource(R.string.language_turkish)
        AppLanguage.English -> stringResource(R.string.language_english)
    }

// The RAOP service name is "<12 hex digits>@<name>" and an mDNS label holds 63 bytes: 25 characters
// fit even when every one of them takes two bytes in UTF-8 (ç, ğ, ı, ö, ş, ü).
private const val MAX_NAME_LENGTH = 25

private const val ROW_NAME = "name"

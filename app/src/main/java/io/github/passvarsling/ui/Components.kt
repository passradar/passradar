package io.github.passvarsling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.passvarsling.R
import io.github.passvarsling.data.Availability
import io.github.passvarsling.data.Watch
import io.github.passvarsling.domain.Dates
import io.github.passvarsling.domain.WaitLevel
import io.github.passvarsling.domain.WaitTime
import io.github.passvarsling.domain.WaitUnit
import io.github.passvarsling.domain.parseDateOrNull
import io.github.passvarsling.ui.theme.LocalWaitPalette
import io.github.passvarsling.ui.theme.WaitColors
import io.github.passvarsling.ui.theme.WaitTone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat

@Composable
fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

fun WaitLevel.color(): Color? = when (this) {
    WaitLevel.Short -> WaitColors.Short
    WaitLevel.Medium -> WaitColors.Medium
    WaitLevel.Long -> WaitColors.Long
    WaitLevel.VeryLong -> WaitColors.VeryLong
    WaitLevel.NoneFree -> WaitColors.NoneFree
    WaitLevel.Unknown -> null
}

@Composable
fun WaitLevel.tone(): WaitTone? {
    val palette = LocalWaitPalette.current
    return when (this) {
        WaitLevel.Short -> palette.short
        WaitLevel.Medium -> palette.medium
        WaitLevel.Long -> palette.long
        WaitLevel.VeryLong -> palette.veryLong
        WaitLevel.NoneFree -> palette.noneFree
        WaitLevel.Unknown -> null
    }
}

fun waitLevel(availability: Availability?, today: LocalDate): WaitLevel =
    WaitLevel.of(availability != null, availability?.earliestDate, today)

@Composable
fun WaitDot(level: WaitLevel, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val color = level.color()
    val shape = Modifier.size(size).clip(CircleShape)
    Box(
        modifier.then(
            if (color != null) shape.background(color)
            else shape.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ),
    )
}

/** "3 dager", "4 uker", "3 mnd." */
@Composable
fun waitUnitLabel(value: Long, unit: WaitUnit): String = pluralStringResource(
    when (unit) {
        WaitUnit.Days -> R.plurals.wait_days
        WaitUnit.Weeks -> R.plurals.wait_weeks
        WaitUnit.Months -> R.plurals.wait_months
    },
    value.toInt(),
)

/** Spoken and written waiting time: "i dag", "i morgen", "3 dager", "4 uker". */
@Composable
fun waitText(days: Long): String = when (days) {
    0L -> stringResource(R.string.wait_today)
    1L -> stringResource(R.string.wait_tomorrow)
    else -> WaitTime.compact(days).let { "${it.value} ${waitUnitLabel(it.value, it.unit)}" }
}

/**
 * The waiting time as a coloured tile: a big number over its unit. Shows "?" before
 * the first check and a dash when nothing is free.
 */
@Composable
fun WaitBadge(
    availability: Availability?,
    today: LocalDate,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    /** The office doesn't offer the chosen service, so there is nothing to wait for. */
    notOffered: Boolean = false,
) {
    val level = waitLevel(availability, today)
    val tone = if (notOffered) null else level.tone()
    val earliest = availability?.earliestDate
    val (big, small) = when {
        notOffered -> "–" to ""
        availability == null -> "?" to stringResource(R.string.wait_unknown_short)
        earliest == null -> "–" to stringResource(R.string.wait_none_short)
        else -> {
            val days = WaitTime.days(today, earliest)
            when (days) {
                0L -> stringResource(R.string.wait_today_short) to ""
                else -> WaitTime.compact(days).let { it.value.toString() to waitUnitLabel(it.value, it.unit) }
            }
        }
    }
    val description = when {
        notOffered -> stringResource(R.string.watch_not_offered)
        availability == null -> stringResource(R.string.earliest_unknown)
        earliest == null -> stringResource(R.string.earliest_none)
        else -> stringResource(R.string.wait_description, waitText(WaitTime.days(today, earliest)))
    }
    val shape = RoundedCornerShape(size * 0.32f)
    Column(
        modifier
            .size(size)
            .clip(shape)
            .then(if (tone != null) Modifier.background(tone.container) else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape))
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = tone?.content ?: MaterialTheme.colorScheme.onSurfaceVariant
        val bigStyle = if (size >= 72.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge
        Text(big, style = bigStyle.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1, textAlign = TextAlign.Center)
        if (small.isNotEmpty()) {
            Text(small, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    }
}

/** "tor. 14. jan. 2027", or why there is no date. */
@Composable
fun earliestLine(availability: Availability?): String {
    val earliest = availability?.earliestDate
    return when {
        availability == null -> stringResource(R.string.earliest_unknown)
        earliest == null -> stringResource(R.string.earliest_none)
        else -> Format.dayDate(earliest, currentLocale())
    }
}

/** What the last background check of a watch found, when the overview has nothing newer. */
fun Watch.snapshotAvailability(today: LocalDate): Availability? =
    if (!baselineDone) null
    else Availability.from(knownDates.mapNotNull(::parseDateOrNull), today, lastChecked)

@Composable
fun FollowButton(followed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (followed) R.string.watch_remove else R.string.watch_add)
    if (followed) {
        FilledTonalIconButton(
            onClick = onToggle,
            modifier = modifier,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Icon(Icons.Filled.Notifications, contentDescription = description) }
    } else {
        IconButton(onClick = onToggle, modifier = modifier) {
            Icon(Icons.Outlined.Notifications, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A rounded group of settings or facts, like Android's own settings screens. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 8.dp), content = content)
    }
}

@Composable
fun NoticeCard(title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.size(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

/** Picks the last date worth a notification. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinePickerDialog(initial: LocalDate?, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val today = Dates.today()
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today.plusWeeks(4)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    stringResource(R.string.deadline_dialog_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Small "12 km" label. */
@Composable
fun distanceLabel(km: Double): String =
    if (km < 10) stringResource(R.string.distance_km_decimal, km) else stringResource(R.string.distance_km, km.toInt())

/** Extra side padding that keeps content at a readable width on tablets and in landscape. */
@Composable
fun readableSidePadding(maxWidth: Dp = 680.dp): Dp {
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return ((windowWidth - maxWidth) / 2).coerceAtLeast(0.dp)
}

/**
 * Search fields: the keyboard's action key closes the keyboard, and the keyboard only
 * opens on a tap. Android 8.1 and older give the first field focus when a screen opens,
 * which would otherwise pop the keyboard up by itself.
 */
val SearchKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false)

@Composable
fun searchKeyboardActions(): KeyboardActions {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    return KeyboardActions(onSearch = {
        // Hide the keyboard before the field lets go of focus: Android 8.1 and older
        // ignore a later request and leave the keyboard up.
        ContextCompat.getSystemService(view.context, InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        focusManager.clearFocus()
    })
}

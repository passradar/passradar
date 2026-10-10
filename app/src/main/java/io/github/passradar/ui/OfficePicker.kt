package io.github.passradar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.passradar.R
import io.github.passradar.domain.Dates
import io.github.passradar.domain.distanceKm
import io.github.passradar.domain.matchesSearch
import io.github.passradar.ui.glass.PillShape
import kotlinx.coroutines.launch

/** Choose many offices at once; the user gets alerts from all of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficePickerSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val offices by vm.offices.collectAsStateWithLifecycle()
    val availability by vm.availability.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val today = Dates.today()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val requestNotifications = rememberNotificationPermission()

    val settings by vm.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(watches.mapTo(HashSet()) { it.officeId }.toSet()) }
    var search by remember { mutableStateOf("") }
    var nearMe by remember { mutableStateOf(settings.sortByDistance && home != null) }
    val requestLocation = rememberLocationRequest { location ->
        if (location != null) {
            vm.setHome(location.latitude, location.longitude)
            nearMe = true
        } else {
            vm.locationUnavailable()
        }
    }

    val position = home
    val shown = remember(offices, availability, search, nearMe, position) {
        val matching = offices.filter { matchesSearch(it, search) }
        if (nearMe && position != null) {
            matching.sortedBy { distanceKm(position.lat, position.lon, it.lat ?: 0.0, it.lon ?: 0.0) }
        } else {
            matching.sortedBy { availability[it.id]?.earliest ?: "9999" }
        }
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        KeyboardBackHandler()
        KeepFocusOutOfFields()
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(2.dp))
                Hint(stringResource(R.string.picker_hint))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint_short), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = SearchKeyboardOptions,
                    keyboardActions = searchKeyboardActions(),
                    shape = PillShape,
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = nearMe && position != null,
                    onClick = { if (position != null) nearMe = !nearMe else requestLocation() },
                    label = { Text(stringResource(R.string.picker_near_me)) },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = !nearMe || position == null,
                    onClick = { nearMe = false },
                    label = { Text(stringResource(R.string.picker_shortest)) },
                )
                if (nearMe && position != null) {
                    FilterChip(
                        selected = false,
                        onClick = { selected = selected + shown.take(5).map { it.id } },
                        label = { Text(stringResource(R.string.picker_pick_nearest)) },
                    )
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { office ->
                    val checked = office.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox) {
                                selected = if (checked) selected - office.id else selected + office.id
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(office.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val details = listOfNotNull(
                                earliestLine(availability[office.id]),
                                position?.let { distanceLabel(distanceKm(it.lat, it.lon, office.lat ?: 0.0, office.lon ?: 0.0)) },
                            ).joinToString(" · ")
                            Hint(details)
                        }
                        Spacer(Modifier.width(8.dp))
                        WaitBadge(availability[office.id], today, size = 46.dp)
                    }
                }
            }
            Button(
                onClick = {
                    if (selected.isNotEmpty()) requestNotifications()
                    vm.setFollowed(selected)
                    close()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                shape = PillShape,
            ) {
                Text(
                    if (selected.isEmpty()) stringResource(R.string.picker_save_none)
                    else pluralStringResource(R.plurals.picker_save, selected.size, selected.size),
                )
            }
        }
    }
}

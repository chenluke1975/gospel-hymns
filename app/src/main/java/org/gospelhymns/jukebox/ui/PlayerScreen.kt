package org.gospelhymns.jukebox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.gospelhymns.jukebox.LyricMode
import org.gospelhymns.jukebox.MainViewModel
import org.gospelhymns.jukebox.PlayerUiState
import org.gospelhymns.jukebox.data.HymnDetail
import org.gospelhymns.jukebox.data.PersonCredit
import org.gospelhymns.jukebox.data.SOURCE_HYMNAL
import org.gospelhymns.jukebox.data.SOURCE_UNKNOWN
import org.gospelhymns.jukebox.data.Stanza

@Composable
fun PlayerScreen(vm: MainViewModel, state: PlayerUiState, onAuthor: (String) -> Unit) {
    var about by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Zh.appName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { about = true }) {
                Icon(Icons.Filled.Info, contentDescription = Zh.about)
            }
        }
        Header(vm, state, onAuthor)
        Transport(vm, state)
        Text(Zh.lyricsPanel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        ModeRow(state.lyricMode, vm::setLyricMode)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            LyricsBody(state)
            BioBlock(vm, state.hymn, onAuthor)
        }
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(Zh.about) },
            text = { Text(Zh.aboutBody) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearCache()
                    about = false
                }) { Text(Zh.clearCache) }
            },
            dismissButton = { TextButton(onClick = { about = false }) { Text(Zh.back) } },
        )
    }
}

@Composable
private fun Header(vm: MainViewModel, state: PlayerUiState, onAuthor: (String) -> Unit) {
    val hymn = state.hymn?.takeIf { it.number == state.number }
    val place = vm.catalog.place(state.number)
    Text("第 ${state.number} 首", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(
        hymn?.chineseTitle?.ifBlank { "第 ${state.number} 首" } ?: "第 ${state.number} 首",
        style = MaterialTheme.typography.headlineMedium,
    )
    if (!hymn?.englishTitle.isNullOrBlank()) {
        Text(
            hymn?.englishTitle.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (place != null) {
        Text("${place.category} · ${place.subcategory}", style = MaterialTheme.typography.bodySmall)
    }
    val credits = hymn ?: run {
        val credit = vm.catalog.credit(state.number)
        credit?.let {
            HymnDetail(number = state.number, lyricists = it.lyricists, composers = it.composers)
        }
    }
    if (credits != null) {
        CreditLine(Zh.lyricist, credits.lyricists, onAuthor)
        CreditLine(Zh.composer, credits.composers, onAuthor)
        val filled = (credits.lyricists + credits.composers).any { it.source != SOURCE_HYMNAL && it.source != SOURCE_UNKNOWN }
        if (filled) {
            Text(Zh.filled, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CreditLine(label: String, people: List<PersonCredit>, onAuthor: (String) -> Unit) {
    if (people.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        people.forEachIndexed { index, person ->
            TextButton(
                onClick = { onAuthor(person.name) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(person.label())
            }
            if (index != people.lastIndex) Text("、")
        }
    }
}

@Composable
private fun Transport(vm: MainViewModel, state: PlayerUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.numberInput,
            onValueChange = vm::onNumberInput,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(Zh.number) },
            placeholder = { Text(Zh.numberHint) },
        )
        TextButton(onClick = { vm.submitNumber(autoplay = true) }) { Text(Zh.go) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::previous) { Icon(Icons.Filled.SkipPrevious, contentDescription = Zh.prev) }
        IconButton(onClick = vm::togglePlay, enabled = !state.loading) {
            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (state.isPlaying) Zh.pause else Zh.play)
        }
        IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, contentDescription = Zh.next) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(state.positionMs), style = MaterialTheme.typography.bodySmall)
        Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall)
    }
    val duration = state.durationMs.coerceAtLeast(0L)
    if (duration > 0L) {
        Slider(
            value = state.positionMs.coerceIn(0L, duration).toFloat(),
            onValueChange = { vm.onSeek(it.toLong()) },
            onValueChangeFinished = vm::finishSeek,
            valueRange = 0f..duration.toFloat(),
        )
    } else if (state.loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
}

@Composable
private fun ModeRow(mode: LyricMode, onMode: (LyricMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip(Zh.chinese, mode == LyricMode.Chinese) { onMode(LyricMode.Chinese) }
        ModeChip(Zh.english, mode == LyricMode.English) { onMode(LyricMode.English) }
        ModeChip(Zh.bilingual, mode == LyricMode.Both) { onMode(LyricMode.Both) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun LyricsBody(state: PlayerUiState) {
    val hymn = state.hymn?.takeIf { it.number == state.number }
    if (state.loading && hymn == null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
            CircularProgressIndicator()
            Text(Zh.loading, modifier = Modifier.padding(start = 12.dp))
        }
        return
    }
    if (hymn == null) {
        Text(Zh.loadPrompt, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    when (state.lyricMode) {
        LyricMode.Chinese -> StanzaList(hymn.chineseStanzas, Zh.noChinese)
        LyricMode.English -> StanzaList(hymn.englishStanzas, Zh.noEnglish)
        LyricMode.Both -> Bilingual(hymn)
    }
}

@Composable
private fun Bilingual(hymn: HymnDetail) {
    val count = maxOf(hymn.chineseStanzas.size, hymn.englishStanzas.size)
    if (count == 0) {
        Text(Zh.noChinese)
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(Zh.chinese, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(Zh.english, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
    for (index in 0 until count) {
        val zh = hymn.chineseStanzas.getOrNull(index)
        val en = hymn.englishStanzas.getOrNull(index)
        Text(
            zh?.label ?: en?.label ?: "",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(zh?.lines?.joinToString("\n") ?: Zh.emptySide, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text(en?.lines?.joinToString("\n") ?: Zh.emptySide, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StanzaList(stanzas: List<Stanza>, empty: String) {
    if (stanzas.isEmpty()) {
        Text(empty, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    stanzas.forEach { stanza ->
        Text(stanza.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
        Text(stanza.lines.joinToString("\n"))
    }
}

@Composable
private fun BioBlock(vm: MainViewModel, hymn: HymnDetail?, onAuthor: (String) -> Unit) {
    if (hymn == null) return
    val people = (hymn.lyricists + hymn.composers).distinctBy { it.name }
    if (people.isEmpty()) return
    Text(Zh.bio, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
    people.forEach { person ->
        TextButton(onClick = { onAuthor(person.name) }) { Text(person.label()) }
        Text(vm.catalog.bio(person.name), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(total / 60L, total % 60L)
}

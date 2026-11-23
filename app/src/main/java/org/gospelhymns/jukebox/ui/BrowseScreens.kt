package org.gospelhymns.jukebox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.gospelhymns.jukebox.MainViewModel
import org.gospelhymns.jukebox.data.AuthorGroup
import org.gospelhymns.jukebox.data.searchHymns

@Composable
fun CategoryScreen(vm: MainViewModel, onOpen: (Int) -> Unit) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                Zh.categories,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        vm.catalog.categories.forEach { category ->
            item(key = "cat-${category.id}") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openId = if (openId == category.id) null else category.id }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(category.name, style = MaterialTheme.typography.titleLarge)
                    Text("${category.hymnCount} ${Zh.hymns}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
            if (openId == category.id) {
                category.subcategories.forEach { sub ->
                    item(key = "sub-${category.id}-${sub.name}") {
                        Text(
                            sub.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
                        )
                    }
                    items(sub.hymns, key = { "h-$it" }) { number ->
                        HymnLine(vm, number, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: MainViewModel, initialQuery: String, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val sync = vm.lyricSync.value
    val hits = searchHymns(query, vm.catalog, vm.cachedHymns())
    Column(Modifier.fillMaxSize()) {
        Text(Zh.search, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text(Zh.search) },
            placeholder = { Text(Zh.searchHint) },
        )
        Text(
            Zh.searchHelp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LyricDownloadCard(sync, vm::startLyricDownload, vm::stopLyricDownload)
        if (query.isNotBlank() && hits.isEmpty()) {
            Text(Zh.noResults, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(hits, key = { it.number }) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(hit.number) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    val title = hit.title?.takeIf { it.isNotBlank() } ?: vm.catalog.place(hit.number)?.let { "${it.category} · ${it.subcategory}" }
                    Text("第 ${hit.number} 首${if (title != null) "  $title" else ""}", style = MaterialTheme.typography.titleMedium)
                    if (hit.matchLine != null) {
                        HighlightedLine(hit.matchLine, query)
                    }
                    Text(hit.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LyricDownloadCard(
    sync: org.gospelhymns.jukebox.data.LyricSyncProgress,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        when {
            sync.complete -> Text(Zh.downloadDone, style = MaterialTheme.typography.bodyMedium)
            sync.running -> {
                Text(
                    "${Zh.downloadRunning}　${sync.saved} / ${sync.total}" +
                        (sync.current?.let { "　第 $it 首" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val fraction = if (sync.total == 0) 0f else sync.saved.toFloat() / sync.total
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                OutlinedButton(onClick = onStop) { Text(Zh.stopDownload) }
            }
            else -> {
                Text(Zh.downloadOffer, style = MaterialTheme.typography.bodySmall)
                if (!sync.note.isNullOrBlank()) {
                    Text(sync.note, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onStart) { Text(Zh.downloadLyrics) }
                    Text(
                        "　${sync.saved} / ${sync.total}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightedLine(line: String, query: String) {
    val needle = query.trim()
    val start = if (needle.isEmpty()) -1 else line.indexOf(needle, ignoreCase = true)
    val mark = SpanStyle(background = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    val text = buildAnnotatedString {
        if (start < 0) {
            append(line)
        } else {
            append(line.substring(0, start))
            withStyle(mark) { append(line.substring(start, start + needle.length)) }
            append(line.substring(start + needle.length))
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
}

@Composable
fun AuthorListScreen(vm: MainViewModel, onAuthor: (String) -> Unit) {
    val groups = vm.catalog.authorGroups
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(Zh.authors, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        }
        items(groups, key = { it.name }) { group ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onAuthor(group.name) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text("${group.hymns.size} ${Zh.hymns}", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun AuthorDetailScreen(vm: MainViewModel, name: String, onOpen: (Int) -> Unit) {
    val group: AuthorGroup? = vm.catalog.author(name)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp))
            Text(Zh.bio, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            Text(vm.catalog.bio(name), modifier = Modifier.padding(16.dp))
            Text(
                if (group == null) Zh.noResults else "${group.hymns.size} ${Zh.hymns}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(group?.hymns.orEmpty(), key = { "${it.number}-${it.role}" }) { hymn ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(hymn.number) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                val cached = vm.catalog.credit(hymn.number)
                val title = vm.peekTitle(hymn.number)
                Text("第 ${hymn.number} 首${if (title.isNullOrBlank()) "" else "  $title"}", style = MaterialTheme.typography.titleMedium)
                Text(hymn.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (cached != null) {
                    val other = (cached.lyricists + cached.composers).joinToString("、") { it.name }
                    if (other.isNotBlank()) Text(other, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun HymnLine(vm: MainViewModel, number: Int, onOpen: (Int) -> Unit) {
    val credit = vm.catalog.credit(number)
    val title = vm.peekTitle(number)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(number) }
            .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            "第 $number 首${if (title.isNullOrBlank()) "" else "  $title"}",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (credit != null) {
            val lyric = credit.lyricists.joinToString("、") { it.name }.ifBlank { Zh.unknown }
            val music = credit.composers.joinToString("、") { it.name }.ifBlank { Zh.unknown }
            Text("${Zh.lyricist} $lyric · ${Zh.composer} $music", style = MaterialTheme.typography.bodySmall)
        }
    }
}

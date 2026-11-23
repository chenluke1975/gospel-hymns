package org.gospelhymns.jukebox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.gospelhymns.jukebox.data.Catalog
import org.gospelhymns.jukebox.data.HymnDetail
import org.gospelhymns.jukebox.data.HymnRepository
import org.gospelhymns.jukebox.data.LyricSyncProgress
import org.gospelhymns.jukebox.player.PlayerHolder
import org.gospelhymns.jukebox.ui.Zh

enum class LyricMode { Chinese, English, Both }

data class PlayerUiState(
    val number: Int = 1,
    val numberInput: String = "1",
    val hymn: HymnDetail? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val lyricMode: LyricMode = LyricMode.Both,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val seeking: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val gospel = app as GospelApp
    val catalog: Catalog = gospel.catalog
    private val repository: HymnRepository = gospel.repository
    private val player: PlayerHolder = gospel.playerHolder

    var state = androidx.compose.runtime.mutableStateOf(PlayerUiState())
        private set
    var lyricSync = androidx.compose.runtime.mutableStateOf(LyricSyncProgress(0, 0, null, false, null))
        private set

    private var retriedNumber = -1
    private var downloadJob: Job? = null

    init {
        player.onError = { code ->
            viewModelScope.launch {
                val number = state.value.number
                if (retriedNumber == number) {
                    state.value = state.value.copy(error = "${Zh.playFailed}（$code）")
                    return@launch
                }
                retriedNumber = number
                val loaded = runCatching { repository.open(number) }.getOrNull()
                val url = loaded?.detail?.audioUrl
                if (loaded != null && !url.isNullOrBlank()) {
                    state.value = state.value.copy(hymn = loaded.detail, offline = loaded.fromCache, error = null)
                    player.play(number, loaded.detail.chineseTitle, url)
                } else {
                    state.value = state.value.copy(error = Zh.playFailed)
                }
            }
        }
        refreshLyricSync()
        viewModelScope.launch {
            while (isActive) {
                val snapshot = state.value
                if (!snapshot.seeking) {
                    val duration = player.player.duration
                    state.value = snapshot.copy(
                        positionMs = player.player.currentPosition.coerceAtLeast(0L),
                        durationMs = if (duration > 0) duration else 0L,
                        isPlaying = player.player.isPlaying,
                    )
                }
                delay(250)
            }
        }
    }

    fun openNumber(raw: Int, autoplay: Boolean) {
        if (!catalog.contains(raw) && raw !in 1..780 && raw !in 1001..1006) {
            state.value = state.value.copy(error = Zh.invalidNumber)
            return
        }
        viewModelScope.launch {
            state.value = state.value.copy(
                number = raw,
                numberInput = raw.toString(),
                loading = true,
                error = null,
                offline = false,
            )
            try {
                val loaded = repository.open(raw)
                state.value = state.value.copy(
                    hymn = loaded.detail,
                    loading = false,
                    offline = loaded.fromCache,
                    error = if (loaded.fromCache) Zh.offline else null,
                )
                if (autoplay) playLoaded(loaded.detail)
            } catch (_: Exception) {
                state.value = state.value.copy(loading = false, hymn = null, error = Zh.network)
            }
        }
    }

    fun submitNumber(autoplay: Boolean) {
        val raw = state.value.numberInput.trim().toIntOrNull()
        if (raw == null) {
            state.value = state.value.copy(error = Zh.invalidNumber)
            return
        }
        openNumber(raw, autoplay)
    }

    fun onNumberInput(value: String) {
        state.value = state.value.copy(numberInput = value.filter { it.isDigit() }.take(4))
    }

    fun previous() = step(-1)

    fun next() = step(1)

    private fun step(delta: Int) {
        val target = catalog.step(state.value.number, delta)
        if (target == state.value.number) return
        openNumber(target, autoplay = state.value.isPlaying || player.player.isPlaying)
    }

    fun togglePlay() {
        val hymn = state.value.hymn
        if (hymn == null || hymn.number != state.value.number) {
            submitNumber(autoplay = true)
            return
        }
        if (player.player.isPlaying) {
            player.toggle()
            return
        }
        playLoaded(hymn)
    }

    fun onSeek(positionMs: Long) {
        state.value = state.value.copy(seeking = true, positionMs = positionMs)
    }

    fun finishSeek() {
        player.seekTo(state.value.positionMs)
        state.value = state.value.copy(seeking = false)
    }

    fun setLyricMode(mode: LyricMode) {
        state.value = state.value.copy(lyricMode = mode)
    }

    fun clearCache() {
        stopLyricDownload()
        repository.clearCache()
        refreshLyricSync()
        state.value = state.value.copy(toast = Zh.cacheCleared)
    }

    fun startLyricDownload() {
        if (downloadJob?.isActive == true || lyricSync.value.complete) return
        downloadJob = viewModelScope.launch {
            repository.downloadMissingLyrics { progress ->
                lyricSync.value = progress
            }
            refreshLyricSync()
        }
    }

    fun stopLyricDownload() {
        downloadJob?.cancel()
        downloadJob = null
        refreshLyricSync()
    }

    private fun refreshLyricSync() {
        if (downloadJob?.isActive == true) return
        lyricSync.value = LyricSyncProgress(
            saved = repository.lyricsSavedCount(),
            total = repository.lyricLibrarySize(),
            current = null,
            running = false,
            note = null,
        )
    }

    fun consumeToast() {
        state.value = state.value.copy(toast = null)
    }

    fun cachedHymns() = repository.cached()

    fun peekTitle(number: Int): String? = repository.peek(number)?.chineseTitle?.takeIf { it.isNotBlank() }

    private fun playLoaded(hymn: HymnDetail) {
        val url = hymn.audioUrl
        if (url.isNullOrBlank()) {
            state.value = state.value.copy(error = Zh.noAudio)
            return
        }
        retriedNumber = -1
        player.play(hymn.number, hymn.chineseTitle, url)
    }
}

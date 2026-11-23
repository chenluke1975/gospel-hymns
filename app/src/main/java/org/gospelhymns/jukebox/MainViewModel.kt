package org.gospelhymns.jukebox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.gospelhymns.jukebox.data.Catalog
import org.gospelhymns.jukebox.data.HymnDetail
import org.gospelhymns.jukebox.data.HymnRepository
import org.gospelhymns.jukebox.data.LyricSyncProgress
import org.gospelhymns.jukebox.data.LyricSyncWorker
import org.gospelhymns.jukebox.player.PlayerHolder
import org.gospelhymns.jukebox.ui.Zh

enum class LyricMode { Chinese, English, Both }

enum class AudioMix { Vocal, Instrumental }

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
    val audioMix: AudioMix = AudioMix.Vocal,
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
    var returnToCategory = androidx.compose.runtime.mutableStateOf(false)
        private set
    var highlightNumber = androidx.compose.runtime.mutableStateOf<Int?>(null)
        private set
    var openCategoryId = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var categoryScrollIndex = androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var categoryScrollOffset = androidx.compose.runtime.mutableIntStateOf(0)
        private set

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
            WorkManager.getInstance(getApplication()).getWorkInfosForUniqueWorkFlow(LyricSyncWorker.NAME).collect { infos ->
                val info = infos.firstOrNull()
                val running = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
                val saved = info?.progress?.getInt("saved", -1)?.takeIf { it >= 0 } ?: repository.lyricsSavedCount()
                val total = info?.progress?.getInt("total", 0)?.takeIf { it > 0 } ?: repository.lyricLibrarySize()
                val current = info?.progress?.getInt("current", -1)?.takeIf { it >= 0 }
                val waiting = info?.state == WorkInfo.State.ENQUEUED
                lyricSync.value = LyricSyncProgress(
                    saved = saved,
                    total = total,
                    current = current,
                    running = running,
                    note = if (waiting && saved < total) "等待網路連線後繼續" else null,
                )
            }
        }
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
                    audioMix = if (loaded.detail.vocal() != null) AudioMix.Vocal else AudioMix.Instrumental,
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
        WorkManager.getInstance(getApplication()).cancelUniqueWork(LyricSyncWorker.NAME)
        repository.clearCache()
        refreshLyricSync()
        LyricSyncWorker.enqueue(getApplication(), replace = true)
        state.value = state.value.copy(toast = Zh.cacheCleared)
    }

    fun retryLyricDownload() {
        LyricSyncWorker.enqueue(getApplication(), replace = true)
    }

    fun openHymnFromCategory(number: Int, scrollIndex: Int, scrollOffset: Int) {
        categoryScrollIndex.intValue = scrollIndex
        categoryScrollOffset.intValue = scrollOffset
        highlightNumber.value = number
        openCategoryId.value = catalog.place(number)?.categoryId
        returnToCategory.value = true
        openNumber(number, autoplay = false)
    }

    fun showCategoryOfCurrentHymn() {
        val number = state.value.number
        val place = catalog.place(number) ?: return
        openCategoryId.value = place.categoryId
        highlightNumber.value = number
        categoryScrollIndex.intValue = catalog.rowIndex(place.categoryId, number)
        categoryScrollOffset.intValue = 0
        returnToCategory.value = false
    }

    fun finishReturnToCategory() {
        returnToCategory.value = false
    }

    private fun refreshLyricSync() {
        lyricSync.value = LyricSyncProgress(
            saved = repository.lyricsSavedCount(),
            total = repository.lyricLibrarySize(),
            current = null,
            running = false,
            note = null,
        )
    }

    fun setAudioMix(mix: AudioMix) {
        val hymn = state.value.hymn ?: return
        val url = when (mix) {
            AudioMix.Vocal -> hymn.vocal()
            AudioMix.Instrumental -> hymn.instrumental()
        } ?: return
        state.value = state.value.copy(audioMix = mix, error = null)
        if (state.value.isPlaying) {
            player.play(hymn.number, hymn.chineseTitle, url)
        }
    }

    fun consumeToast() {
        state.value = state.value.copy(toast = null)
    }

    fun cachedHymns() = repository.cached()

    fun peekTitle(number: Int): String? = repository.peek(number)?.chineseTitle?.takeIf { it.isNotBlank() }

    fun peekFirstLine(number: Int): String? = repository.peek(number)?.firstLyricLine()

    private fun playLoaded(hymn: HymnDetail) {
        val mix = state.value.audioMix
        val url = when (mix) {
            AudioMix.Vocal -> hymn.vocal() ?: hymn.instrumental()
            AudioMix.Instrumental -> hymn.instrumental() ?: hymn.vocal()
        }
        if (url.isNullOrBlank()) {
            state.value = state.value.copy(error = Zh.noAudio)
            return
        }
        retriedNumber = -1
        player.play(hymn.number, hymn.chineseTitle, url)
    }
}

package org.gospelhymns.jukebox.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.gospelhymns.jukebox.data.BROWSER_UA

class PlayerHolder(context: Context) {
    val player: ExoPlayer
    var onError: ((String) -> Unit)? = null

    init {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_UA)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.hymnal.net/"))
            .setAllowCrossProtocolRedirects(true)
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError?.invoke(error.errorCodeName)
            }
        })
    }

    fun play(number: Int, title: String, url: String) {
        val current = player.currentMediaItem
        val sameItem = current?.mediaId == number.toString() &&
            current.localConfiguration?.uri?.toString() == url
        if (!sameItem) {
            val item = MediaItem.Builder()
                .setMediaId(number.toString())
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist("第 $number 首")
                        .build(),
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
        }
        player.play()
    }

    fun toggle() {
        if (player.playbackState == Player.STATE_IDLE) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }
}

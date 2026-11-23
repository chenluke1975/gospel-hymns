package org.gospelhymns.jukebox

import android.app.Application
import org.gospelhymns.jukebox.data.Catalog
import org.gospelhymns.jukebox.data.HymnCache
import org.gospelhymns.jukebox.data.HymnRepository
import org.gospelhymns.jukebox.data.OkHttpHymnalApi
import org.gospelhymns.jukebox.data.defaultHttpClient
import org.gospelhymns.jukebox.player.PlayerHolder
import java.io.File

class GospelApp : Application() {
    lateinit var catalog: Catalog
        private set
    lateinit var repository: HymnRepository
        private set
    lateinit var playerHolder: PlayerHolder
        private set

    override fun onCreate() {
        super.onCreate()
        catalog = Catalog.load(
            assets.open("catalog/categories.json"),
            assets.open("catalog/authors.json"),
            assets.open("catalog/bios.json"),
        )
        repository = HymnRepository(
            api = OkHttpHymnalApi(defaultHttpClient()),
            cache = HymnCache(File(filesDir, "hymn-cache")),
            catalog = catalog,
        )
        playerHolder = PlayerHolder(this)
    }
}

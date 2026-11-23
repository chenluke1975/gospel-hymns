package org.gospelhymns.jukebox

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import org.gospelhymns.jukebox.ui.HymnTheme
import org.gospelhymns.jukebox.ui.JukeboxApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val hymn = intent.getIntExtra("hymn", -1)
        val screen = intent.getStringExtra("screen")
        val author = intent.getStringExtra("author")
        val query = intent.getStringExtra("query").orEmpty()
        val autoplay = intent.getBooleanExtra("autoplay", false)
        setContent {
            HymnTheme {
                val vm: MainViewModel = viewModel()
                LaunchedEffect(hymn, autoplay) {
                    if (hymn > 0) vm.openNumber(hymn, autoplay)
                }
                JukeboxApp(vm, screen, author, query)
            }
        }
    }
}

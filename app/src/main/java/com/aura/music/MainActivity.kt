package com.aura.music

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.random.Random

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val albumId: Long
)

class MainActivity : ComponentActivity() {
    private val library = mutableStateListOf<Song>()
    private var player: MediaPlayer? = null
    private var currentIndex by mutableIntStateOf(-1)
    private var isPlaying by mutableStateOf(false)
    private var positionMs by mutableLongStateOf(0L)
    private var shuffle by mutableStateOf(false)
    private var repeat by mutableStateOf(false)
    private var liked by mutableStateOf(setOf<Long>())
    private var fullGlass by mutableStateOf(true)
    private var scanComplete by mutableStateOf(false)

    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun scanMusic() {
        val found = mutableListOf<Song>()
        val cols = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cols,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val title = cursor.getString(1).orEmpty().ifBlank { "Unknown song" }
                    val artist = cursor.getString(2).orEmpty().ifBlank { "Unknown artist" }
                    found.add(Song(id, title, artist, cursor.getLong(3), cursor.getLong(4)))
                }
            }
        } catch (_: SecurityException) { }
        library.clear()
        library.addAll(found)
        scanComplete = true
    }

    private fun playAt(index: Int) {
        if (index !in library.indices) return
        player?.release()
        player = null
        currentIndex = index
        positionMs = 0
        try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, library[index].id)
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
                setOnCompletionListener {
                    if (repeat) playAt(currentIndex) else skipNext()
                }
                start()
            }
            isPlaying = true
        } catch (_: Exception) { isPlaying = false }
    }

    private fun togglePlayback() {
        try {
            player?.let {
                if (it.isPlaying) it.pause() else it.start()
                isPlaying = it.isPlaying
            }
        } catch (_: IllegalStateException) { }
    }

    private fun skipNext() {
        if (library.isNotEmpty()) {
            playAt(if (shuffle) Random.nextInt(library.size) else (currentIndex + 1).mod(library.size))
        }
    }
    private fun skipPrevious() {
        if (library.isNotEmpty()) {
            playAt(if (currentIndex < 1) library.lastIndex else currentIndex - 1)
        }
    }
    private fun seek(ms: Long) {
        val max = library.getOrNull(currentIndex)?.duration ?: return
        val safe = ms.coerceIn(0L, max)
        player?.seekTo(safe.toInt())
        positionMs = safe
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val prefs = getSharedPreferences("aura_prefs", MODE_PRIVATE)
        liked = prefs.getStringSet("liked", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        fullGlass = prefs.getBoolean("full_glass", true)

        setContent {
            var hasPermission by remember {
                mutableStateOf(checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED)
            }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                hasPermission = granted
            }
            LaunchedEffect(Unit) {
                if (!hasPermission) permissionLauncher.launch(audioPermission())
            }
            LaunchedEffect(hasPermission) {
                if (hasPermission) scanMusic()
            }
            LaunchedEffect(currentIndex) {
                while (true) {
                    positionMs = try { player?.currentPosition?.toLong() ?: 0L }
                    catch (_: Exception) { 0L }
                    delay(300)
                }
            }

            AuraUi(
                songs = library,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                positionMs = positionMs,
                isShuffling = shuffle,
                isRepeating = repeat,
                liked = liked,
                fullGlass = fullGlass,
                permissionGranted = hasPermission,
                scanComplete = scanComplete,
                onRequestPermission = { permissionLauncher.launch(audioPermission()) },
                onRescan = { if (hasPermission) scanMusic() },
                onPlaySong = ::playAt,
                onToggle = ::togglePlayback,
                onNext = ::skipNext,
                onPrevious = ::skipPrevious,
                onSeek = ::seek,
                onShuffle = { shuffle = !shuffle },
                onRepeat = { repeat = !repeat },
                onLike = { song ->
                    liked = if (song.id in liked) liked - song.id else liked + song.id
                    prefs.edit().putStringSet("liked", liked.map { it.toString() }.toSet()).apply()
                },
                onGlass = {
                    fullGlass = !fullGlass
                    prefs.edit().putBoolean("full_glass", fullGlass).apply()
                }
            )
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

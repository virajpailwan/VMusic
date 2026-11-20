package com.aura.music

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlin.random.Random

data class Song(val id: Long, val title: String, val artist: String, val duration: Long, val albumId: Long)

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private val songs = mutableStateListOf<Song>()
    private var current by mutableIntStateOf(-1)
    private var playing by mutableStateOf(false)
    private var position by mutableLongStateOf(0L)
    private var shuffle by mutableStateOf(false)
    private var repeat by mutableStateOf(false)

    private fun scan() {
        songs.clear()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                songs.add(Song(c.getLong(0), c.getString(1) ?: "Unknown",
                    c.getString(2) ?: "Unknown Artist", c.getLong(3), c.getLong(4)))
            }
        }
    }

    private fun start(index: Int) {
        if (index !in songs.indices) return
        player?.release()
        player = null
        current = index
        position = 0
        try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songs[index].id)
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
                setOnCompletionListener { if (repeat) start(current) else next() }
                start()
            }
            playing = true
        } catch (_: Exception) { playing = false }
    }
    private fun toggle() {
        player?.let {
            if (it.isPlaying) it.pause() else it.start()
            playing = it.isPlaying
        }
    }
    private fun next() {
        if (songs.isNotEmpty()) start(if (shuffle) Random.nextInt(songs.size) else (current + 1) % songs.size)
    }
    private fun previous() {
        if (songs.isNotEmpty()) start(if (current <= 0) songs.lastIndex else current - 1)
    }
    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var permission by remember {
                mutableStateOf(checkSelfPermission(if (Build.VERSION.SDK_INT >= 33)
                    Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                permission = it
                if (it) scan()
            }
            LaunchedEffect(permission) {
                if (permission) scan() else launcher.launch(if (Build.VERSION.SDK_INT >= 33)
                    Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            LaunchedEffect(playing, current) {
                while (true) {
                    position = try { player?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }
                    delay(400)
                }
            }
            var screen by remember { mutableStateOf("Home") }
            var query by remember { mutableStateOf("") }
            var fullGlass by remember { mutableStateOf(true) }
            var favorites by remember { mutableStateOf(setOf<Long>()) }
            val selected = songs.getOrNull(current)
            val bg = Brush.verticalGradient(listOf(Color(0xFF4B206F), Color(0xFF261735), Color(0xFF100E18)))
            Box(Modifier.fillMaxSize().background(bg)) {
                if (fullGlass) {
                    Box(Modifier.size(310.dp).offset(x = 110.dp, y = (-60).dp)
                        .blur(90.dp).background(Color(0x995B1BE8), CircleShape))
                    Box(Modifier.size(300.dp).align(Alignment.BottomStart)
                        .blur(100.dp).background(Color(0x886B3CFF), CircleShape))
                }
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    if (screen == "Player" && selected != null) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { screen = "Library" }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                            Spacer(Modifier.weight(1f))
                            Text("NOW PLAYING", color = Color.White)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { fullGlass = !fullGlass }) {
                                Icon(Icons.Default.AutoAwesome, "Toggle glass", tint = Color.White)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AlbumArt(selected.albumId, Modifier.fillMaxWidth().padding(horizontal = 40.dp).aspectRatio(1f).clip(RoundedCornerShape(28.dp)))
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.padding(horizontal = 28.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(selected.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(selected.artist, color = Color(0xFFDDD0EC), fontSize = 16.sp)
                                }
                                IconButton(onClick = {
                                    favorites = if (selected.id in favorites) favorites - selected.id else favorites + selected.id
                                }) {
                                    Icon(if (selected.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        null, tint = Color.White)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Slider(value = position.toFloat().coerceAtMost(selected.duration.toFloat()),
                                onValueChange = { player?.seekTo(it.toInt()); position = it.toLong() },
                                valueRange = 0f..selected.duration.coerceAtLeast(1L).toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                                    inactiveTrackColor = Color(0x88FFFFFF)))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(time(position), color = Color.White)
                                Text(time(selected.duration), color = Color.White)
                            }
                            Spacer(Modifier.height(28.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { shuffle = !shuffle }) {
                                    Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Color(0xFFCB72FF) else Color.White)
                                }
                                IconButton(onClick = { previous() }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                                Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0x66FFFFFF))
                                    .border(1.dp, Color(0x77FFFFFF), CircleShape).clickable { toggle() },
                                    contentAlignment = Alignment.Center) {
                                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                                        tint = Color.White, modifier = Modifier.size(42.dp))
                                }
                                IconButton(onClick = { next() }) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                                IconButton(onClick = { repeat = !repeat }) {
                                    Icon(Icons.Default.Repeat, null, tint = if (repeat) Color(0xFFCB72FF) else Color.White)
                                }
                            }
                            Spacer(Modifier.height(42.dp))
                        }
                    } else {
                        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(24.dp))
                            Text(if (screen == "Home") "Your music, your aura." else if (screen == "Favorites") "Liked songs" else "Your library",
                                color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            if (screen == "Home") {
                                Text("OFFLINE • PERSONAL • PURE", color = Color(0xFFDBC4EF), fontSize = 12.sp)
                                Spacer(Modifier.height(22.dp))
                                Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(26.dp))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFFB223E7), Color(0xFF512B95))))
                                    .clickable { if (songs.isNotEmpty()) start(0); screen = "Player" }
                                    .padding(22.dp)) {
                                    Column {
                                        Text("Feel the beat", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("${songs.size} songs on your phone", color = Color.White)
                                        Spacer(Modifier.height(14.dp))
                                        Text("START LISTENING  ▶", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search your songs", color = Color.LightGray) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                                singleLine = true, colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White, focusedBorderColor = Color(0xFFBF70FF),
                                    unfocusedBorderColor = Color(0x66FFFFFF)))
                            Spacer(Modifier.height(12.dp))
                            val filtered = songs.filter {
                                (screen != "Favorites" || it.id in favorites) &&
                                    (it.title.contains(query, true) || it.artist.contains(query, true))
                            }
                            if (!permission) Text("Allow audio permission to show your local music.", color = Color.White)
                            else if (filtered.isEmpty()) Text("No songs found. Add music files to your phone.", color = Color.White)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filtered, key = { it.id }) { song ->
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(Color(0x332F2440))
                                        .clickable { start(songs.indexOf(song)); screen = "Player" }
                                        .padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AlbumArt(song.albumId, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, color = Color(0xFFBDAFCB), fontSize = 12.sp, maxLines = 1)
                                        }
                                        Text(time(song.duration), color = Color.LightGray, fontSize = 12.sp)
                                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(start = 12.dp))
                                    }
                                }
                            }
                        }
                        if (selected != null) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(20.dp)).background(Color(0x885D4277))
                                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(20.dp))
                                .clickable { screen = "Player" }.padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AlbumArt(selected.albumId, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(selected.title, color = Color.White, maxLines = 1)
                                    Text(selected.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(onClick = { toggle() }) {
                                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 55.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(35.dp)).background(Color(0x664A3B57))
                            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(35.dp)),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(Triple("Home", Icons.Default.Home, "Home"),
                                Triple("Library", Icons.Default.LibraryMusic, "Library"),
                                Triple("Favorites", Icons.Default.Favorite, "Favorites")).forEach { (name, icon, desc) ->
                                IconButton(onClick = { screen = name }) {
                                    Icon(icon, desc, tint = if (screen == name) Color(0xFFCB72FF) else Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun time(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun AlbumArt(albumId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val art = remember(albumId) {
        try {
            val uri = ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"), albumId)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF9C28CE), Color(0xFF3C226D), Color(0xFF141B42)))),
        contentAlignment = Alignment.Center) {
        if (art != null) Image(art, null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
    }
}

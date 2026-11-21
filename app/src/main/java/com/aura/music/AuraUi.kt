package com.aura.music

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin

private val White = Color(0xFFFEFAFF)
private val Subtle = Color(0xFFC8BACF)
private val Purple = Color(0xFFA827F2)
private val PalePurple = Color(0xFFDAB1F4)
private val GlassFill = Color(0x1FFFFFFF)
private val GlassLine = Color(0x50FFFFFF)
private val DarkText = Color(0xFF1C1522)

private enum class Screen { HOME, LIBRARY, PLAYER, SETTINGS }

@Composable
fun AuraUi(
    songs: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    positionMs: Long,
    isShuffling: Boolean,
    isRepeating: Boolean,
    liked: Set<Long>,
    fullGlass: Boolean,
    permissionGranted: Boolean,
    scanComplete: Boolean,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onLike: (Song) -> Unit,
    onGlass: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var returnScreen by remember { mutableStateOf(Screen.HOME) }
    var search by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showAlerts by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val current = songs.getOrNull(currentIndex)
    val context = LocalContext.current
    val openPlayer = {
        if (current != null) {
            returnScreen = screen
            screen = Screen.PLAYER
        }
    }
    val playAt: (Int) -> Unit = { index ->
        onPlaySong(index)
        returnScreen = screen
        screen = Screen.PLAYER
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            if (screen == Screen.PLAYER && current != null) {
                PlayerScreen(
                    song = current,
                    previous = songs.getOrNull(if (currentIndex > 0) currentIndex - 1 else songs.lastIndex),
                    next = songs.getOrNull(if (songs.isEmpty()) -1 else (currentIndex + 1) % songs.size),
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    liked = current.id in liked,
                    fullGlass = fullGlass,
                    shuffle = isShuffling,
                    repeat = isRepeating,
                    onBack = { screen = returnScreen },
                    onPlayPause = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onLike = { onLike(current) },
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                    onQueue = { showQueue = true },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, current.title + " • " + current.artist)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share song"))
                    }
                )
            } else {
                NightBackground(fullGlass)
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Box(Modifier.weight(1f)) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                songs = songs, liked = liked,
                                permissionGranted = permissionGranted, scanComplete = scanComplete,
                                onPermission = onRequestPermission,
                                onSearch = { screen = Screen.LIBRARY; search = true },
                                onAlerts = { showAlerts = true },
                                onPlay = playAt,
                                onLibrary = { screen = Screen.LIBRARY }
                            )
                            Screen.LIBRARY -> LibraryScreen(
                                songs = songs, liked = liked,
                                permissionGranted = permissionGranted, scanComplete = scanComplete,
                                search = search,
                                query = searchText,
                                onQueryChange = { searchText = it },
                                onSearch = { search = !search; if (!search) searchText = "" },
                                onAlerts = { showAlerts = true },
                                onPermission = onRequestPermission,
                                onPlay = playAt
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                fullGlass = fullGlass, onGlass = onGlass, onRescan = onRescan
                            )
                            Screen.PLAYER -> Unit
                        }
                    }
                    if (current != null) {
                        MiniPlayer(current, isPlaying, onOpen = openPlayer, onToggle = onToggle)
                    }
                    FloatingNav(screen, onSelect = { page ->
                        screen = page
                        search = false
                        searchText = ""
                    })
                }
            }
            if (showAlerts) {
                AlertDialog(
                    onDismissRequest = { showAlerts = false },
                    title = { Text("Notifications") },
                    text = { Text("Just you and your music. No online notifications.") },
                    confirmButton = { TextButton("OK") { showAlerts = false } }
                )
            }
            if (showQueue) {
                AlertDialog(
                    onDismissRequest = { showQueue = false },
                    title = { Text("Play queue") },
                    text = {
                        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                            songs.forEachIndexed { index, item ->
                                Text(
                                    item.title, modifier = Modifier.fillMaxWidth().clickable {
                                        onPlaySong(index)
                                        showQueue = false
                                    }.padding(vertical = 10.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton("Close") { showQueue = false } }
                )
            }
        }
    }
}

@Composable
private fun TextButton(label: String, click: () -> Unit) {
    androidx.compose.material.TextButton(onClick = click) { Text(label) }
}

@Composable
private fun NightBackground(fullGlass: Boolean) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF683082), Color(0xFF382244), Color(0xFF241A2F), Color(0xFF17131D)),
                startY = 0f
            )
        )
    ) {
        if (fullGlass) {
            Box(
                Modifier.size(330.dp).align(Alignment.TopStart).offset(x = (-90).dp, y = (-135).dp)
                    .blur(75.dp).background(Color(0x556E209B), CircleShape)
            )
            Box(
                Modifier.size(205.dp).align(Alignment.TopEnd).offset(x = 70.dp, y = (-65).dp)
                    .blur(80.dp).background(Color(0x44432A9C), CircleShape)
            )
        }
    }
}

@Composable
private fun HeaderIcons(onSearch: () -> Unit, onAlerts: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(45.dp).clip(CircleShape).background(
                Brush.linearGradient(listOf(Color(0xFFE782C8), Color(0xFFAB38DF), Color(0xFF543B9F)))
            ).border(1.dp, GlassLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        GlassCircle(Icons.Default.Search, "Search", onSearch)
        Spacer(Modifier.width(10.dp))
        GlassCircle(Icons.Default.NotificationsNone, "Notifications", onAlerts)
    }
}

@Composable
private fun GlassCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 44.dp
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x20FFFFFF))
            .border(1.dp, Color(0x66FFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = White, modifier = Modifier.size((size.value * 0.46f).dp))
    }
}

@Composable
private fun FilterChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier.clip(RoundedCornerShape(30.dp))
                    .background(
                        if (isSelected) Brush.horizontalGradient(listOf(Color(0xFF8E31F1), Color(0xFFC128E4)))
                        else Brush.horizontalGradient(listOf(Color(0x28FFFFFF), Color(0x12FFFFFF)))
                    )
                    .border(1.dp, if (isSelected) Color.Transparent else GlassLine, RoundedCornerShape(30.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(option, color = White, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    songs: List<Song>, liked: Set<Long>,
    permissionGranted: Boolean, scanComplete: Boolean,
    onPermission: () -> Unit,
    onSearch: () -> Unit, onAlerts: () -> Unit,
    onPlay: (Int) -> Unit, onLibrary: () -> Unit
) {
    var category by remember { mutableStateOf("All") }
    val shown = when (category) {
        "Favorites" -> songs.filter { it.id in liked }
        else -> songs
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 10.dp)
    ) {
        item {
            HeaderIcons(onSearch, onAlerts)
            Spacer(Modifier.height(20.dp))
            Text("Hello, Alex", color = White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            FilterChips(listOf("All", "New Artists", "Favorites", "Albums"), category) { category = it }
            Spacer(Modifier.height(20.dp))
            Text("For you", color = White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    FeatureCard(
                        title = "Feel the Beat",
                        subtitle = "Explore your favorites and rediscover the sounds you love.",
                        song = songs.firstOrNull(),
                        colors = listOf(Color(0xFFC32CE7), Color(0xFF532E96), Color(0xFF1C598D)),
                        onClick = { if (songs.isNotEmpty()) onPlay(0) else onLibrary() }
                    )
                }
                item {
                    FeatureCard(
                        title = "Mood Mix",
                        subtitle = "Your own music, your own mood.",
                        song = songs.getOrNull(1),
                        colors = listOf(Color(0xFF3A7CCD), Color(0xFF7036B9), Color(0xFF242647)),
                        onClick = { if (songs.isNotEmpty()) onPlay(if (songs.size > 1) 1 else 0) else onLibrary() }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Popular", color = White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    "Show all  ›", color = Subtle, fontSize = 12.sp,
                    modifier = Modifier.clickable { onLibrary() }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!permissionGranted) {
            item { EmptyMessage("Allow music access to see songs on your phone.", "Allow access", onPermission) }
        } else if (shown.isEmpty()) {
            item { EmptyMessage("Add some music files to your phone and they will appear here.", "Open library", onLibrary) }
        }
        items(shown.take(16), key = { it.id }) { song ->
            SongRow(song = song, onClick = { onPlay(songs.indexOf(song)) }, showTime = false)
        }
    }
}

@Composable
private fun FeatureCard(
    title: String, subtitle: String, song: Song?,
    colors: List<Color>, onClick: () -> Unit
) {
    Box(
        Modifier.width(285.dp).height(166.dp).clip(RoundedCornerShape(23.dp))
            .background(Brush.linearGradient(colors)).clickable(onClick = onClick)
    ) {
        if (song != null) {
            AlbumImage(
                song,
                Modifier.size(187.dp).align(Alignment.CenterEnd).offset(x = 23.dp).alpha(0.82f)
                    .clip(RoundedCornerShape(18.dp))
            )
        } else {
            Box(Modifier.size(145.dp).align(Alignment.CenterEnd).offset(x = 35.dp)
                .blur(27.dp).background(Color(0x9921C0ED), CircleShape))
            Icon(Icons.Default.Headphones, null, tint = Color(0x99FFFFFF),
                modifier = Modifier.size(110.dp).align(Alignment.CenterEnd).offset(x = 14.dp))
        }
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(listOf(Color(0xBB8B16B4), Color(0x99682CC8), Color.Transparent))
        ))
        Column(
            Modifier.fillMaxHeight().width(199.dp).padding(17.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = White.copy(alpha = 0.90f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3)
            }
            Box(
                Modifier.clip(RoundedCornerShape(25.dp))
                    .background(Color(0x34FFFFFF))
                    .border(1.dp, Color(0x7CFFFFFF), RoundedCornerShape(25.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("Start Listening", color = White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    songs: List<Song>, liked: Set<Long>,
    permissionGranted: Boolean, scanComplete: Boolean,
    search: Boolean, query: String, onQueryChange: (String) -> Unit,
    onSearch: () -> Unit, onAlerts: () -> Unit,
    onPermission: () -> Unit, onPlay: (Int) -> Unit
) {
    var filter by remember { mutableStateOf("All") }
    val filtered = remember(songs.toList(), liked, filter, query) {
        songs.filter { song ->
            (filter != "Liked Songs" || song.id in liked) &&
                (song.title.contains(query, ignoreCase = true) || song.artist.contains(query, ignoreCase = true))
        }
    }
    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 22.dp)) {
        HeaderIcons(onSearch, onAlerts)
        Spacer(Modifier.height(20.dp))
        Text("Your library", color = White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(13.dp))
        FilterChips(listOf("All", "Liked Songs", "Playlists", "Downloads"), filter) { filter = it }
        if (search) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = White) },
                placeholder = { Text("Search songs or artists", color = Subtle) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = White, focusedBorderColor = PalePurple, unfocusedBorderColor = GlassLine
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!permissionGranted) {
            EmptyMessage("Allow access to display local songs.", "Allow access", onPermission)
        } else if (filter == "Playlists") {
            EmptyMessage("Your playlists will appear here. For now, tap the heart on a song to save it.", "Liked songs") { filter = "Liked Songs" }
        } else if (filtered.isEmpty()) {
            EmptyMessage(
                if (filter == "Liked Songs") "No liked songs yet. Tap a heart in Now Playing."
                else "No music found. Add songs to your phone.",
                "Show all"
            ) { filter = "All" }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(filtered, key = { it.id }) { song ->
                    SongRow(song = song, showTime = true, onClick = { onPlay(songs.indexOf(song)) })
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, showTime: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(69.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumImage(song, Modifier.size(53.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(song.artist, color = Subtle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showTime) {
            Text(timeText(song.duration), color = Subtle, fontSize = 11.sp)
            Spacer(Modifier.width(12.dp))
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0x2DFFFFFF))
                .border(1.dp, Color(0x55FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, "Play " + song.title, tint = White, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun EmptyMessage(message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LibraryMusic, null, tint = PalePurple, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = Subtle, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material.TextButton(onClick = onAction) { Text(action, color = PalePurple) }
    }
}

@Composable
private fun FloatingNav(screen: Screen, onSelect: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 13.dp, top = 5.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.height(61.dp).clip(RoundedCornerShape(35.dp))
                .background(Color(0x772B2536))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(35.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Screen.HOME to Icons.Default.Home,
                Screen.LIBRARY to Icons.Default.MusicNote,
                Screen.SETTINGS to Icons.Default.Settings
            ).forEach { (page, icon) ->
                val active = screen == page
                Box(
                    Modifier.size(55.dp).clip(CircleShape)
                        .background(
                            if (active) Brush.linearGradient(listOf(Color(0xFF8F37F3), Color(0xFFBF29E5)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onSelect(page) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, page.name, tint = White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(song: Song, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color(0x9A5F4274))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(18.dp))
            .clickable { onOpen() }.padding(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumImage(song, Modifier.size(43.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = White, fontSize = 12.sp, maxLines = 1)
            Text(song.artist, color = Subtle, fontSize = 10.sp, maxLines = 1)
        }
        IconButton(onClick = onToggle) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Toggle playback", tint = White)
        }
    }
}

@Composable
private fun PlayerScreen(
    song: Song, previous: Song?, next: Song?,
    isPlaying: Boolean, positionMs: Long, liked: Boolean, fullGlass: Boolean,
    shuffle: Boolean, repeat: Boolean,
    onBack: () -> Unit, onPlayPause: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit,
    onLike: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit,
    onQueue: () -> Unit, onShare: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFFB98BCB), Color(0xFFA969AE),
                    Color(0xFFAC7EBD), Color(0xFF7A95D2), Color(0xFF6794D2)
                )
            )
        )
    ) {
        if (fullGlass) {
            AlbumImage(
                song, Modifier.fillMaxSize().blur(90.dp).alpha(0.18f)
            )
            Box(Modifier.size(310.dp).align(Alignment.TopStart).offset(x = (-130).dp, y = (-40).dp)
                .blur(92.dp).background(Color(0x99D384D5), CircleShape))
            Box(Modifier.size(380.dp).align(Alignment.BottomEnd).offset(x = 130.dp, y = 50.dp)
                .blur(100.dp).background(Color(0x775E8FDB), CircleShape))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(Icons.Default.ChevronLeft, "Back", onBack, 45.dp)
                Spacer(Modifier.weight(1f))
                Text("Now Playing", color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                GlassCircle(Icons.Default.OpenInNew, "Share", onShare, 45.dp)
            }
            Spacer(Modifier.height(26.dp))
            CoverCarousel(song, previous, next)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(song.artist, color = Color(0xBFEDE8F6), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(song.title, color = White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassCircle(
                    if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Like song", onLike, 44.dp
                )
            }
            Spacer(Modifier.height(32.dp))
            WaveformProgress(
                progress = if (song.duration > 0) (positionMs.toFloat() / song.duration).coerceIn(0f, 1f) else 0f,
                durationMs = song.duration,
                onSeek = onSeek
            )
            Row(Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeText(positionMs), color = Color(0xDFFFFFFF), fontSize = 12.sp)
                Text(timeText(song.duration), color = Color(0xDFFFFFFF), fontSize = 12.sp)
            }
            Spacer(Modifier.weight(0.75f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRepeat) {
                    Icon(Icons.Default.Repeat, "Repeat", tint = if (repeat) Color(0xFF4B267C) else White)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.FastRewind, "Previous", tint = White, modifier = Modifier.size(30.dp))
                }
                Box(
                    Modifier.size(70.dp).clip(CircleShape)
                        .background(Color(0x2BFFFFFF))
                        .border(1.dp, Color(0x76FFFFFF), CircleShape)
                        .clickable { onPlayPause() }, contentAlignment = Alignment.Center
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play or pause", tint = White, modifier = Modifier.size(31.dp))
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.FastForward, "Next", tint = White, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = onQueue) {
                    Icon(Icons.Default.PlaylistPlay, "Queue", tint = White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(35.dp))
        }
    }
}

@Composable
private fun CoverCarousel(song: Song, previous: Song?, next: Song?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cover = (maxWidth - 78.dp).coerceAtMost(315.dp)
        Box(Modifier.fillMaxWidth().height(cover)) {
            if (previous != null) {
                AlbumImage(previous, Modifier.size(cover)
                    .align(Alignment.CenterStart).offset(x = -cover * 0.88f)
                    .clip(RoundedCornerShape(27.dp)).alpha(0.8f))
            }
            if (next != null) {
                AlbumImage(next, Modifier.size(cover)
                    .align(Alignment.CenterEnd).offset(x = cover * 0.88f)
                    .clip(RoundedCornerShape(27.dp)).alpha(0.8f))
            }
            AlbumImage(
                song,
                Modifier.size(cover).align(Alignment.Center)
                    .clip(RoundedCornerShape(27.dp))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(27.dp))
            )
        }
    }
}

@Composable
private fun WaveformProgress(progress: Float, durationMs: Long, onSeek: (Long) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth().height(43.dp)
            .pointerInput(durationMs) {
                detectTapGestures { location ->
                    if (durationMs > 0) {
                        val percent = (location.x / size.width).coerceIn(0f, 1f)
                        onSeek((durationMs * percent).toLong())
                    }
                }
            }
    ) {
        val count = 70
        val step = size.width / count
        val middle = size.height * 0.5f
        for (i in 0 until count) {
            val motion = (abs(sin(i * 0.56)) * 0.57 + abs(sin(i * 0.14 + 1.3)) * 0.27).toFloat()
            val height = size.height * (0.18f + motion * 0.78f)
            val x = step * (i + 0.5f)
            drawLine(
                color = White.copy(alpha = if (i.toFloat() / count <= progress) 0.98f else 0.65f),
                start = Offset(x, middle - height * 0.5f),
                end = Offset(x, middle + height * 0.5f),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        val x = (size.width * progress).coerceIn(0f, size.width)
        drawLine(White, Offset(x, 0f), Offset(x, size.height), 1.8.dp.toPx())
        drawCircle(White, radius = 3.2.dp.toPx(), center = Offset(x, middle))
    }
}

@Composable
private fun SettingsScreen(fullGlass: Boolean, onGlass: () -> Unit, onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp)) {
        Text("Settings", color = White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Your personal listening experience", color = Subtle, fontSize = 13.sp)
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(GlassFill).border(1.dp, GlassLine, RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Full liquid glass", color = White, fontSize = 16.sp)
                Spacer(Modifier.height(5.dp))
                Text("Heavy live blur and ambient glow", color = Subtle, fontSize = 12.sp)
            }
            Switch(checked = fullGlass, onCheckedChange = { onGlass() },
                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = Purple))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(GlassFill).border(1.dp, GlassLine, RoundedCornerShape(22.dp))
                .clickable { onRescan() }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Refresh, null, tint = White)
            Spacer(Modifier.width(12.dp))
            Text("Rescan local music", color = White, fontSize = 15.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("AURA • Offline Music Player", color = Subtle, fontSize = 12.sp)
    }
}

private val imageCache = object : LruCache<Long, ImageBitmap>(14) {}

@Composable
private fun AlbumImage(song: Song, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val artwork by produceState<ImageBitmap?>(initialValue = imageCache.get(song.id), key1 = song.id) {
        value = withContext(Dispatchers.IO) { loadArtwork(context, song) }
    }
    Box(modifier.background(
        Brush.linearGradient(
            when ((song.albumId % 4).toInt()) {
                0 -> listOf(Color(0xFFBE2ECF), Color(0xFF6F38B5), Color(0xFF282046))
                1 -> listOf(Color(0xFF982CE4), Color(0xFF3D3EAB), Color(0xFF141A3C))
                2 -> listOf(Color(0xFFCE39A0), Color(0xFF5333B0), Color(0xFF20194B))
                else -> listOf(Color(0xFF5B8EDF), Color(0xFF8933C4), Color(0xFF30215F))
            }
        )
    ), contentAlignment = Alignment.Center) {
        if (artwork != null) {
            Image(artwork!!, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(brush = Brush.radialGradient(
                    listOf(Color(0xAAE64AE7), Color.Transparent),
                    center = Offset(size.width * 0.74f, size.height * 0.22f),
                    radius = size.width * 0.65f
                ), radius = size.width * 0.65f, center = Offset(size.width * 0.74f, size.height * 0.22f))
                drawCircle(
                    Color(0x44FFFFFF),
                    radius = size.width * 0.28f,
                    center = Offset(size.width * 0.5f, size.height * 0.55f),
                    style = Stroke(width = (size.width * 0.025f).coerceAtLeast(1f))
                )
            }
            Icon(Icons.Default.MusicNote, null, tint = White.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxSize(0.32f))
        }
    }
}

private fun loadArtwork(context: Context, song: Song): ImageBitmap? {
    imageCache.get(song.id)?.let { return it }
    val bitmap: Bitmap? = try {
        val artUri = Uri.parse("content://media/external/audio/albumart/" + song.albumId)
        context.contentResolver.openInputStream(artUri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
        }
    } catch (_: Exception) { null }
    val result = bitmap ?: try {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size,
                    BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        } finally {
            retriever.release()
        }
    } catch (_: Exception) { null }
    return result?.asImageBitmap()?.also { imageCache.put(song.id, it) }
}

private fun timeText(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000)
    return (seconds / 60).toString() + ":" + (seconds % 60).toString().padStart(2, '0')
}

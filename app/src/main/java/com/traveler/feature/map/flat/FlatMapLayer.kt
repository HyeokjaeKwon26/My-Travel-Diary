package com.traveler.feature.map.flat

import android.graphics.Canvas
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.traveler.feature.map.renderer.*
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/** The same projected footprint is used for streets, routes and video frames. */
class FlatMapTiles(private val session: StreetMapSession) {
    fun draw(canvas: Canvas, viewport: TravelViewportCalculator) {
        val sx = viewport.spanX / viewport.contentW
        val sy = viewport.spanY / viewport.contentH
        val left = viewport.viewportMinX - viewport.insets.left * sx
        val top = viewport.viewportMinY - viewport.insets.top * sy
        val footprint = MapFootprint(left, top, left + viewport.width * sx, top + viewport.height * sy)
        val plan = StreetTilePlan.visible(footprint, viewport.width, viewport.height)
        session.request(plan)
        session.paint(canvas, footprint, viewport.width, viewport.height, plan)
    }
}

@Composable
fun FlatMapLayer(renderer: TravelMapRenderer, model: TravelMapRenderModel,
                 state: TravelPlaybackState?, playing: Boolean, showOptions: Boolean,
                 dismissOptions: () -> Unit, controlsInset: Dp) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("flat_map_settings", 0) }
    // Existing offline users explicitly opt in to network map requests.
    var online by remember { mutableStateOf(preferences.getBoolean("street_detail", false)) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val session = remember { StreetMapSession(context, network = true, changed = { scope.launch { revision++ } }) }
    val tiles = remember(session) { FlatMapTiles(session) }
    DisposableEffect(session) { onDispose { renderer.streetLayer = null; session.close() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Pausing the journey still lets the currently visible map finish loading.
    SideEffect { session.setActive(online && foreground) }
    LaunchedEffect(renderer) {
        RegionalBasemapCache.ensureLoaded(context.applicationContext)?.let {
            renderer.setPreparedRegionalBasemap(it)
            revision++
        }
    }
    Box(Modifier.fillMaxSize()) {
        ComposeCanvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val observed = revision
            renderer.streetLayer = tiles::draw
            renderer.render(drawContext.canvas.nativeCanvas, size.width.toInt(), size.height.toInt(), model, state,
                SafeContentInsets())
        }
        Text("N ↑ · © OpenStreetMap contributors · Natural Earth",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = controlsInset)
                .background(Color(0xDC102638)).padding(horizontal = 6.dp, vertical = 4.dp),
            color = Color.White, fontSize = 9.sp, lineHeight = 11.sp)
    }
    if (showOptions) AlertDialog(onDismissRequest = dismissOptions,
        title = { Text("2D map settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("North stays up. The map follows your journey at an automatic scale. No terrain downloads are needed.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Internet street detail", Modifier.weight(1f))
                Switch(checked = online, onCheckedChange = {
                    online = it; preferences.edit().putBoolean("street_detail", it).apply()
                })
            }
            Text("Optional roads and place names use Wi-Fi or mobile data for the visible map. The provider receives your IP address and requested map area. Previously viewed detail is cached (up to 96 MB).")
            Text("Missing detail always uses the built-in map. Videos use cached detail without downloading maps during export. Photos and Timeline files stay on this device.")
        } }, confirmButton = { TextButton(onClick = dismissOptions) { Text("Done") } })
}

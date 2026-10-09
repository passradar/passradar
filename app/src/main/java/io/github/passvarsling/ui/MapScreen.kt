package io.github.passvarsling.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.passvarsling.R
import io.github.passvarsling.ui.theme.LocalDarkTheme
import io.github.passvarsling.data.Availability
import io.github.passvarsling.data.Office
import io.github.passvarsling.domain.Dates
import io.github.passvarsling.domain.WaitLevel
import io.github.passvarsling.domain.WaitTime
import io.github.passvarsling.domain.WaitUnit
import io.github.passvarsling.net.Http
import io.github.passvarsling.ui.glass.GlassPanel
import io.github.passvarsling.ui.glass.PillShape
import io.github.passvarsling.ui.glass.glass
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDate

/** Kartverket's grey topographic map (open data, CC BY 4.0). */
private object KartverketTiles : OnlineTileSourceBase(
    "KartverketTopoGraatone", 0, 18, 256, ".png",
    arrayOf("https://cache.kartverket.no/v1/wmts/1.0.0/topograatone/default/webmercator/"),
    "© Kartverket",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + ".png"
}

private val MainlandNorway = BoundingBox(71.2, 31.2, 57.9, 4.6)

/**
 * Dark mode: a softened negative of the grey map, so land turns dark grey and roads and
 * names light. Water is transparent in these tiles and shows the dark background.
 */
private val DarkTiles = ColorMatrixColorFilter(
    floatArrayOf(
        -0.85f, 0f, 0f, 0f, 222f,
        0f, -0.85f, 0f, 0f, 227f,
        0f, 0f, -0.85f, 0f, 230f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Land colour of the grey map, used for tiles that are still loading. */
private const val TILE_LAND = 0xFFD4D4D4.toInt()

/** From this zoom level the markers show the waiting time instead of a plain dot. */
private const val LABEL_ZOOM = 7.0

private object MapSetup {
    @Volatile
    private var done = false

    fun ensure(context: Context) {
        if (done) return
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = Http.userAgent
        // Keep the tile cache inside the app so no storage permission is needed.
        val base = File(context.cacheDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
        done = true
    }
}

@Composable
fun MapScreen(vm: AppViewModel, bottomSpace: Dp) {
    val context = LocalContext.current
    val offices by vm.offices.collectAsStateWithLifecycle()
    val availability by vm.availability.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val today = Dates.today()
    val requestNotifications = rememberNotificationPermission()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val watched = remember(watches) { watches.mapTo(HashSet()) { it.officeId } }
    val backdrop = rememberLayerBackdrop()
    val brand = ContextCompat.getColor(context, R.color.brand)
    val border = with(LocalDensity.current) { 64.dp.roundToPx() }

    val markers = remember { MarkerLayer(context, brand) { selectedId = it } }
    val mapView = remember {
        MapSetup.ensure(context)
        MapView(context).apply {
            setTileSource(KartverketTiles)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            overlayManager.tilesOverlay.loadingBackgroundColor = TILE_LAND
            overlayManager.tilesOverlay.loadingLineColor = TILE_LAND
            minZoomLevel = 4.0
            maxZoomLevel = 17.0
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    selectedId = null
                    return true
                }

                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    event?.let { markers.onZoom(this@apply, it.zoomLevel) }
                    return false
                }
            })
            val camera = vm.mapCamera
            val start = vm.home.value
            if (camera != null) {
                controller.setZoom(camera.zoom)
                controller.setCenter(GeoPoint(camera.lat, camera.lon))
            } else if (start != null) {
                // Near me: open on the area around the user, where the waiting times show.
                controller.setZoom(LABEL_ZOOM + 1)
                controller.setCenter(GeoPoint(start.lat, start.lon))
            } else {
                controller.setZoom(4.5)
                controller.setCenter(GeoPoint(64.5, 15.0))
                addOnFirstLayoutListener { _, _, _, _, _ -> zoomToBoundingBox(MainlandNorway, false, border) }
            }
        }
    }

    val dark = LocalDarkTheme.current
    LaunchedEffect(dark) {
        mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) DarkTiles else null)
        mapView.invalidate()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            val center = mapView.mapCenter
            vm.mapCamera = MapCamera(center.latitude, center.longitude, mapView.zoomLevelDouble)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { map -> markers.show(map, offices, availability, watched, today, home) },
            )
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .glass(backdrop, PillShape, lensHeight = 10.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            WaitLegend()
        }

        // Kartverket's open data licence (CC BY 4.0) asks for this credit.
        Text(
            stringResource(R.string.about_map),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = bottomSpace - 6.dp),
        )

        val selected = selectedId?.let { id -> offices.firstOrNull { it.id == id } }
        if (selected != null) {
            val isWatched = selected.id in watched
            GlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomSpace + 4.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WaitBadge(availability[selected.id], today)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Hint(earliestLine(availability[selected.id]))
                    }
                    FollowButton(isWatched, onToggle = {
                        if (!isWatched) requestNotifications()
                        vm.toggleWatch(selected)
                    })
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.openOffice(selected.id) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = PillShape) {
                    Text(stringResource(R.string.map_open_office))
                }
            }
        }
    }
}

@Composable
private fun WaitLegend() {
    val items = listOf(
        WaitLevel.Short to R.string.wait_short,
        WaitLevel.Medium to R.string.wait_medium,
        WaitLevel.Long to R.string.wait_long,
        WaitLevel.VeryLong to R.string.wait_very_long,
        WaitLevel.NoneFree to R.string.wait_none,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((level, label) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WaitDot(level, size = 10.dp)
                Spacer(Modifier.width(5.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Office markers: coloured dots far out, waiting-time labels when zoomed in. */
private class MarkerLayer(
    private val context: Context,
    private val brand: Int,
    private val onSelect: (String) -> Unit,
) {
    private var offices: List<Office> = emptyList()
    private var availability: Map<String, Availability> = emptyMap()
    private var watched: Set<String> = emptySet()
    private var today: LocalDate = Dates.today()
    private var home: Position? = null
    private var labels: Boolean? = null
    private val icons = HashMap<String, Drawable>()

    fun show(map: MapView, offices: List<Office>, availability: Map<String, Availability>, watched: Set<String>, today: LocalDate, home: Position?) {
        this.offices = offices
        this.availability = availability
        this.watched = watched
        this.today = today
        this.home = home
        labels = map.zoomLevelDouble >= LABEL_ZOOM
        rebuild(map)
    }

    fun onZoom(map: MapView, zoom: Double) {
        val wanted = zoom >= LABEL_ZOOM
        if (wanted != labels) {
            labels = wanted
            rebuild(map)
        }
    }

    private fun rebuild(map: MapView) {
        map.overlays.removeAll { it is Marker }
        val withLabels = labels == true
        // Shortest waits and followed offices are drawn last, so they end up on top.
        val ranked = offices
            .filter { it.lat != null && it.lon != null }
            .map { it to waitLevel(availability[it.id], today) }
            .sortedWith(compareBy<Pair<Office, WaitLevel>>({ it.first.id in watched }, { -it.second.ordinal }))
        // Drawn first, so the offices' labels stay readable on top of it.
        home?.let { position ->
            map.overlays.add(Marker(map).apply {
                this.position = GeoPoint(position.lat, position.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = icons.getOrPut("home") { homeIcon() }
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        for ((office, level) in ranked) {
            val isWatched = office.id in watched
            val text = if (withLabels) badgeText(availability[office.id]) else null
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(office.lat!!, office.lon!!)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = icons.getOrPut("$level|$isWatched|$text") { markerIcon(level, isWatched, text) }
                title = office.name
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ ->
                    onSelect(office.id)
                    true
                }
            })
        }
        map.invalidate()
    }

    private fun badgeText(availability: Availability?): String {
        val res = context.resources
        val earliest = availability?.earliestDate ?: return if (availability == null) "?" else "–"
        val compact = WaitTime.compact(WaitTime.days(today, earliest))
        val n = compact.value.toInt()
        return when (compact.unit) {
            WaitUnit.Days -> res.getString(R.string.wait_badge_days, n)
            WaitUnit.Weeks -> res.getString(R.string.wait_badge_weeks, n)
            WaitUnit.Months -> res.getString(R.string.wait_badge_months, n)
        }
    }

    /** The user's own (approximate) position: a blue dot with a white ring. */
    private fun homeIcon(): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (18 * density).toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF1A73E8.toInt())
            setStroke((3 * density).toInt(), android.graphics.Color.WHITE)
            setSize(size, size)
        }
    }

    private fun markerIcon(level: WaitLevel, watched: Boolean, text: String?): Drawable {
        val density = context.resources.displayMetrics.density
        val fill = level.color()?.toArgb() ?: android.graphics.Color.WHITE
        val stroke = when {
            watched -> brand
            level == WaitLevel.Unknown -> android.graphics.Color.GRAY
            else -> android.graphics.Color.WHITE
        }
        val strokeWidth = (if (watched) 3 else 2) * density
        if (text == null) {
            val size = ((if (watched) 22 else 16) * density).toInt()
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                setStroke(strokeWidth.toInt(), stroke)
                setSize(size, size)
            }
        }
        val textColor = when (level) {
            WaitLevel.Medium, WaitLevel.Long, WaitLevel.Unknown -> 0xFF1C1B1F.toInt()
            else -> android.graphics.Color.WHITE
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 12 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val height = 22 * density
        val width = maxOf(height * 1.4f, paint.measureText(text) + 16 * density)
        val bitmap = createBitmap((width + strokeWidth * 2).toInt(), (height + strokeWidth * 2).toInt())
        val canvas = Canvas(bitmap)
        val rect = RectF(strokeWidth, strokeWidth, strokeWidth + width, strokeWidth + height)
        val radius = height / 2
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        })
        val baseline = rect.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, rect.centerX(), baseline, paint)
        return bitmap.toDrawable(context.resources)
    }
}

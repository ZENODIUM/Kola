package com.vizeno.kola

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vizeno.kola.ui.theme.KolaTheme
import com.google.mlkit.vision.pose.PoseLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.Executors
import kotlin.math.abs

private val Ink = Color(0xFF112E29)
private val Mint = Color(0xFF42D3A2)
private val Cream = Color(0xFFF4F1E8)
private val Paper = Color(0xFFFFFEFA)
private val Coral = Color(0xFFFF705C)
private val Blue = Color(0xFF5C8FF0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KolaTheme { KolaApp(intent?.data) } }
    }
}

@Composable
private fun KolaApp(initialDemoUri: Uri? = null, vm: MonitorViewModel = viewModel()) {
    NotificationPermissionEffect()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = Cream, bottomBar = {
        NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
            listOf("Monitor", "Signals", "History", "Settings").forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { NavGlyph(index, tab == index) },
                    label = { Text(label, fontSize = 10.sp, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Mint.copy(.18f), selectedIconColor = Ink, selectedTextColor = Ink))
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> MonitorScreen(vm, initialDemoUri)
                1 -> TrendsScreen(vm)
                2 -> HistoryScreen(vm)
                else -> ConnectScreen(vm)
            }
        }
    }
}

@Composable
private fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
private fun NavGlyph(index: Int, selected: Boolean) {
    val color = if (selected) Ink else Ink.copy(.45f)
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        when (index) {
            0 -> { drawCircle(color, s * .36f, center, style = Stroke(s * .09f)); drawCircle(color, s * .09f, center) }
            1 -> { drawLine(color, Offset(s*.1f,s*.7f), Offset(s*.37f,s*.42f), s*.09f, StrokeCap.Round); drawLine(color, Offset(s*.37f,s*.42f), Offset(s*.62f,s*.58f), s*.09f, StrokeCap.Round); drawLine(color, Offset(s*.62f,s*.58f), Offset(s*.9f,s*.2f), s*.09f, StrokeCap.Round) }
            2 -> repeat(3) { i -> drawRoundRect(color, Offset(s*.12f, s*(.18f+i*.27f)), Size(s*.76f,s*.11f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s*.06f)) }
            else -> { drawCircle(color, s*.14f, Offset(s*.24f,s*.5f), style=Stroke(s*.08f)); drawCircle(color,s*.14f,Offset(s*.76f,s*.28f),style=Stroke(s*.08f)); drawCircle(color,s*.14f,Offset(s*.76f,s*.72f),style=Stroke(s*.08f)); drawLine(color,Offset(s*.38f,s*.47f),Offset(s*.62f,s*.32f),s*.07f); drawLine(color,Offset(s*.38f,s*.53f),Offset(s*.62f,s*.68f),s*.07f) }
        }
    }
}

@Composable
private fun MonitorScreen(vm: MonitorViewModel, initialDemoUri: Uri? = null) {
    val context = LocalContext.current
    val vision by vm.vision.collectAsStateWithLifecycle()
    val zone by vm.zone.collectAsStateWithLifecycle()
    val zoneClosed by vm.zoneClosed.collectAsStateWithLifecycle()
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var overlayOptions by remember { mutableStateOf(OverlayOptions()) }
    var layersOpen by remember { mutableStateOf(false) }
    var demoUri by remember { mutableStateOf(initialDemoUri) }
    var videoLibrary by remember { mutableStateOf(loadVideoLibrary(context)) }
    var videosOpen by remember { mutableStateOf(false) }
    fun rememberVideos(uris: List<Uri>) {
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        val merged = (videoLibrary + uris.map { VideoChoice(it, videoName(context, it)) }).distinctBy { it.uri.toString() }
        videoLibrary = merged
        context.getSharedPreferences("kola_settings", 0).edit().also { edit ->
            edit.putStringSet("video_library", merged.map { it.uri.toString() }.toSet())
            merged.forEach { edit.putString("video_name_${it.uri.toString().hashCode()}", it.name) }
        }.apply()
    }
    LaunchedEffect(initialDemoUri) { initialDemoUri?.let { rememberVideos(listOf(it)) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { rememberVideos(uris); demoUri = uris.first(); videosOpen = false }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 17.dp, 20.dp, 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("KOLA", color = Ink, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 24.sp); Text("local vision monitor", color = Ink.copy(.52f), fontSize = 11.sp, letterSpacing = .4.sp) }
            StatusPill(if (sync.startsWith("Synced")) "SYNCED" else "PRIVATE / LOCAL", Mint, true)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).background(Color.Black, RoundedCornerShape(26.dp)).border(1.dp, Color.White.copy(.14f), RoundedCornerShape(26.dp))) {
            val viewportAspect = maxWidth.value / maxHeight.value
            if (demoUri == null) PermissionCamera(vm) else key(demoUri) { DemoVideo(vm, demoUri!!, viewportAspect) }
            DetectionOverlay(vision, zone, zoneClosed, editing, overlayOptions, vm::addZonePoint)
            Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatusPill(if (demoUri == null) "LIVE CAMERA" else "TEST VIDEO", Coral)
                StatusPill("${vision.fps} FPS · UPDATES/S", Color.White.copy(.9f), true)
            }
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { layersOpen = true }, colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(.52f), contentColor = Color.White)) { Text("Layers", fontSize = 11.sp) }
                    TextButton(onClick = { videosOpen = true },
                        colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(.52f), contentColor = Color.White)) { Text("Videos", fontSize = 11.sp) }
                }
                DropdownMenu(expanded = layersOpen, onDismissRequest = { layersOpen = false }, modifier = Modifier.width(248.dp).background(Paper)) {
                    Text("CAMERA ANNOTATIONS", Modifier.padding(14.dp, 8.dp), color = Ink.copy(.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    LayerToggle("Safe-zone shape", overlayOptions.safeZone) { overlayOptions = overlayOptions.copy(safeZone = it) }
                    LayerToggle("Area movement heatmap", overlayOptions.areaHeatmap) { overlayOptions = overlayOptions.copy(areaHeatmap = it) }
                    LayerToggle("Movement region", overlayOptions.movementRegion) { overlayOptions = overlayOptions.copy(movementRegion = it) }
                    LayerToggle("Direction marker", overlayOptions.movementDirection) { overlayOptions = overlayOptions.copy(movementDirection = it) }
                    LayerToggle("Boundary radar", overlayOptions.boundaryRadar) { overlayOptions = overlayOptions.copy(boundaryRadar = it) }
                    LayerToggle("Body guide (13 points)", overlayOptions.poseGuides) { overlayOptions = overlayOptions.copy(poseGuides = it) }
                    LayerToggle("Object labels", overlayOptions.objects) { overlayOptions = overlayOptions.copy(objects = it) }
                }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.84f)))).padding(16.dp, 34.dp, 16.dp, 14.dp)) {
                Text(vision.status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${vision.direction}  ·  ${(vision.motionArea*100).toInt()}% of view moving  ·  ${zoneLabel(vision.insideZone)}",
                    color = if (vision.insideZone == false) Coral else Color.White.copy(.7f), fontSize = 11.sp)
                Text(buildString {
                    append("Speed ${Math.round(vision.movementSpeed*100)}")
                    vision.boundaryDistance?.let { append("  ·  edge ${(it*100).toInt()}% away") }
                    vision.boundaryEtaSeconds?.let { append("  ·  predicted exit ${"%.1f".format(it)}s") }
                    if(vision.detectedItems.isNotEmpty()) append("  ·  ${vision.detectedItems.size} object${if(vision.detectedItems.size==1)"" else "s"}")
                },color=Color.White.copy(.55f),fontSize=9.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp, 12.dp, 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SignalTile("MOTION ENERGY", "HOW MUCH MOVED", "${(vision.motion*100).toInt()}%", Coral, Modifier.weight(1f))
            SignalTile("ACTIVE AREA", "HOW MUCH OF VIEW MOVED", "${(vision.motionArea*100).toInt()}%", Mint, Modifier.weight(1f))
            SignalTile("STABILITY", "HOW STEADY", "${(vision.stability*100).toInt()}%", Blue, Modifier.weight(1f))
            SignalTile("MOVE SPEED", "HOW FAST IT MOVES", "${(vision.movementSpeed*100).toInt()}", Color(0xFFFFB94A), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(onClick = {
                if (editing && zone.size >= 3) { vm.finishZone(); editing = false }
                else if (!editing) { vm.beginZoneEditing(); editing = true }
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = if (editing) Coral else Ink, contentColor = Color.White)) {
                Text(if (editing) "Finish custom shape (${zone.size} points)" else if (zoneClosed) "Edit safe zone" else "Draw safe zone", fontSize = 12.sp)
            }
            OutlinedButton(vm::undoZonePoint, enabled = zone.isNotEmpty(), shape = RoundedCornerShape(13.dp)) { Text("Undo", fontSize = 11.sp) }
            OutlinedButton({ vm.clearZone(); editing = false }, enabled = zone.isNotEmpty(), shape = RoundedCornerShape(13.dp)) { Text("Clear", fontSize = 11.sp) }
        }
        Text(if (editing) "Tap 3–16 points to draw any custom shape. Finish joins the last point to the first."
            else "Motion uses frame comparison · objects use an on-device model · no cloud video, face or emotion analysis.",
            Modifier.padding(18.dp, 7.dp, 18.dp, 10.dp), color = Ink.copy(.55f), fontSize = 10.sp)
    }
    if (videosOpen) VideoLibraryDialog(videoLibrary, demoUri, onDismiss = { videosOpen = false }, onCamera = { demoUri = null; videosOpen = false },
        onSelect = { demoUri = it; videosOpen = false }, onAdd = { picker.launch(arrayOf("video/*")) })
}

private data class VideoChoice(val uri: Uri, val name: String)

private fun loadVideoLibrary(context: Context): List<VideoChoice> {
    val preferences = context.getSharedPreferences("kola_settings", 0)
    return preferences.getStringSet("video_library", emptySet()).orEmpty().mapNotNull { value ->
        runCatching { Uri.parse(value) }.getOrNull()?.let { uri ->
            VideoChoice(uri, preferences.getString("video_name_${value.hashCode()}", null) ?: videoName(context, uri))
        }
    }.sortedBy { it.name.lowercase() }
}

private fun videoName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video"

@Composable
private fun VideoLibraryDialog(videos: List<VideoChoice>, selected: Uri?, onDismiss: () -> Unit, onCamera: () -> Unit, onSelect: (Uri) -> Unit, onAdd: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Column { Text("Video library", color = Ink, fontWeight = FontWeight.Black); Text("Choose a test clip or return to the live camera", color = Ink.copy(.55f), fontSize = 11.sp) } },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { VideoChoiceRow("Live camera", selected == null, onCamera) }
            items(videos, key = { it.uri.toString() }) { video -> VideoChoiceRow(video.name, selected == video.uri) { onSelect(video.uri) } }
            if (videos.isEmpty()) item { Text("No test videos added yet.", color = Ink.copy(.5f), fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
        } },
        confirmButton = { Button(onAdd, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White)) { Text("Add videos") } },
        dismissButton = { TextButton(onDismiss) { Text("Close") } }, containerColor = Paper, shape = RoundedCornerShape(24.dp))
}

@Composable
private fun VideoChoiceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Mint.copy(.14f) else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (selected) Mint else Ink.copy(.18f), CircleShape)); Spacer(Modifier.width(10.dp))
            Text(name, Modifier.weight(1f), color = Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 2)
            if (selected) Text("ACTIVE", color = Ink.copy(.55f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
        }
    }
}

private fun zoneLabel(value: Boolean?) = when (value) { true -> "INSIDE ZONE"; false -> "OUTSIDE ZONE"; null -> "NO ZONE SIGNAL" }

@Composable
private fun SignalTile(label: String, plain: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(Paper), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(9.dp, 8.dp)) { Box(Modifier.width(18.dp).height(3.dp).background(accent, CircleShape)); Spacer(Modifier.height(4.dp)); Text(value, color = Ink, fontWeight = FontWeight.Black, fontSize = 16.sp); Text(label, color = Ink.copy(.5f), fontWeight = FontWeight.Bold, fontSize = 7.sp, letterSpacing = .35.sp, maxLines = 1); Text("($plain)", color = Ink.copy(.42f), fontWeight = FontWeight.Medium, fontSize = 6.sp, lineHeight = 7.sp, maxLines = 2) }
    }
}

@Composable
private fun DemoVideo(vm: MonitorViewModel, uri: Uri, viewportAspect: Float) {
    val context = LocalContext.current
    val analyzer = remember(uri, viewportAspect) { DemoVideoAnalyzer(context, viewportAspect, vm::onVisionFrame) }
    var texture by remember(uri) { mutableStateOf<TextureView?>(null) }
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    LaunchedEffect(texture, analyzer) {
        while (isActive) {
            texture?.takeIf { it.isAvailable && it.width > 0 && it.height > 0 }?.let { view ->
                val width = 480
                val height = (width / viewportAspect).toInt().coerceAtLeast(1)
                view.getBitmap(width, height)?.let(analyzer::submit)
            }
            delay(125L)
        }
    }
    DisposableEffect(analyzer) { onDispose { analyzer.close(); player?.release(); player = null } }
    AndroidView(factory = { ctx -> TextureView(ctx).apply {
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                val media = MediaPlayer()
                runCatching {
                    media.setDataSource(ctx, uri)
                    Surface(surfaceTexture).also { surface -> media.setSurface(surface); surface.release() }
                    media.isLooping = true
                    media.setOnPreparedListener { it.start() }
                    media.prepareAsync()
                    player = media
                }.onFailure {
                    media.release()
                    vm.onVisionFrame(VisionState(status = "Video permission unavailable · reopen the file", source = "VIDEO"))
                }
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { player?.release(); player = null; return true }
        }
        texture = this
    } }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PermissionCamera(vm: MonitorViewModel) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) launcher.launch(Manifest.permission.CAMERA) }
    if (allowed) CameraPreview(vm) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button({ launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") } }
}

@Composable
private fun CameraPreview(vm: MonitorViewModel) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }; val analyzer = remember { VisionAnalyzer(context, vm::onVisionFrame) }
    DisposableEffect(Unit) { onDispose { analyzer.close(); executor.shutdown() } }
    AndroidView(factory = { ctx -> PreviewView(ctx).apply {
        scaleType = PreviewView.ScaleType.FIT_CENTER
        ProcessCameraProvider.getInstance(context).also { future -> future.addListener({
            val provider = future.get(); val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context)) }
    } }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun DetectionOverlay(state: VisionState, zone: List<Offset>, zoneClosed: Boolean, editing: Boolean, options: OverlayOptions, addPoint: (Offset) -> Unit) {
    Canvas(Modifier.fillMaxSize().pointerInput(editing) { if (editing) detectTapGestures { addPoint(Offset(it.x/size.width, it.y/size.height)) } }) {
        if (options.areaHeatmap) state.areaHeatmap.forEach { p ->
            val tile = Size(size.width / 16f, size.height / 12f)
            drawRoundRect(Blue.copy(alpha = .025f + p.intensity * .13f), Offset(p.position.x*size.width-tile.width/2,p.position.y*size.height-tile.height/2), tile,
                androidx.compose.ui.geometry.CornerRadius(7f,7f))
        }
        if (options.movementRegion) state.motionBounds?.let { b ->
            val tl=Offset(b.left*size.width,b.top*size.height); val box=Size(b.width*size.width,b.height*size.height)
            drawRoundRect(Mint.copy(.52f),tl,box,androidx.compose.ui.geometry.CornerRadius(8f),style=Stroke(1.5f))
        }
        if (options.safeZone && zone.isNotEmpty()) {
            val path=Path().apply { moveTo(zone[0].x*size.width,zone[0].y*size.height); zone.drop(1).forEach { lineTo(it.x*size.width,it.y*size.height) }; if(zoneClosed&&zone.size>=3) close() }
            if(zoneClosed) drawPath(path,(if(state.insideZone==false) Coral else Mint).copy(.055f))
            drawPath(path,(if(state.insideZone==false) Coral else Mint).copy(.76f),style=Stroke(2f))
            zone.forEachIndexed { i,p -> val at=Offset(p.x*size.width,p.y*size.height); drawCircle(Color.Black.copy(.45f),5f,at); drawCircle(Color.White.copy(.9f),3f,at); if(editing&&i==zone.lastIndex) drawCircle(Coral,2f,at) }
        }
        if (options.boundaryRadar && zoneClosed && zone.size >= 3) state.subjectCenter?.let { c ->
            val nearest = zone.indices.map { i -> nearestOnSegment(c, zone[i], zone[(i+1)%zone.size]) }.minByOrNull { (it-c).getDistance() }
            nearest?.let { edge ->
                val from=Offset(c.x*size.width,c.y*size.height); val to=Offset(edge.x*size.width,edge.y*size.height)
                val color=if(state.boundaryEtaSeconds != null && state.boundaryEtaSeconds < 4f) Coral else Color.White.copy(.62f)
                drawLine(color,from,to,1.5f,StrokeCap.Round); drawCircle(color,4f,to,style=Stroke(1.5f))
            }
        }
        if (options.movementDirection) state.subjectCenter?.let { c ->
            val at=Offset(c.x*size.width,c.y*size.height); val color=if(state.insideZone==false) Coral else Mint
            drawCircle(Color.Black.copy(.38f),7f,at); drawCircle(color.copy(.88f),4f,at)
            val end=at+Offset(state.directionX*size.width*2.2f,state.directionY*size.height*2.2f)
            if(abs(state.directionX)+abs(state.directionY)>.006f) drawLine(color.copy(.72f),at,end,1.8f,StrokeCap.Round)
        }
        if (options.poseGuides && state.poseJoints.isNotEmpty()) {
            val joints = state.poseJoints.associateBy { it.id }
            val segments = listOf(
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
                PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
                PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
                PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
            )
            segments.forEach { (startId,endId) ->
                val start=joints[startId]?.position; val end=joints[endId]?.position
                if(start!=null&&end!=null) drawLine(Color.White.copy(.48f),Offset(start.x*size.width,start.y*size.height),Offset(end.x*size.width,end.y*size.height),1.35f,StrokeCap.Round)
            }
            state.poseJoints.forEach { joint ->
                val at=Offset(joint.position.x*size.width,joint.position.y*size.height)
                drawCircle(Color.Black.copy(.38f),4.2f,at); drawCircle(Mint.copy(.82f),2.5f,at)
            }
        }
        if (options.objects) state.detectedItems.forEach { item ->
            val b=item.bounds; val tl=Offset(b.left*size.width,b.top*size.height); val box=Size(b.width*size.width,b.height*size.height)
            val color=when(item.risk){"DANGER"->Coral;"REVIEW"->Color(0xFFFFB94A);else->Color.White}
            drawRoundRect(color.copy(.68f),tl,box,androidx.compose.ui.geometry.CornerRadius(6f),style=Stroke(1.5f))
            drawContext.canvas.nativeCanvas.drawText("${item.label} ${(item.confidence*100).toInt()}%",tl.x+5f,(tl.y-6f).coerceAtLeast(16f),android.graphics.Paint().apply{this.color=android.graphics.Color.argb(210,255,255,255);textSize=11.sp.toPx();isAntiAlias=true})
        }
    }
}

private fun nearestOnSegment(point: Offset, a: Offset, b: Offset): Offset {
    val ab=b-a; val length2=ab.x*ab.x+ab.y*ab.y
    val t=if(length2==0f)0f else (((point-a).x*ab.x+(point-a).y*ab.y)/length2).coerceIn(0f,1f)
    return a+ab*t
}

@Composable
private fun LayerToggle(label:String,checked:Boolean,onChange:(Boolean)->Unit){
    DropdownMenuItem(text={Text(label,color=Ink,fontSize=12.sp)},onClick={onChange(!checked)},trailingIcon={Switch(checked,onChange,modifier=Modifier.height(24.dp),colors=SwitchDefaults.colors(checkedTrackColor=Mint))})
}

@Composable
private fun TrendsScreen(vm: MonitorViewModel) {
    val history by vm.telemetry.collectAsStateWithLifecycle(); val current by vm.vision.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Spacer(Modifier.height(18.dp)); Text("Signal studio",fontSize=28.sp,fontWeight=FontWeight.Black,color=Ink); Text("Live measurements explained in plain language",color=Ink.copy(.55f)); Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { TrendStat("PROCESSING TIME\n(HOW FAST)","${current.processingMs} ms",Modifier.weight(1f)); TrendStat("ANALYSIS RATE\n(UPDATES / SEC)","${current.fps} fps",Modifier.weight(1f)); TrendStat("DIRECTION\n(WHERE IT MOVED)",current.direction,Modifier.weight(1f)) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { TrendStat("MOVE SPEED\n(HOW FAST IT MOVES)","${(current.movementSpeed*100).toInt()} /s",Modifier.weight(1f)); TrendStat("LONGEST QUIET\n(NO MOVEMENT)","${current.longestQuietMs/1000}s",Modifier.weight(1f)); TrendStat("NEW ITEM AGE\n(HOW LONG PRESENT)","${current.newObjectPersistenceMs/1000}s",Modifier.weight(1f)) }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=18.dp)) {
            item { DirectionCard(history) }
            item { TrendChart("MOTION ENERGY (HOW MUCH MOVED)",history.map{it.motion},1f,Coral) }
            item { TrendChart("ACTIVE FRAME AREA (HOW MUCH OF THE VIEW MOVED)",history.map{it.motionArea},1f,Mint) }
            item { TrendChart("MOVEMENT SPEED (HOW FAST THE MOVEMENT TRAVELS)",history.map{it.movementSpeed},1f,Color(0xFFFFB94A)," /s") }
            item { TrendChart("DISTANCE TO SAFE-ZONE EDGE (ROOM BEFORE CROSSING)",history.map{it.boundaryDistance?:0f},.5f,Color(0xFF36B8C9)) }
            item { TrendChart("NEW-OBJECT PERSISTENCE (HOW LONG A NEW ITEM REMAINS)",history.map{it.newObjectPersistenceMs/1000f},20f,Color(0xFF9B76D6)," s") }
            item { TrendChart("SCENE STABILITY (HOW STEADY THE VIEW IS)",history.map{it.stability},1f,Blue) }
            item { TrendChart("EXPOSURE / LIGHT (HOW BRIGHT THE VIEW IS)",history.map{it.brightness},1f,Color(0xFFFFB94A)) }
            item { TrendChart("IMAGE CONTRAST (HOW EASY DETAILS ARE TO SEE)",history.map{it.sceneContrast},1f,Color(0xFF71859F)) }
            item { TrendChart("PROCESSING TIME (HOW FAST EACH CHECK RUNS)",history.map{it.processingMs.toFloat()},40f,Color(0xFF9B76D6)," ms") }
            item { TrendChart("ANALYSIS RATE (UPDATES EACH SECOND)",history.map{it.fps.toFloat()},30f,Ink," fps") }
        }
    }
}

@Composable
private fun DirectionCard(history: List<TelemetrySample>) {
    Card(colors=CardDefaults.cardColors(Paper),shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(15.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("MOTION VECTOR (WHERE MOVEMENT IS HEADING)",color=Ink.copy(.5f),fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=.5.sp); Text("LEFT/RIGHT · UP/DOWN",color=Ink,fontWeight=FontWeight.Bold,fontSize=8.sp) }
        Canvas(Modifier.fillMaxWidth().height(108.dp).padding(top=8.dp)) {
            drawLine(Ink.copy(.1f),Offset(size.width/2,0f),Offset(size.width/2,size.height),1f); drawLine(Ink.copy(.1f),Offset(0f,size.height/2),Offset(size.width,size.height/2),1f)
            if(history.size>1) { val path=Path(); history.takeLast(60).forEachIndexed { i,s -> val x=size.width/2+s.directionX*size.width*6f; val y=size.height/2+s.directionY*size.height*6f; if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }; drawPath(path,Mint,style=Stroke(3f)); val s=history.last(); drawCircle(Coral,6f,Offset(size.width/2+s.directionX*size.width*6f,size.height/2+s.directionY*size.height*6f)) }
        }
    } }
}

@Composable
private fun TrendStat(label:String,value:String,modifier:Modifier=Modifier) { Card(modifier,colors=CardDefaults.cardColors(Paper),shape=RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp)) { Text(label,color=Ink.copy(.45f),fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=.8.sp); Text(value,color=Ink,fontSize=15.sp,fontWeight=FontWeight.Black) } } }

@Composable
private fun TrendChart(label:String,values:List<Float>,ceiling:Float,color:Color,suffix:String="%") {
    val latest=values.lastOrNull()?:0f; val shown=if(suffix=="%") "${(latest*100).toInt()}%" else "${latest.toInt()}$suffix"
    Card(colors=CardDefaults.cardColors(Paper),shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(15.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,color=Ink.copy(.5f),fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text(shown,color=Ink,fontWeight=FontWeight.Black) }
        Canvas(Modifier.fillMaxWidth().height(86.dp).padding(top=8.dp)) {
            repeat(4){i->val y=size.height*i/3f;drawLine(Ink.copy(.07f),Offset(0f,y),Offset(size.width,y),1f)}
            if(values.size>=2){ val line=Path(); val fill=Path(); values.forEachIndexed{i,v->val x=i.toFloat()/(values.size-1)*size.width;val y=size.height*(1f-(v/ceiling).coerceIn(0f,1f));if(i==0){line.moveTo(x,y);fill.moveTo(x,size.height);fill.lineTo(x,y)}else{line.lineTo(x,y);fill.lineTo(x,y)}};fill.lineTo(size.width,size.height);fill.close();drawPath(fill,Brush.verticalGradient(listOf(color.copy(.24f),Color.Transparent)));drawPath(line,color,style=Stroke(3.5f));val y=size.height*(1f-(values.last()/ceiling).coerceIn(0f,1f));drawCircle(Paper,7f,Offset(size.width,y));drawCircle(color,5f,Offset(size.width,y)) }
        }
    } }
}

@Composable
private fun HistoryScreen(vm: MonitorViewModel) {
    val events by vm.events.collectAsStateWithLifecycle(); val telemetry by vm.telemetry.collectAsStateWithLifecycle(); var filter by remember{mutableStateOf("All")}
    val filtered=events.filter{filter=="All"||when(filter){"Safety"->it.kind==AlertKind.BOUNDARY||it.kind==AlertKind.STILLNESS||it.kind==AlertKind.OBJECT;"Motion"->it.kind==AlertKind.MOTION;else->it.kind==AlertKind.SYSTEM}}
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Spacer(Modifier.height(18.dp)); Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Timeline",fontSize=28.sp,fontWeight=FontWeight.Black,color=Ink);Text("On-device evidence, not guesses",color=Ink.copy(.55f))};TextButton(vm::addDemoEvent){Text("Add test")}}
        Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TrendStat("EVENTS\n(SAVED MOMENTS)",events.size.toString(),Modifier.weight(1f));TrendStat("SAFETY\n(IMPORTANT)",events.count{it.severity>=3}.toString(),Modifier.weight(1f));TrendStat("AVG MOTION\n(USUAL MOVEMENT)","${((telemetry.map{it.motion}.average().takeIf{!it.isNaN()}?:0.0)*100).toInt()}%",Modifier.weight(1f))}
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("All","Safety","Motion","System").forEach{label->FilterChip(filter==label,{filter=label},{Text(label,fontSize=10.sp)})}}
        Text("Stored locally · latest 100 events",color=Ink.copy(.42f),fontSize=10.sp);Spacer(Modifier.height(10.dp))
        if(filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(42.dp).background(Mint.copy(.17f),CircleShape));Spacer(Modifier.height(10.dp));Text("No matching events",fontWeight=FontWeight.Bold,color=Ink);Text("Quiet is the default state.",color=Ink.copy(.5f),fontSize=12.sp)}}
        else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=8.dp)){items(filtered,key={it.id}){e->Card(colors=CardDefaults.cardColors(Paper),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(15.dp)){Box(Modifier.width(4.dp).height(48.dp).background(if(e.severity>=3)Coral else Mint,CircleShape));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(e.title,fontWeight=FontWeight.Bold,color=Ink);Text(vm.eventTime(e.timestamp),color=Ink.copy(.4f),fontSize=10.sp)};Text(e.detail,color=Ink.copy(.6f),fontSize=12.sp,lineHeight=16.sp);Text(e.kind.name,color=Ink.copy(.38f),fontWeight=FontWeight.Bold,fontSize=8.sp,letterSpacing=1.sp)}}}};item{TextButton(vm::clearHistory,Modifier.fillMaxWidth()){Text("Clear local history",color=Coral)}}}
    }
}

@Composable
private fun ConnectScreen(vm: MonitorViewModel) {
    val stored by vm.serverUrl.collectAsStateWithLifecycle()
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    val alerts by vm.notificationSettings.collectAsStateWithLifecycle()
    var url by remember(stored){mutableStateOf(stored)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(top=18.dp,bottom=22.dp)) {
        item { Text("Alerts & connection",fontSize=28.sp,fontWeight=FontWeight.Black,color=Ink) }
        item { Text("Choose exactly which activities may interrupt you. Turning one off stops its phone notification but keeps the event in History.",color=Ink.copy(.65f),lineHeight=19.sp) }
        item { Card(colors=CardDefaults.cardColors(Paper),shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("NOTIFICATION CONTROLS",color=Ink.copy(.45f),fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp);Text("Niche activity alerts",color=Ink,fontSize=19.sp,fontWeight=FontWeight.Black)};TextButton(vm::testNotification,colors=ButtonDefaults.textButtonColors(contentColor=Ink)){Text("Test")}}
            NotificationRule.entries.forEachIndexed { index, rule ->
                if(index>0) HorizontalDivider(color=Ink.copy(.07f))
                Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(rule.title,color=Ink,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(rule.plainDescription,color=Ink.copy(.55f),fontSize=10.sp,lineHeight=13.sp)};Switch(checked=alerts.isEnabled(rule),onCheckedChange={vm.setNotificationEnabled(rule,it)},colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=Mint))}
            }
        } } }
        item { OutlinedTextField(url,{url=it},label={Text("Local dashboard URL")},placeholder={Text("http://192.168.1.20:8787")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp)) }
        item { Button({vm.updateServerUrl(url)},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Ink,contentColor=Color.White),shape=RoundedCornerShape(14.dp)){Text("Save and sync settings")} }
        item { Card(colors=CardDefaults.cardColors(Paper),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text("CONNECTION",color=Ink.copy(.45f),fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp);Text(sync,color=if(sync.startsWith("Synced"))Color(0xFF138B65)else Coral,fontSize=17.sp,fontWeight=FontWeight.Black);Text("Video stays on Android · only measurements, events and alert settings are synchronized.",color=Ink.copy(.55f),fontSize=11.sp,lineHeight=15.sp)}} }
    }
}

@Composable
private fun StatusPill(text:String,color:Color,darkText:Boolean=false){Text(text,color=if(darkText)Ink else Color.White,fontWeight=FontWeight.Bold,fontSize=9.sp,letterSpacing=.5.sp,modifier=Modifier.background(color,CircleShape).padding(horizontal=9.dp,vertical=6.dp))}

package com.vizeno.kola

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("kola_settings", 0)
    private val _vision = MutableStateFlow(VisionState())
    val vision: StateFlow<VisionState> = _vision.asStateFlow()
    private val _events = MutableStateFlow(loadEvents())
    val events: StateFlow<List<MonitorEvent>> = _events.asStateFlow()
    private val _telemetry = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val telemetry: StateFlow<List<TelemetrySample>> = _telemetry.asStateFlow()
    private val _zone = MutableStateFlow<List<Offset>>(emptyList())
    val zone: StateFlow<List<Offset>> = _zone.asStateFlow()
    private val _zoneClosed = MutableStateFlow(false)
    val zoneClosed: StateFlow<Boolean> = _zoneClosed.asStateFlow()
    private val _serverUrl = MutableStateFlow(preferences.getString("server_url", "http://192.168.1.2:8787") ?: "http://192.168.1.2:8787")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    private val _syncStatus = MutableStateFlow("Local only")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()
    private val _notificationSettings = MutableStateFlow(loadNotificationSettings())
    val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()

    private val client = OkHttpClient.Builder().connectTimeout(1200, TimeUnit.MILLISECONDS).build()
    private var outsideSince: Long? = null
    private var darkSince: Long? = null
    private var shiftFrames = 0
    private var boundaryAlerted = false
    private var stillAlerted = false
    private var obstructionAlerted = false
    private var cameraShiftAlerted = false
    private var movementFrames = 0
    private var movementAlerted = false
    private var lastTelemetryAt = 0L
    private val areaHeatmap = AreaHeatmap()
    private var quietStartedAt: Long? = null
    private var longestQuietMs = 0L
    private val objectAlerts = mutableSetOf<String>()

    init {
        createNotificationChannel()
        pushNotificationSettings()
        viewModelScope.launch(Dispatchers.IO) { while (true) { syncSnapshot(); pullNotificationSettings(); delay(3_000) } }
    }

    fun updateServerUrl(value: String) {
        _serverUrl.value = value.trim().trimEnd('/')
        preferences.edit().putString("server_url", _serverUrl.value).apply()
        syncSnapshot()
        pushNotificationSettings()
    }
    fun setNotificationEnabled(rule: NotificationRule, enabled: Boolean) {
        _notificationSettings.value = NotificationSettings(_notificationSettings.value.enabled + (rule to enabled))
        preferences.edit().putBoolean("notify_${rule.name}", enabled).apply()
        pushNotificationSettings()
    }
    fun testNotification() = showNotification(NotificationRule.MOVEMENT_BURST, "Kola notifications are ready", "You will only receive the alert types enabled below.", 1)
    fun beginZoneEditing() { _zoneClosed.value = false }
    fun finishZone() { _zoneClosed.value = _zone.value.size >= 3 }
    fun addZonePoint(point: Offset) { if (_zone.value.size < 16) { _zoneClosed.value = false; _zone.value += point } }
    fun undoZonePoint() { _zoneClosed.value = false; _zone.value = _zone.value.dropLast(1) }
    fun clearZone() { _zoneClosed.value = false; _zone.value = emptyList() }

    fun onVisionFrame(state: VisionState) {
        val inside = state.subjectCenter?.takeIf { _zoneClosed.value && state.activityPresent }?.let { ZoneMath.contains(it, _zone.value) }
        val now = System.currentTimeMillis()
        if (state.motion < .025f) quietStartedAt = quietStartedAt ?: now else quietStartedAt = null
        longestQuietMs = maxOf(longestQuietMs, quietStartedAt?.let { now - it } ?: 0L)
        val distance = state.subjectCenter?.takeIf { _zoneClosed.value }?.let { ZoneMath.distanceToEdges(it, _zone.value) }
        val eta = state.subjectCenter?.takeIf { _zoneClosed.value && state.activityPresent && state.direction != "STILL" }?.let {
            ZoneMath.predictedExitSeconds(it, Offset(state.directionX, state.directionY), state.movementSpeed, _zone.value)
        }
        val persistence = state.detectedItems.filter { it.newlyIntroduced }.maxOfOrNull { it.persistenceMs } ?: 0L
        val next = state.copy(insideZone = inside, areaHeatmap = areaHeatmap.update(state.heatPoints),
            boundaryDistance = distance, boundaryEtaSeconds = eta, longestQuietMs = longestQuietMs,
            newObjectPersistenceMs = persistence)
        _vision.value = next
        if (now - lastTelemetryAt >= 400L) {
            lastTelemetryAt = now
            _telemetry.value = (_telemetry.value + TelemetrySample(now, next.motion, next.motionArea, next.brightness,
                next.sceneContrast, next.stability, next.directionX, next.directionY, next.processingMs, next.fps, next.insideZone,
                next.movementSpeed, next.boundaryDistance, next.boundaryEtaSeconds, next.newObjectPersistenceMs)).takeLast(150)
        }
        if (inside == false) outsideSince = outsideSince ?: now else { outsideSince = null; boundaryAlerted = false }
        if (!boundaryAlerted && outsideSince?.let { now - it > 2_000 } == true) {
            boundaryAlerted = true
            addEvent(AlertKind.BOUNDARY, NotificationRule.ZONE_EXIT, "Safe-zone crossing", "The center of movement stayed outside the custom drawn shape for 2 seconds.", 3)
        }
        val stillNow = state.activityPresent && now - state.lastMovementAt > 12_000
        if (!stillNow) stillAlerted = false
        if (stillNow && !stillAlerted) {
            stillAlerted = true
            addEvent(AlertKind.STILLNESS, NotificationRule.EXTENDED_QUIET, "Extended stillness", "No significant frame change for 12 seconds. Check the live view.", 2)
        }
        movementFrames = if (!state.cameraShift && state.motion > .48f && state.motionArea in .04f..0.60f) movementFrames + 1 else 0
        if (state.motion < .22f) movementAlerted = false
        if (movementFrames >= 2 && !movementAlerted) {
            movementAlerted = true
            addEvent(AlertKind.MOTION, NotificationRule.MOVEMENT_BURST, "Sudden movement burst", "A large amount of the view moved quickly for more than one check.", 2)
        }
        if (state.brightness in 0.001f..0.035f || state.sceneContrast in 0.001f..0.025f) darkSince = darkSince ?: now
        else { darkSince = null; obstructionAlerted = false }
        shiftFrames = if (state.cameraShift) shiftFrames + 1 else 0
        if (shiftFrames == 0) cameraShiftAlerted = false
        if (!obstructionAlerted && darkSince?.let { now - it > 3_000 } == true) {
            obstructionAlerted = true
            addEvent(AlertKind.SYSTEM, NotificationRule.VIEW_BLOCKED, "Camera may be covered", "The image stayed dark or nearly flat for 3 seconds.", 2)
        } else if (shiftFrames >= 2 && !cameraShiftAlerted) {
            cameraShiftAlerted = true
            addEvent(AlertKind.SYSTEM, NotificationRule.CAMERA_MOVED, "Camera view changed", "Most of the picture changed at once, which may mean the phone moved.", 2)
        }
        val visibleRiskLabels = state.detectedItems.filter { it.risk != "NONE" }.map { it.label }.toSet()
        objectAlerts.retainAll(visibleRiskLabels)
        state.detectedItems.filter { it.newlyIntroduced && it.risk != "NONE" && it.persistenceMs >= 1_400L }.forEach { item ->
            if (objectAlerts.add(item.label)) addEvent(AlertKind.OBJECT, NotificationRule.UNSAFE_ITEM,
                if (item.risk == "DANGER") "Unsafe item detected" else "New item needs review",
                "${item.label.replaceFirstChar { it.uppercase() }} remained in the monitored area for ${item.persistenceMs / 1_000f}s.",
                if (item.risk == "DANGER") 3 else 2)
        }
    }

    fun addDemoEvent() = addEvent(AlertKind.MOTION, NotificationRule.MOVEMENT_BURST, "Movement burst", "Changes between video frames crossed the movement alert level.", 1)
    private fun addEvent(kind: AlertKind, rule: NotificationRule, title: String, detail: String, severity: Int) {
        val event = MonitorEvent(kind = kind, title = title, detail = detail, severity = severity)
        _events.value = (listOf(event) + _events.value).take(100)
        persistEvents(); postJson("/api/events", eventJson(event))
        showNotification(rule, title, detail, severity)
    }
    fun clearHistory() { _events.value = emptyList(); persistEvents() }

    private fun loadEvents(): List<MonitorEvent> = try {
        val array = JSONArray(preferences.getString("event_history", "[]"))
        (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            MonitorEvent(value.getLong("id"), runCatching { AlertKind.valueOf(value.getString("kind")) }.getOrDefault(AlertKind.SYSTEM),
                value.getString("title"), value.getString("detail"), value.getInt("severity"), value.getLong("timestamp"))
        }
    } catch (_: Exception) { emptyList() }

    private fun persistEvents() {
        val array = JSONArray()
        _events.value.forEach { e -> array.put(JSONObject().put("id", e.id).put("timestamp", e.timestamp).put("kind", e.kind.name)
            .put("title", e.title).put("detail", e.detail).put("severity", e.severity)) }
        preferences.edit().putString("event_history", array.toString()).apply()
    }

    private fun loadNotificationSettings() = NotificationSettings(NotificationRule.entries.associateWith {
        preferences.getBoolean("notify_${it.name}", true)
    })
    private fun settingsJson() = JSONObject().also { json -> NotificationRule.entries.forEach { json.put(it.name.lowercase(), _notificationSettings.value.isEnabled(it)) } }
    private fun pushNotificationSettings() = postJson("/api/settings", settingsJson())
    private fun pullNotificationSettings() {
        val base = _serverUrl.value
        if (!base.startsWith("http")) return
        try {
            client.newCall(Request.Builder().url("$base/api/settings").get().build()).execute().use { response ->
                if (!response.isSuccessful) return
                val json = JSONObject(response.body.string())
                val values = NotificationRule.entries.associateWith { json.optBoolean(it.name.lowercase(), _notificationSettings.value.isEnabled(it)) }
                if (values != _notificationSettings.value.enabled) {
                    _notificationSettings.value = NotificationSettings(values)
                    val edit = preferences.edit(); values.forEach { (rule, enabled) -> edit.putBoolean("notify_${rule.name}", enabled) }; edit.apply()
                }
            }
        } catch (_: Exception) { Unit }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("kola_alerts", "Nursery alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Selected Kola movement, zone and camera alerts"
            }
            getApplication<Application>().getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun showNotification(rule: NotificationRule, title: String, detail: String, severity: Int) {
        if (!_notificationSettings.value.isEnabled(rule)) return
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, rule.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "kola_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail)).setContentIntent(pending).setAutoCancel(true)
            .setPriority(if (severity >= 3) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM).build()
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun syncSnapshot() {
        val s = _vision.value
        postJson("/api/status", JSONObject().put("device", "Kola Android").put("online", true).put("fps", s.fps)
            .put("motion", s.motion.toDouble()).put("motionArea", s.motionArea.toDouble()).put("brightness", s.brightness.toDouble())
            .put("sceneContrast", s.sceneContrast.toDouble()).put("stability", s.stability.toDouble())
            .put("directionX", s.directionX.toDouble()).put("directionY", s.directionY.toDouble()).put("direction", s.direction)
            .put("processingMs", s.processingMs).put("cameraShift", s.cameraShift).put("source", s.source).put("status", s.status)
            .put("movementSpeed", s.movementSpeed.toDouble()).put("boundaryDistance", s.boundaryDistance)
            .put("boundaryEtaSeconds", s.boundaryEtaSeconds).put("longestQuietMs", s.longestQuietMs)
            .put("newObjectPersistenceMs", s.newObjectPersistenceMs)
            .put("detectedObjects", JSONArray().also { array -> s.detectedItems.forEach { array.put(JSONObject().put("label", it.label).put("confidence", it.confidence).put("risk", it.risk).put("new", it.newlyIntroduced).put("persistenceMs", it.persistenceMs)) } })
            .put("insideZone", s.insideZone).put("activityPresent", s.activityPresent).put("updatedAt", System.currentTimeMillis()))
    }
    private fun eventJson(e: MonitorEvent) = JSONObject().put("id", e.id.toString()).put("kind", e.kind.name.lowercase())
        .put("title", e.title).put("detail", e.detail).put("severity", e.severity).put("timestamp", e.timestamp)
    private fun postJson(path: String, json: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            val base = _serverUrl.value
            if (!base.startsWith("http")) return@launch
            val request = Request.Builder().url(base + path).post(json.toString().toRequestBody("application/json".toMediaType())).build()
            try { client.newCall(request).execute().use { _syncStatus.value = if (it.isSuccessful) "Synced now" else "Sync ${it.code}" } }
            catch (_: Exception) { _syncStatus.value = "Local only" }
        }
    }
    fun eventTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

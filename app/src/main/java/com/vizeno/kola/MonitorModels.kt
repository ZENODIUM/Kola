package com.vizeno.kola

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

enum class AlertKind { BOUNDARY, STILLNESS, MOTION, OBJECT, SYSTEM }

enum class NotificationRule(val title: String, val plainDescription: String) {
    ZONE_EXIT("Safe-zone exit", "Movement stays outside your drawn safe shape"),
    MOVEMENT_BURST("Sudden movement burst", "A large part of the view moves quickly"),
    EXTENDED_QUIET("Extended quiet", "Movement stops for an unusually long period"),
    CAMERA_MOVED("Camera moved", "Most of the picture shifts at the same time"),
    VIEW_BLOCKED("View blocked or dark", "The camera becomes dark or details disappear"),
    UNSAFE_ITEM("Unsafe item nearby", "A knife, scissors, or another review item stays visible"),
}

data class NotificationSettings(val enabled: Map<NotificationRule, Boolean> = NotificationRule.entries.associateWith { true }) {
    fun isEnabled(rule: NotificationRule) = enabled[rule] != false
}

data class MonitorEvent(
    val id: Long = System.currentTimeMillis(),
    val kind: AlertKind,
    val title: String,
    val detail: String,
    val severity: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

data class HeatPoint(val position: Offset, val intensity: Float)

data class DetectedItem(
    val label: String,
    val confidence: Float,
    val bounds: Rect,
    val risk: String = "NONE",
    val newlyIntroduced: Boolean = false,
    val persistenceMs: Long = 0,
)

data class PoseJoint(val id: Int, val position: Offset, val confidence: Float)

data class OverlayOptions(
    val safeZone: Boolean = true,
    val areaHeatmap: Boolean = true,
    val movementRegion: Boolean = true,
    val movementDirection: Boolean = true,
    val boundaryRadar: Boolean = true,
    val objects: Boolean = true,
    val poseGuides: Boolean = true,
)

data class VisionState(
    val fps: Int = 0,
    val motion: Float = 0f,
    val motionArea: Float = 0f,
    val brightness: Float = 0f,
    val sceneContrast: Float = 0f,
    val stability: Float = 1f,
    val directionX: Float = 0f,
    val directionY: Float = 0f,
    val direction: String = "STILL",
    val activityPresent: Boolean = false,
    val status: String = "Waiting for camera",
    val subjectCenter: Offset? = null,
    val motionBounds: Rect? = null,
    val heatPoints: List<HeatPoint> = emptyList(),
    val areaHeatmap: List<HeatPoint> = emptyList(),
    val detectedItems: List<DetectedItem> = emptyList(),
    val poseJoints: List<PoseJoint> = emptyList(),
    val cameraShift: Boolean = false,
    val processingMs: Long = 0,
    val source: String = "CAMERA",
    val insideZone: Boolean? = null,
    val movementSpeed: Float = 0f,
    val boundaryDistance: Float? = null,
    val boundaryEtaSeconds: Float? = null,
    val longestQuietMs: Long = 0,
    val newObjectPersistenceMs: Long = 0,
    val lastMovementAt: Long = System.currentTimeMillis(),
)

data class TelemetrySample(
    val timestamp: Long,
    val motion: Float,
    val motionArea: Float,
    val brightness: Float,
    val sceneContrast: Float,
    val stability: Float,
    val directionX: Float,
    val directionY: Float,
    val processingMs: Long,
    val fps: Int,
    val insideZone: Boolean?,
    val movementSpeed: Float,
    val boundaryDistance: Float?,
    val boundaryEtaSeconds: Float?,
    val newObjectPersistenceMs: Long,
)

object ZoneMath {
    fun contains(point: Offset, polygon: List<Offset>): Boolean {
        if (polygon.size < 3) return true
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val crosses = (a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / ((b.y - a.y).takeIf { it != 0f } ?: 0.0001f) + a.x
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    fun distanceToEdges(point: Offset, polygon: List<Offset>): Float? {
        if (polygon.size < 3) return null
        return polygon.indices.minOf { i ->
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val ab = b - a
            val length2 = ab.x * ab.x + ab.y * ab.y
            val t = if (length2 == 0f) 0f else (((point - a).x * ab.x + (point - a).y * ab.y) / length2).coerceIn(0f, 1f)
            (point - (a + ab * t)).getDistance()
        }
    }

    fun predictedExitSeconds(point: Offset, direction: Offset, speed: Float, polygon: List<Offset>, horizon: Float = 8f): Float? {
        if (polygon.size < 3 || !contains(point, polygon) || speed < .008f || direction.getDistance() < .006f) return null
        val unit = direction / direction.getDistance()
        var t = .2f
        while (t <= horizon) {
            if (!contains(point + unit * speed * t, polygon)) return t
            t += .2f
        }
        return null
    }
}

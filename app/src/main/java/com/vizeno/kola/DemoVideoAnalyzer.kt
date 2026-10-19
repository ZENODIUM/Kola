package com.vizeno.kola

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Analyzes bitmaps captured from the playing TextureView, so overlay time matches the visible video. */
class DemoVideoAnalyzer(
    context: Context,
    private val viewportAspect: Float,
    private val onResult: (VisionState) -> Unit,
) {
    private val engine = ClassicalVisionEngine()
    private val objectDetector = AsyncObjectDetector(context)
    private val poseDetector = AsyncPoseDetector(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = AtomicBoolean(false)
    private var lastFrameAt = 0L

    fun submit(bitmap: Bitmap) {
        if (!pending.compareAndSet(false, true)) { bitmap.recycle(); return }
        scope.launch {
            try { analyze(bitmap) } finally { bitmap.recycle(); pending.set(false) }
        }
    }

    private suspend fun analyze(bitmap: Bitmap) {
        val started = SystemClock.elapsedRealtime()
        val luma = IntArray(32 * 24) { index ->
            val x = index % 32 * (bitmap.width - 1) / 31
            val y = index / 32 * (bitmap.height - 1) / 23
            val pixel = bitmap.getPixel(x, y)
            ((pixel shr 16 and 255) * 3 + (pixel shr 8 and 255) * 6 + (pixel and 255)) / 10
        }
        val result = engine.analyze(luma)
        if (objectDetector.isDue()) objectDetector.submit(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        if (poseDetector.isDue()) poseDetector.submit(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        val objects = objectDetector.current()
        val pose = poseDetector.current()
        val aspect = bitmap.width.toFloat() / bitmap.height
        val map: (Offset) -> Offset = { mapVideoFit(it, aspect) }
        val directionScale = if (aspect > viewportAspect) Offset(1f, viewportAspect / aspect) else Offset(aspect / viewportAspect, 1f)
        val bounds = result.bounds?.let { Rect(map(it.topLeft), map(it.bottomRight)) }
        val now = SystemClock.elapsedRealtime()
        val measuredFps = if (lastFrameAt == 0L) 0 else (1_000L / (now - lastFrameAt).coerceAtLeast(1L)).toInt()
        lastFrameAt = now
        val status = when {
            result.cameraShift -> "Camera view changed"
            result.contrast < .04f -> "Low contrast · details may be hard to see"
            result.motion > .22f -> "A lot of movement in view"
            result.activityPresent && result.direction == "STILL" -> "Recent movement tracked"
            result.activityPresent -> "Activity tracked · ${result.direction.lowercase()}"
            else -> "Quiet scene"
        }
        withContext(Dispatchers.Main) {
            onResult(VisionState(fps = measuredFps, motion = result.motion, motionArea = result.motionArea,
                brightness = result.brightness, sceneContrast = result.contrast, stability = result.stability,
                directionX = result.directionX * directionScale.x, directionY = result.directionY * directionScale.y,
                direction = result.direction, movementSpeed = result.movementSpeed,
                activityPresent = result.activityPresent, status = status, subjectCenter = result.center?.let(map),
                motionBounds = bounds, heatPoints = result.heatPoints.map { it.copy(position = map(it.position)) },
                detectedItems = objects.map { item -> item.copy(bounds = Rect(map(item.bounds.topLeft), map(item.bounds.bottomRight))) },
                poseJoints = pose.map { it.copy(position = map(it.position)) },
                cameraShift = result.cameraShift, processingMs = SystemClock.elapsedRealtime() - started,
                source = "VIDEO", lastMovementAt = result.lastMovementAt))
        }
    }

    private fun mapVideoFit(point: Offset, videoAspect: Float): Offset = if (videoAspect > viewportAspect) {
        val shownHeight = viewportAspect / videoAspect
        Offset(point.x, (1f - shownHeight) / 2f + point.y * shownHeight)
    } else {
        val shownWidth = videoAspect / viewportAspect
        Offset((1f - shownWidth) / 2f + point.x * shownWidth, point.y)
    }

    fun close() { scope.cancel(); objectDetector.close(); poseDetector.close() }
}

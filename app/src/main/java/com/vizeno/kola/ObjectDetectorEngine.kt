package com.vizeno.kola

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class RawDetection(val label: String, val confidence: Float, val bounds: Rect)

/** Lightweight temporal association: labels initial-scene objects as baseline and later tracks as introduced. */
class ObjectTracker(private val startedAt: Long = System.currentTimeMillis()) {
    private data class Track(var bounds: Rect, val firstSeen: Long, var lastSeen: Long, val baseline: Boolean)
    private val tracks = mutableMapOf<String, MutableList<Track>>()

    fun update(detections: List<RawDetection>, now: Long): List<DetectedItem> {
        tracks.values.flatten().filter { now - it.lastSeen > 2_500 }.forEach { stale ->
            tracks.values.forEach { it.remove(stale) }
        }
        return detections.map { detection ->
            val candidates = tracks.getOrPut(detection.label) { mutableListOf() }
            val match = candidates.filter { now - it.lastSeen < 2_500 }.maxByOrNull { iou(it.bounds, detection.bounds) }
                ?.takeIf { iou(it.bounds, detection.bounds) >= .18f }
            val track = match?.apply { bounds = detection.bounds; lastSeen = now } ?: Track(
                detection.bounds, now, now, now - startedAt <= 2_500L
            ).also(candidates::add)
            val risk = riskFor(detection.label)
            DetectedItem(detection.label, detection.confidence, detection.bounds, risk,
                newlyIntroduced = !track.baseline, persistenceMs = now - track.firstSeen)
        }
    }

    private fun iou(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left); val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right); val bottom = minOf(a.bottom, b.bottom)
        val intersection = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    companion object {
        private val danger = setOf("knife", "scissors")
        private val review = setOf("fork", "spoon", "bottle", "wine glass", "cup", "sports ball", "cell phone", "remote", "book", "teddy bear", "backpack", "handbag", "umbrella", "tie")
        fun riskFor(label: String) = when (label.lowercase()) {
            in danger -> "DANGER"
            in review -> "REVIEW"
            else -> "NONE"
        }
    }
}

class ObjectDetectorEngine(context: Context) : AutoCloseable {
    private val tracker = ObjectTracker()
    private val detector = ObjectDetector.createFromFileAndOptions(
        context,
        "efficientdet-lite0.tflite",
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setNumThreads(2).build())
            .setScoreThreshold(.34f)
            .setMaxResults(6)
            .build(),
    )
    @Volatile private var lastRunAt = 0L
    private var cached = emptyList<DetectedItem>()

    fun isDue(now: Long = System.currentTimeMillis()) = now - lastRunAt >= 700L
    fun current() = cached

    fun detectIfDue(bitmap: Bitmap, now: Long = System.currentTimeMillis()): List<DetectedItem> {
        if (now - lastRunAt < 700L) return cached
        val width = bitmap.width.toFloat(); val height = bitmap.height.toFloat()
        val raw = detector.detect(TensorImage.fromBitmap(bitmap)).mapNotNull { detection ->
            val category = detection.categories.maxByOrNull { it.score } ?: return@mapNotNull null
            val box = detection.boundingBox
            val displayLabel = category.label.ifBlank { "object" }.let { if (it.equals("person", true)) "baby" else it }
            RawDetection(displayLabel, category.score,
                Rect((box.left / width).coerceIn(0f, 1f), (box.top / height).coerceIn(0f, 1f),
                    (box.right / width).coerceIn(0f, 1f), (box.bottom / height).coerceIn(0f, 1f)))
        }
        val finishedAt = System.currentTimeMillis()
        cached = tracker.update(raw, finishedAt)
        lastRunAt = finishedAt
        return cached
    }

    override fun close() = detector.close()
}

/** Owns detector work and input bitmaps so motion analysis never waits for model inference. */
class AsyncObjectDetector(context: Context) : AutoCloseable {
    private val engine = ObjectDetectorEngine(context)
    private val worker = Executors.newSingleThreadExecutor()
    private val pending = AtomicBoolean(false)
    @Volatile private var latest = emptyList<DetectedItem>()

    fun isDue() = !pending.get() && engine.isDue()
    fun current() = latest
    fun submit(bitmap: Bitmap) {
        if (!isDue() || !pending.compareAndSet(false, true)) { bitmap.recycle(); return }
        worker.execute {
            try { latest = engine.detectIfDue(bitmap) }
            finally { bitmap.recycle(); pending.set(false) }
        }
    }

    override fun close() {
        worker.execute { engine.close() }
        worker.shutdown()
    }
}

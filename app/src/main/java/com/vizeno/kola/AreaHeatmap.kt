package com.vizeno.kola

import androidx.compose.ui.geometry.Offset

/** Slowly decaying movement density grid, independent of whether the scene is a crib or floor area. */
class AreaHeatmap(private val columns: Int = 16, private val rows: Int = 12) {
    private val cells = FloatArray(columns * rows)

    fun update(points: List<HeatPoint>): List<HeatPoint> {
        for (i in cells.indices) cells[i] *= .965f
        points.forEach { point ->
            val x = (point.position.x * columns).toInt().coerceIn(0, columns - 1)
            val y = (point.position.y * rows).toInt().coerceIn(0, rows - 1)
            val index = y * columns + x
            cells[index] = (cells[index] + point.intensity * .22f).coerceAtMost(1f)
        }
        return cells.indices.filter { cells[it] > .055f }.map { index ->
            HeatPoint(Offset((index % columns + .5f) / columns, (index / columns + .5f) / rows), cells[index])
        }
    }
}

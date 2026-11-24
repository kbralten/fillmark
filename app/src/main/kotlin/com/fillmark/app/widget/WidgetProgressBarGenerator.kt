package com.fillmark.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave

object WidgetProgressBarGenerator {

    /**
     * Creates a custom progress bar Bitmap matching the modern luminous cyan pill design
     * with an orange/coral glowing pacing marker line and dot.
     */
    fun createProgressBarBitmap(
        context: Context,
        progress: Float,
        paceRatio: Float?,
        widthDp: Int = 280,
        heightDp: Int = 20,
        isDark: Boolean = true
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt().coerceAtLeast(100)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(16)
        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)

        val barHeight = heightPx * 0.85f
        val barTop = (heightPx - barHeight) / 2f
        val cornerRadius = barHeight / 2f

        // 1. Background Track
        val trackPath = Path().apply {
            addRoundRect(
                RectF(0f, barTop, widthPx.toFloat(), barTop + barHeight),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (if (isDark) "#0C2438" else "#DCE8F2").toColorInt()
        }
        canvas.drawPath(trackPath, trackPaint)

        val safeProgress = if (progress.isNaN()) 0f else progress

        // 2. Base Progress Fill & Overflow inside track bounds
        val baseFillWidth = (widthPx * safeProgress.coerceIn(0f, 1f)).coerceAtLeast(0f)
        if (baseFillWidth > 0f) {
            canvas.withSave {
                clipPath(trackPath)

                // Outer cyan fill gradient
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, barTop,
                        baseFillWidth, barTop + barHeight,
                        "#34F3FF".toColorInt(),
                        "#0EB5C4".toColorInt(),
                        Shader.TileMode.CLAMP
                    )
                }
                drawRoundRect(
                    RectF(0f, barTop, baseFillWidth, barTop + barHeight),
                    cornerRadius,
                    cornerRadius,
                    fillPaint
                )

                // Inner luminous glow / highlight pill
                if (baseFillWidth > 12 * density) {
                    val insetY = 2.5f * density
                    val insetX = 3f * density
                    val innerHeight = barHeight - (insetY * 2)
                    val innerWidth = (baseFillWidth - (insetX * 2)).coerceAtLeast(4f)
                    val innerRadius = innerHeight / 2f

                    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = "#66FFFFFF".toColorInt()
                    }
                    drawRoundRect(
                        RectF(insetX, barTop + insetY, insetX + innerWidth, barTop + insetY + innerHeight),
                        innerRadius,
                        innerRadius,
                        highlightPaint
                    )
                }

                // 3. Overflow Fill for > 100% consumption
                if (safeProgress > 1f) {
                    val overflowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            0f, barTop,
                            widthPx.toFloat(), barTop + barHeight,
                            "#64FFDA".toColorInt(),
                            "#00E5FF".toColorInt(),
                            Shader.TileMode.CLAMP
                        )
                    }
                    val overflowWidth = (widthPx * (safeProgress - 1f).coerceIn(0f, 1f)).coerceAtLeast(0f)
                    if (overflowWidth > 0) {
                        drawRoundRect(
                            RectF(0f, barTop, overflowWidth, barTop + barHeight),
                            cornerRadius,
                            cornerRadius,
                            overflowPaint
                        )
                    }
                }
            }
        }

        // 4. Glowing Pacing Marker (Vertical line + glowing orange dot)
        if (paceRatio != null) {
            val markerX = (widthPx * paceRatio.coerceIn(0.04f, 0.96f))
            val centerY = heightPx / 2f

            // Soft vertical glowing line
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#CCFF7A50".toColorInt()
                strokeWidth = 2.5f * density
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(markerX, barTop - (1.5f * density), markerX, barTop + barHeight + (1.5f * density), linePaint)

            // Outer glowing halo
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#55FF7A50".toColorInt()
            }
            canvas.drawCircle(markerX, centerY, 6f * density, haloPaint)

            // Center orange dot
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = "#FFFF7A50".toColorInt()
            }
            canvas.drawCircle(markerX, centerY, 3.5f * density, dotPaint)

            // Core highlight
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            canvas.drawCircle(markerX, centerY, 1.5f * density, corePaint)
        }

        return bitmap
    }
}

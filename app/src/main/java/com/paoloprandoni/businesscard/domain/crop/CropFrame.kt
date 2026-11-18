package com.paoloprandoni.businesscard.domain.crop

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Punto in coordinate immagine (pixel). */
data class Point2(val x: Float, val y: Float)

/**
 * Rettangolo di ritaglio ruotabile, espresso in pixel dell'immagine sorgente.
 *
 * [angleDeg] è la rotazione in senso orario del rettangolo: a 0° i lati sono paralleli agli assi
 * dell'immagine. Serve a ritagliare dritto un logo su un biglietto fotografato storto.
 *
 * Tutta la matematica è pura Kotlin (nessun tipo Android) così è verificabile con test JVM.
 */
data class CropFrame(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angleDeg: Float = 0f,
) {

    /** Asse lungo la larghezza. */
    val ux: Float get() = cos(Math.toRadians(angleDeg.toDouble())).toFloat()
    val uy: Float get() = sin(Math.toRadians(angleDeg.toDouble())).toFloat()

    /** Asse lungo l'altezza (perpendicolare a [ux]/[uy]). */
    val vx: Float get() = -uy
    val vy: Float get() = ux

    /** Angolo del rettangolo normalizzato in (-180, 180]. */
    val normalizedAngle: Float get() = normalizeAngle(angleDeg)

    /** Angolo del rettangolo in coordinate canonicali (-90, 90], utile per la UI. */
    val displayAngle: Float
        get() {
            val a = normalizedAngle
            return if (a > 90f) a - 180f else if (a <= -90f) a + 180f else a
        }

    /** Vertice del rettangolo: [signX]/[signY] valgono +1 o -1 (angolo in alto a sinistra = -1,-1). */
    fun corner(signX: Int, signY: Int): Point2 {
        val halfW = width / 2f
        val halfH = height / 2f
        return Point2(
            centerX + ux * halfW * signX + vx * halfH * signY,
            centerY + uy * halfW * signX + vy * halfH * signY,
        )
    }

    /** Vertici in ordine orario a partire dall'alto a sinistra. */
    fun corners(): List<Point2> = listOf(
        corner(-1, -1),
        corner(1, -1),
        corner(1, 1),
        corner(-1, 1),
    )

    fun contains(point: Point2): Boolean {
        val dx = point.x - centerX
        val dy = point.y - centerY
        val alongU = dx * ux + dy * uy
        val alongV = dx * vx + dy * vy
        return abs(alongU) <= width / 2f && abs(alongV) <= height / 2f
    }

    fun distanceToCorner(point: Point2, signX: Int, signY: Int): Float {
        val c = corner(signX, signY)
        return distance(point, c)
    }

    /** Maniglia di rotazione: sopra il lato superiore, a [distance] pixel dal bordo. */
    fun rotationHandle(distance: Float): Point2 = Point2(
        centerX - vx * (height / 2f + distance),
        centerY - vy * (height / 2f + distance),
    )

    /** Punto medio del lato superiore. */
    fun topMidpoint(): Point2 = Point2(
        centerX - vx * (height / 2f),
        centerY - vy * (height / 2f),
    )

    fun moved(dx: Float, dy: Float): CropFrame = copy(centerX = centerX + dx, centerY = centerY + dy)

    fun withAngle(angle: Float): CropFrame = copy(angleDeg = normalizeAngle(angle))

    /** Angolo (gradi, orario) che porta la maniglia di rotazione verso [point]. */
    fun angleTowards(point: Point2): Float {
        val deg = Math.toDegrees(
            atan2((point.y - centerY).toDouble(), (point.x - centerX).toDouble()),
        ).toFloat()
        return normalizeAngle(deg + 90f)
    }

    /**
     * Aggancia l'angolo a [targets] quando ne è vicino entro [tolerance] gradi
     * (per ottenere facilmente 0°, 45° e 90° precisi).
     */
    fun snappedAngle(
        tolerance: Float,
        targets: List<Float> = DEFAULT_SNAP_ANGLES,
    ): CropFrame {
        val current = normalizedAngle
        val nearest = targets.minByOrNull { abs(it - current) }
        return if (nearest != null && abs(nearest - current) <= tolerance) {
            withAngle(nearest)
        } else {
            this
        }
    }

    /**
     * Ridimensiona trascinando l'angolo individuato da [signX]/[signY]: l'angolo opposto resta
     * fermo, quindi il rettangolo cresce verso il puntatore mantenendo la rotazione corrente.
     */
    fun resized(
        signX: Int,
        signY: Int,
        pointer: Point2,
        minSide: Float,
        keepSquare: Boolean = false,
    ): CropFrame {
        val fixed = corner(-signX, -signY)
        val dx = pointer.x - fixed.x
        val dy = pointer.y - fixed.y
        val wantedWidth = (dx * ux + dy * uy) * signX
        val wantedHeight = (dx * vx + dy * vy) * signY

        var newWidth = wantedWidth.coerceAtLeast(minSide)
        var newHeight = wantedHeight.coerceAtLeast(minSide)
        if (keepSquare) {
            val side = maxOf(newWidth, newHeight)
            newWidth = side
            newHeight = side
        }

        return copy(
            centerX = fixed.x + ux * (newWidth / 2f) * signX + vx * (newHeight / 2f) * signY,
            centerY = fixed.y + uy * (newWidth / 2f) * signX + vy * (newHeight / 2f) * signY,
            width = newWidth,
            height = newHeight,
        )
    }

    /** Semi-ingombro orizzontale e verticale del rettangolo ruotato. */
    fun halfExtents(): Pair<Float, Float> {
        val halfW = width / 2f
        val halfH = height / 2f
        val halfX = abs(ux) * halfW + abs(vx) * halfH
        val halfY = abs(uy) * halfW + abs(vy) * halfH
        return halfX to halfY
    }

    /** Rimpicciolisce e riposiziona il rettangolo perché resti interamente dentro l'immagine. */
    fun clamped(imageWidth: Float, imageHeight: Float): CropFrame {
        if (imageWidth <= 0f || imageHeight <= 0f) return this
        var frame = this
        var (halfX, halfY) = frame.halfExtents()
        if (halfX <= 0f || halfY <= 0f) return frame

        val limitX = imageWidth / 2f
        val limitY = imageHeight / 2f
        val factor = min(1f, min(limitX / halfX, limitY / halfY))
        if (factor < 1f) {
            frame = frame.copy(width = frame.width * factor, height = frame.height * factor)
            val extents = frame.halfExtents()
            halfX = extents.first
            halfY = extents.second
        }

        val centerX = frame.centerX.coerceIn(halfX, (imageWidth - halfX).coerceAtLeast(halfX))
        val centerY = frame.centerY.coerceIn(halfY, (imageHeight - halfY).coerceAtLeast(halfY))
        return frame.copy(centerX = centerX, centerY = centerY)
    }

    /** Restituisce lo stesso centro con lati uguali (per il vincolo 1:1). */
    fun squared(): CropFrame {
        val side = min(width, height)
        return copy(width = side, height = side)
    }

    companion object {
        val DEFAULT_SNAP_ANGLES = listOf(-180f, -135f, -90f, -45f, 0f, 45f, 90f, 135f, 180f)

        private fun normalizeAngle(angle: Float): Float {
            var value = angle % 360f
            if (value > 180f) value -= 360f
            if (value <= -180f) value += 360f
            return value
        }
    }
}

fun distance(a: Point2, b: Point2): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

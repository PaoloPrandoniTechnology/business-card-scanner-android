package com.paoloprandoni.businesscard.ui.crop

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paoloprandoni.businesscard.data.image.ImageProcessor
import com.paoloprandoni.businesscard.domain.crop.CropFrame
import com.paoloprandoni.businesscard.domain.crop.Point2
import com.paoloprandoni.businesscard.domain.crop.distance
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_CROP_PIXELS = 32f
private const val HANDLE_TOUCH_RADIUS = 56f
private const val ANGLE_SNAP_TOLERANCE = 2f
private const val ROTATION_HANDLE_DP = 36f
private const val SLIDER_MAX_ANGLE = 90f

/** Forma proposta all'apertura quando non c'è un suggerimento. */
enum class CropStartShape {
    /** Quadrato piccolo in alto a sinistra (logo). */
    SQUARE,

    /** Rettangolo centrato con proporzioni da biglietto da visita (area di scansione). */
    CARD,
}

private sealed interface DragMode {
    data object None : DragMode
    data object Move : DragMode
    data class Corner(val signX: Int, val signY: Int) : DragMode
    data class Rotate(val grabOffset: Float) : DragMode
}

private data class Fit(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toImage(offset: Offset): Point2 =
        Point2((offset.x - offsetX) / scale, (offset.y - offsetY) / scale)

    fun toCanvas(point: Point2): Offset =
        Offset(offsetX + point.x * scale, offsetY + point.y * scale)
}

/**
 * Editor di ritaglio riutilizzabile: immagine su Canvas con rettangolo ridimensionabile dalle
 * maniglie, trascinabile, ruotabile (maniglia in alto o slider) e vincolo opzionale 1:1.
 *
 * Usato sia per il logo del contatto sia per scegliere l'area di scansione (ROI) da passare
 * all'OCR quando si importa un'immagine dalla galleria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropEditorScreen(
    imagePath: String,
    title: String,
    confirmLabel: String,
    imageProcessor: ImageProcessor,
    initialNormalized: RectF? = null,
    startShape: CropStartShape = CropStartShape.SQUARE,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = imageProcessor.decode(File(imagePath), ImageProcessor.MAX_DECODE_DIMENSION)
    }

    var frame by remember(bitmap) { mutableStateOf<CropFrame?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragMode by remember { mutableStateOf<DragMode>(DragMode.None) }
    var squareLocked by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary
    val rotationHandlePx = with(LocalDensity.current) { ROTATION_HANDLE_DP.dp.toPx() }

    LaunchedEffect(bitmap) {
        val source = bitmap ?: return@LaunchedEffect
        val imageWidth = source.width.toFloat()
        val imageHeight = source.height.toFloat()
        val initial = initialNormalized?.let { hint ->
            CropFrame(
                centerX = (hint.left + hint.right) / 2f * imageWidth,
                centerY = (hint.top + hint.bottom) / 2f * imageHeight,
                width = (hint.right - hint.left) * imageWidth,
                height = (hint.bottom - hint.top) * imageHeight,
            )
        } ?: when (startShape) {
            CropStartShape.SQUARE -> {
                val side = min(imageWidth, imageHeight) * 0.34f
                CropFrame(
                    centerX = imageWidth * 0.23f,
                    centerY = imageHeight * 0.23f,
                    width = side,
                    height = side,
                )
            }
            CropStartShape.CARD -> cardFrame(imageWidth, imageHeight)
        }
        frame = initial.clamped(imageWidth, imageHeight)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Annulla")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !squareLocked,
                            onClick = { squareLocked = false },
                            label = { Text("Libero") },
                        )
                        FilterChip(
                            selected = squareLocked,
                            onClick = {
                                squareLocked = true
                                val source = bitmap
                                if (source != null) {
                                    frame = frame
                                        ?.squared()
                                        ?.clamped(source.width.toFloat(), source.height.toFloat())
                                }
                            },
                            label = { Text("Quadrato 1:1") },
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Text(
                            text = formatAngle(frame?.displayAngle ?: 0f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.RotateLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Slider(
                            value = (frame?.displayAngle ?: 0f)
                                .coerceIn(-SLIDER_MAX_ANGLE, SLIDER_MAX_ANGLE),
                            onValueChange = { value ->
                                val source = bitmap
                                if (source != null) {
                                    frame = frame
                                        ?.withAngle(snapSliderAngle(value))
                                        ?.clamped(source.width.toFloat(), source.height.toFloat())
                                }
                            },
                            valueRange = -SLIDER_MAX_ANGLE..SLIDER_MAX_ANGLE,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val source = bitmap
                                if (source != null) {
                                    frame = frame
                                        ?.withAngle(0f)
                                        ?.clamped(source.width.toFloat(), source.height.toFloat())
                                }
                            },
                            enabled = (frame?.displayAngle ?: 0f) != 0f,
                        ) { Text("0°") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Annulla") }
                        Button(
                            onClick = {
                                val source = bitmap
                                val selected = frame
                                if (source != null && selected != null && !working) {
                                    scope.launch {
                                        working = true
                                        val cropped = withContext(Dispatchers.Default) {
                                            imageProcessor.cropFrame(source, selected)
                                        }
                                        working = false
                                        if (cropped != null) onConfirm(cropped)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = bitmap != null && frame != null && !working,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (working) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(confirmLabel)
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            val source = bitmap
            if (source == null) {
                CircularProgressIndicator()
                return@Box
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(source, canvasSize) {
                        if (canvasSize.width == 0 || canvasSize.height == 0) return@pointerInput
                        val fit = fitFor(canvasSize.width, canvasSize.height, source)
                        detectDragGestures(
                            onDragStart = { position ->
                                val current = frame
                                if (current == null) {
                                    dragMode = DragMode.None
                                } else {
                                    val point = fit.toImage(position)
                                    val handle = fit.toImage(
                                        rotationHandleCanvas(
                                            current,
                                            fit,
                                            rotationHandlePx,
                                            canvasSize.width.toFloat(),
                                            canvasSize.height.toFloat(),
                                        ),
                                    )
                                    val radius = HANDLE_TOUCH_RADIUS / fit.scale
                                    dragMode = when {
                                        distance(point, handle) <= radius ->
                                            DragMode.Rotate(current.angleDeg - current.angleTowards(point))
                                        distance(point, current.corner(-1, -1)) <= radius ->
                                            DragMode.Corner(-1, -1)
                                        distance(point, current.corner(1, -1)) <= radius ->
                                            DragMode.Corner(1, -1)
                                        distance(point, current.corner(1, 1)) <= radius ->
                                            DragMode.Corner(1, 1)
                                        distance(point, current.corner(-1, 1)) <= radius ->
                                            DragMode.Corner(-1, 1)
                                        current.contains(point) -> DragMode.Move
                                        else -> DragMode.None
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val current = frame
                                if (current != null) {
                                    val point = fit.toImage(change.position)
                                    val updated = when (val mode = dragMode) {
                                        DragMode.None -> current
                                        DragMode.Move -> current.moved(
                                            dragAmount.x / fit.scale,
                                            dragAmount.y / fit.scale,
                                        )
                                        is DragMode.Corner -> current.resized(
                                            signX = mode.signX,
                                            signY = mode.signY,
                                            pointer = point,
                                            minSide = MIN_CROP_PIXELS,
                                            keepSquare = squareLocked,
                                        )
                                        is DragMode.Rotate -> current
                                            .withAngle(current.angleTowards(point) + mode.grabOffset)
                                            .snappedAngle(ANGLE_SNAP_TOLERANCE)
                                    }
                                    frame = updated.clamped(
                                        source.width.toFloat(),
                                        source.height.toFloat(),
                                    )
                                }
                            },
                            onDragEnd = { dragMode = DragMode.None },
                            onDragCancel = { dragMode = DragMode.None },
                        )
                    },
            ) {
                val fit = fitFor(size.width.roundToInt(), size.height.roundToInt(), source)
                val drawWidth = source.width * fit.scale
                val drawHeight = source.height * fit.scale
                drawImage(
                    image = source.asImageBitmap(),
                    dstOffset = IntOffset(fit.offsetX.roundToInt(), fit.offsetY.roundToInt()),
                    dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
                )

                val current = frame ?: return@Canvas
                val corners = current.corners().map { fit.toCanvas(it) }
                val quad = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    lineTo(corners[1].x, corners[1].y)
                    lineTo(corners[2].x, corners[2].y)
                    lineTo(corners[3].x, corners[3].y)
                    close()
                }
                val scrim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(
                        Rect(
                            fit.offsetX,
                            fit.offsetY,
                            fit.offsetX + drawWidth,
                            fit.offsetY + drawHeight,
                        ),
                    )
                    addPath(quad)
                }
                drawPath(scrim, Color.Black.copy(alpha = 0.55f))
                drawPath(quad, Color.White, style = Stroke(width = 2.dp.toPx()))

                // griglia dei terzi, segue la rotazione del rettangolo
                for (i in 1..2) {
                    val t = i / 3f
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = lerp(corners[0], corners[3], t),
                        end = lerp(corners[1], corners[2], t),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = lerp(corners[0], corners[1], t),
                        end = lerp(corners[3], corners[2], t),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val topMid = lerp(corners[0], corners[1], 0.5f)
                val handle = rotationHandleCanvas(
                    current,
                    fit,
                    rotationHandlePx,
                    size.width,
                    size.height,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = topMid,
                    end = handle,
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(Color.Black.copy(alpha = 0.35f), 12.dp.toPx(), handle)
                drawCircle(Color.White, 10.dp.toPx(), handle)
                drawCircle(accentColor, 7.dp.toPx(), handle)

                corners.forEach { corner ->
                    drawCircle(color = Color.White, radius = 9.dp.toPx(), center = corner)
                    drawCircle(color = accentColor, radius = 6.dp.toPx(), center = corner)
                }
            }

            Text(
                text = "Maniglia in alto: ruota\nAngoli: ridimensiona",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/** Rettangolo centrato con proporzioni da biglietto (1.65:1) al 92% della larghezza. */
private fun cardFrame(imageWidth: Float, imageHeight: Float): CropFrame {
    var width = imageWidth * 0.92f
    var height = width / 1.65f
    val maxHeight = imageHeight * 0.92f
    if (height > maxHeight) {
        height = maxHeight
        width = height * 1.65f
    }
    return CropFrame(
        centerX = imageWidth / 2f,
        centerY = imageHeight / 2f,
        width = width,
        height = height,
    )
}

/** Posizione della maniglia di rotazione in coordinate canvas, tenuta dentro l'area visibile. */
private fun rotationHandleCanvas(
    frame: CropFrame,
    fit: Fit,
    distancePx: Float,
    canvasWidth: Float,
    canvasHeight: Float,
): Offset {
    val topMid = fit.toCanvas(frame.topMidpoint())
    val outward = Offset(-frame.vx, -frame.vy)
    val margin = distancePx + 4f
    return Offset(
        (topMid.x + outward.x * distancePx).coerceIn(margin, (canvasWidth - margin).coerceAtLeast(margin)),
        (topMid.y + outward.y * distancePx).coerceIn(margin, (canvasHeight - margin).coerceAtLeast(margin)),
    )
}

private fun fitFor(canvasWidth: Int, canvasHeight: Int, bitmap: Bitmap): Fit {
    if (canvasWidth <= 0 || canvasHeight <= 0) return Fit(1f, 0f, 0f)
    val scale = min(
        canvasWidth.toFloat() / bitmap.width,
        canvasHeight.toFloat() / bitmap.height,
    )
    return Fit(
        scale = scale,
        offsetX = (canvasWidth - bitmap.width * scale) / 2f,
        offsetY = (canvasHeight - bitmap.height * scale) / 2f,
    )
}

private fun snapSliderAngle(value: Float): Float {
    val targets = listOf(-90f, -45f, 0f, 45f, 90f)
    val nearest = targets.minByOrNull { abs(it - value) }
    return if (nearest != null && abs(nearest - value) <= 1.2f) {
        nearest
    } else {
        (value * 2f).roundToInt() / 2f
    }
}

private fun formatAngle(angle: Float): String {
    val rounded = (angle * 10f).roundToInt() / 10f
    val decimals = if (abs(rounded - rounded.roundToInt()) < 0.05f) 0 else 1
    return String.format(Locale.ITALY, "%.${decimals}f°", rounded)
}

package com.paoloprandoni.businesscard.ui.crop

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paoloprandoni.businesscard.data.image.ImageProcessor
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectDragGestures

private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private data class Fit(val scale: Float, val offsetX: Float, val offsetY: Float)

private const val MIN_CROP_PIXELS = 32f

/**
 * Ritaglio interattivo del logo: immagine su Canvas con box ridimensionabile tramite le maniglie
 * angolari, trascinamento dell'area selezionata e vincolo opzionale 1:1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoCropScreen(
    imagePath: String,
    initialRect: RectF?,
    imageProcessor: ImageProcessor,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = imageProcessor.decode(File(imagePath), 2400)
    }

    var selection by remember(bitmap) { mutableStateOf<RectF?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var squareLocked by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(bitmap) {
        val source = bitmap ?: return@LaunchedEffect
        val initial = initialRect?.let { hint ->
            RectF(
                hint.left * source.width,
                hint.top * source.height,
                hint.right * source.width,
                hint.bottom * source.height,
            )
        } ?: RectF(
            source.width * 0.06f,
            source.height * 0.06f,
            source.width * 0.40f,
            source.height * 0.40f,
        )
        selection = clampToBounds(initial, source.width, source.height)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ritaglia logo") },
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                val current = selection
                                if (source != null && current != null) {
                                    selection = makeSquare(current, source.width, source.height)
                                }
                            },
                            label = { Text("Quadrato 1:1") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Annulla") }
                        Button(
                            onClick = {
                                val source = bitmap
                                val selected = selection
                                if (source != null && selected != null && !working) {
                                    scope.launch {
                                        working = true
                                        val normalized = RectF(
                                            (selected.left / source.width).coerceIn(0f, 1f),
                                            (selected.top / source.height).coerceIn(0f, 1f),
                                            (selected.right / source.width).coerceIn(0f, 1f),
                                            (selected.bottom / source.height).coerceIn(0f, 1f),
                                        )
                                        val cropped = withContext(Dispatchers.Default) {
                                            imageProcessor.cropNormalized(source, normalized)
                                        }
                                        working = false
                                        if (cropped != null) onConfirm(cropped)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = bitmap != null && selection != null && !working,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (working) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Ritaglia")
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
                    .pointerInput(source, canvasSize, squareLocked) {
                        if (canvasSize.width == 0 || canvasSize.height == 0) return@pointerInput
                        val fit = fitFor(canvasSize.width, canvasSize.height, source)
                        detectDragGestures(
                            onDragStart = { position ->
                                val selected = selection
                                if (selected == null) {
                                    dragMode = DragMode.NONE
                                } else {
                                    val imagePoint = Offset(
                                        (position.x - fit.offsetX) / fit.scale,
                                        (position.y - fit.offsetY) / fit.scale,
                                    )
                                    dragMode = hitTest(imagePoint, selected, HANDLE_TOUCH_RADIUS / fit.scale)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val selected = selection
                                if (selected != null && dragMode != DragMode.NONE) {
                                    val delta = Offset(
                                        dragAmount.x / fit.scale,
                                        dragAmount.y / fit.scale,
                                    )
                                    selection = if (dragMode == DragMode.MOVE) {
                                        moveRect(selected, delta.x, delta.y, source.width, source.height)
                                    } else {
                                        resizeRect(
                                            selected,
                                            dragMode,
                                            delta.x,
                                            delta.y,
                                            source.width,
                                            source.height,
                                            squareLocked,
                                        )
                                    }
                                }
                            },
                            onDragEnd = { dragMode = DragMode.NONE },
                            onDragCancel = { dragMode = DragMode.NONE },
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

                val selected = selection ?: return@Canvas
                val left = fit.offsetX + selected.left * fit.scale
                val top = fit.offsetY + selected.top * fit.scale
                val right = fit.offsetX + selected.right * fit.scale
                val bottom = fit.offsetY + selected.bottom * fit.scale
                val scrim = Color.Black.copy(alpha = 0.55f)

                drawRect(scrim, topLeft = Offset(fit.offsetX, fit.offsetY), size = Size(drawWidth, top - fit.offsetY))
                drawRect(scrim, topLeft = Offset(fit.offsetX, bottom), size = Size(drawWidth, fit.offsetY + drawHeight - bottom))
                drawRect(scrim, topLeft = Offset(fit.offsetX, top), size = Size(left - fit.offsetX, bottom - top))
                drawRect(scrim, topLeft = Offset(right, top), size = Size(fit.offsetX + drawWidth - right, bottom - top))

                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 2.dp.toPx()),
                )

                // griglia dei terzi
                val cellWidth = (right - left) / 3f
                val cellHeight = (bottom - top) / 3f
                for (i in 1..2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(left + cellWidth * i, top),
                        end = Offset(left + cellWidth * i, bottom),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(left, top + cellHeight * i),
                        end = Offset(right, top + cellHeight * i),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val accent = accentColor
                listOf(
                    Offset(left, top),
                    Offset(right, top),
                    Offset(left, bottom),
                    Offset(right, bottom),
                ).forEach { corner ->
                    drawCircle(color = Color.White, radius = 9.dp.toPx(), center = corner)
                    drawCircle(color = accent, radius = 6.dp.toPx(), center = corner)
                }
            }

            Text(
                text = "Trascina le maniglie per ritagliare",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
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

private fun hitTest(point: Offset, rect: RectF, radius: Float): DragMode {
    fun near(x: Float, y: Float): Boolean =
        abs(point.x - x) <= radius && abs(point.y - y) <= radius

    return when {
        near(rect.left, rect.top) -> DragMode.TOP_LEFT
        near(rect.right, rect.top) -> DragMode.TOP_RIGHT
        near(rect.left, rect.bottom) -> DragMode.BOTTOM_LEFT
        near(rect.right, rect.bottom) -> DragMode.BOTTOM_RIGHT
        rect.contains(point.x, point.y) -> DragMode.MOVE
        else -> DragMode.NONE
    }
}

private fun moveRect(rect: RectF, dx: Float, dy: Float, width: Int, height: Int): RectF {
    val rectWidth = rect.width()
    val rectHeight = rect.height()
    val left = (rect.left + dx).coerceIn(0f, (width - rectWidth).coerceAtLeast(0f))
    val top = (rect.top + dy).coerceIn(0f, (height - rectHeight).coerceAtLeast(0f))
    return RectF(left, top, left + rectWidth, top + rectHeight)
}

private fun resizeRect(
    rect: RectF,
    mode: DragMode,
    dx: Float,
    dy: Float,
    width: Int,
    height: Int,
    square: Boolean,
): RectF {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom

    when (mode) {
        DragMode.TOP_LEFT -> {
            left = (left + dx).coerceIn(0f, right - MIN_CROP_PIXELS)
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP_PIXELS)
        }
        DragMode.TOP_RIGHT -> {
            right = (right + dx).coerceIn(left + MIN_CROP_PIXELS, width.toFloat())
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP_PIXELS)
        }
        DragMode.BOTTOM_LEFT -> {
            left = (left + dx).coerceIn(0f, right - MIN_CROP_PIXELS)
            bottom = (bottom + dy).coerceIn(top + MIN_CROP_PIXELS, height.toFloat())
        }
        DragMode.BOTTOM_RIGHT -> {
            right = (right + dx).coerceIn(left + MIN_CROP_PIXELS, width.toFloat())
            bottom = (bottom + dy).coerceIn(top + MIN_CROP_PIXELS, height.toFloat())
        }
        else -> Unit
    }

    if (square) {
        val side = maxOf(right - left, bottom - top)
            .coerceAtMost(min(width.toFloat(), height.toFloat()))
        when (mode) {
            DragMode.TOP_LEFT -> {
                left = right - side
                top = bottom - side
            }
            DragMode.TOP_RIGHT -> {
                right = left + side
                top = bottom - side
            }
            DragMode.BOTTOM_LEFT -> {
                left = right - side
                bottom = top + side
            }
            DragMode.BOTTOM_RIGHT -> {
                right = left + side
                bottom = top + side
            }
            else -> Unit
        }
        if (left < 0f) {
            right -= left
            left = 0f
        }
        if (top < 0f) {
            bottom -= top
            top = 0f
        }
        if (right > width) {
            left -= right - width
            right = width.toFloat()
        }
        if (bottom > height) {
            top -= bottom - height
            bottom = height.toFloat()
        }
        left = left.coerceAtLeast(0f)
        top = top.coerceAtLeast(0f)
    }

    return RectF(left, top, right, bottom)
}

private fun makeSquare(rect: RectF, width: Int, height: Int): RectF {
    val side = min(rect.width(), rect.height()).coerceAtLeast(MIN_CROP_PIXELS)
    var left = rect.left
    var top = rect.top
    if (left + side > width) left = (width - side).coerceAtLeast(0f)
    if (top + side > height) top = (height - side).coerceAtLeast(0f)
    return RectF(left, top, left + side, top + side)
}

private fun clampToBounds(rect: RectF, width: Int, height: Int): RectF {
    val left = rect.left.coerceIn(0f, (width - MIN_CROP_PIXELS).coerceAtLeast(0f))
    val top = rect.top.coerceIn(0f, (height - MIN_CROP_PIXELS).coerceAtLeast(0f))
    val right = rect.right.coerceIn(left + MIN_CROP_PIXELS, width.toFloat())
    val bottom = rect.bottom.coerceIn(top + MIN_CROP_PIXELS, height.toFloat())
    return RectF(left, top, right, bottom)
}

private const val HANDLE_TOUCH_RADIUS = 56f

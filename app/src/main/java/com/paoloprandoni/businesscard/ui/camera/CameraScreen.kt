package com.paoloprandoni.businesscard.ui.camera

import android.content.Context
import android.graphics.RectF
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.TorchState
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Anteprima CameraX con guida visiva per allineare il biglietto, tap-to-focus,
 * toggle flash e scatto ad alta qualità.
 */
@Composable
fun CameraScreen(
    /** File scattato, cornice guida in pixel della preview e dimensione della preview. */
    onPhotoCaptured: (File, RectF?, IntSize?) -> Unit,
    onClose: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lifecycleOwner) {
        try {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder().build().also { useCase ->
                useCase.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                .build()

            provider.unbindAll()
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
            cameraProvider = provider
            camera = boundCamera
            imageCapture = capture
            hasFlash = boundCamera.cameraInfo.hasFlashUnit()
            boundCamera.cameraInfo.torchState.observe(lifecycleOwner) { state ->
                torchOn = state == TorchState.ON
            }
        } catch (exception: Exception) {
            errorMessage = "Fotocamera non disponibile: ${exception.message ?: "errore sconosciuto"}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { camera?.cameraControl?.enableTorch(false) }
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (isCapturing) return
        isCapturing = true
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    val roi = if (overlaySize.width > 0 && overlaySize.height > 0) {
                        guideFrame(overlaySize.width.toFloat(), overlaySize.height.toFloat())
                    } else {
                        null
                    }
                    onPhotoCaptured(file, roi, overlaySize)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    errorMessage = "Scatto non riuscito: ${exception.message}"
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize = it }
                .pointerInput(imageCapture, hasFlash) {
                    detectTapGestures { position ->
                        runCatching {
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(position.x, position.y)
                            val action = FocusMeteringAction
                                .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                            focusPoint = position
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val guide = guideFrame(size.width, size.height)
                val frame = Rect(guide.left, guide.top, guide.right, guide.bottom)
                val scrim = Color.Black.copy(alpha = 0.42f)

                drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, frame.top))
                drawRect(
                    scrim,
                    topLeft = Offset(0f, frame.bottom),
                    size = Size(size.width, size.height - frame.bottom),
                )
                drawRect(
                    scrim,
                    topLeft = Offset(0f, frame.top),
                    size = Size(frame.left, frame.height),
                )
                drawRect(
                    scrim,
                    topLeft = Offset(frame.right, frame.top),
                    size = Size(size.width - frame.right, frame.height),
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = frame.topLeft,
                    size = frame.size,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )

                val accent = Color(0xFF5EEAD4)
                val stroke = 4.dp.toPx()
                val arm = 34.dp.toPx()
                listOf(
                    frame.topLeft to Offset(arm, 0f) to Offset(0f, arm),
                    Offset(frame.right, frame.top) to Offset(-arm, 0f) to Offset(0f, arm),
                    Offset(frame.left, frame.bottom) to Offset(arm, 0f) to Offset(0f, -arm),
                    frame.bottomRight to Offset(-arm, 0f) to Offset(0f, -arm),
                ).forEach { (cornerPair, horizontal) ->
                    val corner = cornerPair.first
                    val horizontalOffset = cornerPair.second
                    drawLine(
                        color = accent,
                        start = corner,
                        end = corner + horizontalOffset,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accent,
                        start = corner,
                        end = corner + horizontal,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }

                focusPoint?.let { point ->
                    drawCircle(
                        color = accent,
                        radius = 26.dp.toPx(),
                        center = point,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Chiudi", tint = Color.White)
            }
            Text(
                text = "Scansiona biglietto",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Row {
                IconButton(
                    onClick = {
                        val control = camera?.cameraControl ?: return@IconButton
                        if (hasFlash) {
                            control.enableTorch(!torchOn)
                        }
                    },
                    enabled = hasFlash,
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = "Flash",
                        tint = if (hasFlash) Color.White else Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Allinea il biglietto nella cornice",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenGallery) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Galleria", tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable(enabled = !isCapturing) { takePhoto() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(52.dp)
                                .background(Color.White, CircleShape),
                        )
                    }
                }

                Box(Modifier.size(48.dp))
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Fotocamera") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    errorMessage = null
                    onClose()
                }) { Text("Chiudi") }
            },
        )
    }
}

/**
 * Cornice guida del biglietto in pixel della preview: è la stessa area che viene passata all'OCR
 * dopo lo scatto, così il testo fuori dal riquadro non entra nei dati estratti.
 */
private fun guideFrame(width: Float, height: Float): RectF {
    val margin = width * 0.07f
    val frameWidth = width - margin * 2f
    val frameHeight = frameWidth / 1.65f
    val top = (height - frameHeight) * 0.40f
    return RectF(margin, top, margin + frameWidth, top + frameHeight)
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (exception: Exception) {
                    continuation.resumeWithException(exception)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

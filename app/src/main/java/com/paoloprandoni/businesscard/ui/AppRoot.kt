package com.paoloprandoni.businesscard.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paoloprandoni.businesscard.R
import com.paoloprandoni.businesscard.core.ServiceLocator
import com.paoloprandoni.businesscard.ui.camera.CameraScreen
import com.paoloprandoni.businesscard.ui.components.PermissionExplanationDialog
import com.paoloprandoni.businesscard.ui.components.findActivity
import com.paoloprandoni.businesscard.ui.components.hasPermission
import com.paoloprandoni.businesscard.ui.components.openAppSettings
import com.paoloprandoni.businesscard.ui.crop.CropEditorScreen
import com.paoloprandoni.businesscard.ui.crop.CropStartShape
import com.paoloprandoni.businesscard.ui.review.ReviewScreen
import com.paoloprandoni.businesscard.ui.review.ReviewViewModel
import com.paoloprandoni.businesscard.ui.scan.HomeScreen
import com.paoloprandoni.businesscard.ui.scan.ScanViewModel
import kotlinx.coroutines.launch

/** Schermate dell'app (navigazione a stati, UDF). */
sealed interface AppScreen {
    data object Home : AppScreen
    data object Camera : AppScreen
    data object Review : AppScreen

    /** Ritaglio del logo del contatto dall'immagine della sessione. */
    data class Crop(val imagePath: String) : AppScreen

    /** Scelta dell'area di scansione (ROI) prima dell'OCR, dopo l'import da galleria. */
    data class Region(val imagePath: String) : AppScreen
}

@Composable
fun BusinessCardApp() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val imageProcessor = remember { ServiceLocator.imageProcessor }
    val scanViewModel: ScanViewModel = viewModel(factory = ScanViewModel.factory())
    val reviewViewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.factory())
    val scanState by scanViewModel.ui.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var showCameraRationale by remember { mutableStateOf(false) }
    var cameraPermanentlyDenied by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val file = imageProcessor.importImage(context, uri)
                if (file == null) {
                    scanViewModel.reportError("Impossibile leggere l'immagine selezionata.")
                } else {
                    // Prima si sceglie l'area del biglietto: l'OCR lavorerà solo su quella.
                    screen = AppScreen.Region(file.absolutePath)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showCameraRationale = false
            screen = AppScreen.Camera
        } else {
            cameraPermanentlyDenied = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            showCameraRationale = true
        }
    }

    LaunchedEffect(scanState.completedAt) {
        if (scanState.completedAt > 0L) {
            screen = AppScreen.Review
        }
    }

    BackHandler(enabled = screen != AppScreen.Home) {
        screen = when (screen) {
            is AppScreen.Crop -> AppScreen.Review
            else -> AppScreen.Home
        }
    }

    val launchGallery = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    when (val current = screen) {
        AppScreen.Home -> HomeScreen(
            isProcessing = scanState.isProcessing,
            stage = scanState.stage,
            error = scanState.error,
            onScanClick = {
                if (context.hasPermission(Manifest.permission.CAMERA)) {
                    screen = AppScreen.Camera
                } else {
                    cameraPermanentlyDenied = false
                    showCameraRationale = true
                }
            },
            onImportClick = launchGallery,
            onNewScan = {
                scanViewModel.dismissError()
                scanViewModel.reset()
            },
            onDismissError = scanViewModel::dismissError,
        )

        AppScreen.Camera -> CameraScreen(
            onPhotoCaptured = { file, roi, viewSize ->
                screen = AppScreen.Home
                scanViewModel.processImage(context, Uri.fromFile(file), roi, viewSize)
            },
            onClose = { screen = AppScreen.Home },
            onOpenGallery = launchGallery,
        )

        AppScreen.Review -> ReviewScreen(
            viewModel = reviewViewModel,
            onCropLogo = {
                val imagePath = ServiceLocator.scanSession.current()?.imagePath
                if (imagePath != null) screen = AppScreen.Crop(imagePath)
            },
            onNewScan = {
                scanViewModel.reset()
                reviewViewModel.reset()
                ServiceLocator.scanSession.clear()
                screen = AppScreen.Home
            },
            onBack = { screen = AppScreen.Home },
        )

        is AppScreen.Crop -> CropEditorScreen(
            imagePath = current.imagePath,
            title = "Ritaglia logo",
            confirmLabel = "Ritaglia",
            imageProcessor = imageProcessor,
            initialNormalized = ServiceLocator.scanSession.current()?.logoHint,
            onConfirm = { bitmap ->
                reviewViewModel.onLogoCropped(context, bitmap)
                screen = AppScreen.Review
            },
            onCancel = { screen = AppScreen.Review },
        )

        is AppScreen.Region -> CropEditorScreen(
            imagePath = current.imagePath,
            title = "Area di scansione",
            confirmLabel = "Analizza",
            imageProcessor = imageProcessor,
            startShape = CropStartShape.CARD,
            onConfirm = { bitmap ->
                screen = AppScreen.Home
                scanViewModel.processBitmap(context, bitmap)
            },
            onCancel = { screen = AppScreen.Home },
        )
    }

    if (showCameraRationale) {
        PermissionExplanationDialog(
            title = stringResource(R.string.permission_camera_title),
            message = stringResource(R.string.permission_camera_rationale),
            permanentlyDenied = cameraPermanentlyDenied,
            onRequest = {
                showCameraRationale = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = {
                showCameraRationale = false
                context.openAppSettings()
            },
            onDismiss = { showCameraRationale = false },
        )
    }
}

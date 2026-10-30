package com.pasajeya.app.feature.benefit.ui

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.ui.AuthScreen
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CarneCaptureScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BenefitViewModel = hiltViewModel()
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val errorMessage = when {
        state.carneUnreadable -> stringResource(R.string.benefit_carne_unreadable)
        state.error != null -> state.error
        else -> cameraError
    }

    // La pantalla solo se cierra cuando el servidor recibió el carné.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is BenefitEvent.CarneSubmitted) onBack()
        }
    }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(cameraPermissionState.status) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            val cameraProvider = context.getCameraProvider()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CarneCapture", "Use case binding failed", e)
                cameraError = "No se pudo inicializar la cámara."
            }
        }
    }

    AuthScreen(
        title = stringResource(R.string.benefit_step_carne_title),
        onBack = onBack,
        modifier = modifier
    ) {
        if (cameraPermissionState.status.isGranted) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.benefit_step_carne_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )

                    // Guía de encuadre
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Overlay para ayudar al encuadre
                        Box(
                            modifier = Modifier
                                .size(width = 300.dp, height = 190.dp)
                                .align(Alignment.Center)
                                .border(2.dp, PrimaryBlue, RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.1f))
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing || state.isLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                if (state.isLoading) {
                                    Text(
                                        text = stringResource(R.string.benefit_carne_processing),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            FloatingActionButton(
                                onClick = {
                                    isCapturing = true
                                    cameraError = null
                                    takePhoto(
                                        imageCapture = imageCapture,
                                        executor = ContextCompat.getMainExecutor(context),
                                        onImageCaptured = { bytes, rotation ->
                                            isCapturing = false
                                            viewModel.submitCarne(bytes, rotation)
                                        },
                                        onError = {
                                            cameraError = it.message
                                            isCapturing = false
                                        }
                                    )
                                },
                                containerColor = PrimaryBlue,
                                contentColor = Color.White
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Capturar")
                            }
                        }
                    }
                }

                errorMessage?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp)
                            .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(text = it, color = Color.White)
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.benefit_error_no_camera),
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (ByteArray, Int) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val rotation = image.imageInfo.rotationDegrees
                image.close()
                onImageCaptured(bytes, rotation)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProviderFuture ->
        cameraProviderFuture.addListener({
            continuation.resume(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

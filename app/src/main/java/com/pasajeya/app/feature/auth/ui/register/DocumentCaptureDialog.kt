package com.pasajeya.app.feature.auth.ui.register

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.PrimaryBlue

/** Camara a pantalla completa para fotografiar un documento (DNI o brevete). */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DocumentCaptureDialog(
    title: String,
    onDismiss: () -> Unit,
    onCaptured: (jpeg: ByteArray, rotationDegrees: Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = rememberPermissionState(Manifest.permission.CAMERA)
    var isCapturing by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(permission.status) {
        if (!permission.status.isGranted) permission.launchPermissionRequest()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (permission.status.isGranted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (error: Exception) {
                                cameraFailed = true
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.register_capture_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 320.dp, height = 205.dp)
                            .border(3.dp, PrimaryBlue, RoundedCornerShape(14.dp))
                    )

                    if (cameraFailed) {
                        Text(
                            text = stringResource(R.string.register_capture_error),
                            color = Color.White,
                            modifier = Modifier
                                .background(Color(0xFFD64545), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isCapturing) return@FloatingActionButton
                            isCapturing = true
                            takePhoto(
                                imageCapture = imageCapture,
                                onCaptured = { bytes, rotation ->
                                    isCapturing = false
                                    onCaptured(bytes, rotation)
                                },
                                onError = {
                                    isCapturing = false
                                    cameraFailed = true
                                },
                                context = context
                            )
                        },
                        shape = CircleShape,
                        containerColor = PrimaryBlue,
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.register_capture_action),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.register_capture_permission),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permission.launchPermissionRequest() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.register_capture_permission_action))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.register_capture_close),
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    context: android.content.Context,
    onCaptured: (ByteArray, Int) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                onCaptured(bytes, rotation)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

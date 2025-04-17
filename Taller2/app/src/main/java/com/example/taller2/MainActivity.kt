package com.example.taller2

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*

import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CameraScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val permissionState = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        permissionState.status.isGranted -> {
            // Si el permiso ya está concedido, se muestra la cámara
            PhotoCabinView()
        }
        else -> {
            // Si no tiene permiso, se muestra la vista de solicitud
            PermissionRequestView {
                permissionState.launchPermissionRequest()
            }
        }
    }
}


@Composable
fun PermissionRequestView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Añadido padding para mejorar la estética
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Alinea los elementos en la parte superior
    ) {
        Text(
            text = "Hola! Necesitamos el permiso de cámara para funcionar :)",
            modifier = Modifier.padding(top = 32.dp) // Da un poco de espacio superior para que no esté pegado al borde
        )

        Spacer(modifier = Modifier.height(20.dp)) // Añade un espacio entre el texto y el botón

        Button(onClick = onRequest) {
            Text("Permitir uso de cámara")
        }
    }
}



@Composable
fun PermissionDeniedView() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "El permiso de cámara fue denegado permanentemente. Ve a la configuración para habilitarlo.",
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text("Ir a Configuración")
            }
        }
    }
}

@Composable
fun PhotoCabinView() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    val photos = remember { mutableStateListOf<File>() }

    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

    LaunchedEffect(cameraProviderFuture) {
        cameraProvider = cameraProviderFuture.get()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val preview = Preview.Builder().build()
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                cameraProvider?.let { provider ->
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                }

                previewView
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                val photoFile = File(
                    photosDir,
                    "IMG_${System.currentTimeMillis()}.jpg"
                )
                val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            photos.add(photoFile)
                            Toast.makeText(context, "Foto tomada", Toast.LENGTH_SHORT).show()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Toast.makeText(context, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text("Tomar foto")
            }

            Button(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            }) {
                Icon(Icons.Default.Cached, contentDescription = null)
                Text("Cambiar cámara")
            }
        }

        if (photos.isEmpty()) {
            Text(
                "No hay fotos aún.",
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalItemSpacing = 4.dp,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(photos) { _, photo ->
                    Image(
                        painter = rememberAsyncImagePainter(photo),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

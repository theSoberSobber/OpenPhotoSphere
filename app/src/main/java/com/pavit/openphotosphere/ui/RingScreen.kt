package com.pavit.openphotosphere.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.sqrt
import kotlinx.coroutines.suspendCancellableCoroutine
import com.pavit.openphotosphere.math.RingProjector
import com.pavit.openphotosphere.math.Vec3
import com.pavit.openphotosphere.sensor.PoseProvider

@Composable
fun RingScreen(
    onComplete: (photos: List<String>) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val pose = remember { PoseProvider(context) }
    val photos = remember { mutableStateListOf<String>() }
    val capturedIndices = remember { mutableStateListOf<Int>() }
    var showHold by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(Unit) {
        pose.start()
        onDispose { pose.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onReady = { imageCapture.value = it }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val rotation = pose.rotationMatrix
            val gravityDevice = Vec3(pose.gravity[0], pose.gravity[1], pose.gravity[2])

            // Transform measured gravity (device frame) into world frame so the ring stays parallel to the floor.
            val worldUp = RingProjector.deviceToWorld(gravityDevice, rotation).norm()

            val forwardWorld = RingProjector.deviceToWorld(Vec3(0f, 0f, -1f), rotation)
            val forwardDir = forwardWorld.norm()

            // Sphere centered on the camera; oriented by world up.
            val sphereCenter = Vec3(0f, 0f, 0f)
            val radius = 1f
            val h = radius * 0.5f
            val midRadius = sqrt(radius*radius - h*h)

            val equatorPoints = RingProjector.generateRing(worldUp, center = sphereCenter, radius = radius, count = 8)
            val upperRingPoints = RingProjector.generateRing(worldUp, center = sphereCenter + worldUp * h, radius = midRadius, count = 5)
            val lowerRingPoints = RingProjector.generateRing(worldUp, center = sphereCenter - worldUp * h, radius = midRadius, count = 5)
            val polePoints = listOf(
                sphereCenter + worldUp * radius,
                sphereCenter - worldUp * radius
            )
            val allPoints = equatorPoints + upperRingPoints + lowerRingPoints + polePoints
            val remainingPoints = allPoints.filterIndexed { idx, _ -> !capturedIndices.contains(idx) }

            val cx = size.width / 2f
            val cy = size.height / 2f
            val focal = size.width * 0.8f
            val holeRadius = 56f
            val rectHalf = 150f
            val rectStroke = 10f

            fun project(p: Vec3): Offset? {
                val cam = RingProjector.worldToCamera(p, pose.rotationMatrix)
                val depth = -cam.z // Camera looks down -Z
                return if (depth > 0.1f) {
                    Offset(
                        cx + (cam.x / depth) * focal,
                        cy - (cam.y / depth) * focal
                    )
                } else null
            }

            fun drawRing(points: List<Vec3>, color: Color) {
                var first: Offset? = null
                var prev: Offset? = null
                points.forEach { p ->
                    val proj = project(p)
                    proj?.let { curr ->
                        prev?.let {
                            drawLine(color = color, start = it, end = curr, strokeWidth = 8f)
                        }
                        if (first == null) first = curr
                        prev = curr
                    }
                }
                if (first != null && prev != null && first != prev) {
                    drawLine(color = color, start = prev!!, end = first!!, strokeWidth = 8f)
                }
            }

            // Draw rings (gray) to verify placement.
            drawRing(equatorPoints, Color.LightGray)
            drawRing(upperRingPoints, Color.LightGray)
            drawRing(lowerRingPoints, Color.LightGray)

            // Ray-sphere intersection to find where the camera looks on the sphere (centered at origin).
            val lookPoint = forwardDir * radius
            fun lerpColor(a: Color, b: Color, t: Float): Color {
                val clamped = t.coerceIn(0f, 1f)
                return Color(
                    red = a.red + (b.red - a.red) * clamped,
                    green = a.green + (b.green - a.green) * clamped,
                    blue = a.blue + (b.blue - a.blue) * clamped,
                    alpha = a.alpha + (b.alpha - a.alpha) * clamped
                )
            }

            fun drawTarget(p: Vec3, base: Color) {
                val align = forwardDir.dot(p.norm()).coerceIn(-1f, 1f)
                val hit = ((align - 0.9f) / 0.1f).coerceIn(0f, 1f) // lights up when within ~25° cone
                val fillColor = lerpColor(base.copy(alpha = 0.35f), Color(0xFF66FF66), hit)
                val strokeColor = lerpColor(base, Color(0xFF00FF00), hit)
                project(p)?.let { c ->
                    val topLeft = Offset(c.x - rectHalf, c.y - rectHalf)
                    val size = Size(rectHalf * 2f, rectHalf * 2f)
                    drawRect(color = fillColor, topLeft = topLeft, size = size)
                    drawRect(
                        color = strokeColor,
                        topLeft = topLeft,
                        size = size,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = rectStroke)
                    )
                    drawCircle(color = Color.White, radius = holeRadius, center = c)
                }
            }

            // Draw remaining targets (rectangles with holes). Points on each ring are equally spaced by construction.
            remainingPoints.forEach { p ->
                drawTarget(p, Color(0xFF2E7D32))
            }

            // If everything is captured, stop drawing aiming aids.
            if (remainingPoints.isEmpty()) {
                return@Canvas
            }

            val look2d = project(lookPoint)
            val lookRadius = holeRadius * 0.7f // smaller than hole, drawn on top

            val nearest = remainingPoints.minByOrNull { p -> (p - lookPoint).length() }
            val nearest2d = nearest?.let { project(it) }

            if (look2d != null && nearest2d != null) {
                drawLine(color = Color.Blue, start = look2d, end = nearest2d, strokeWidth = 8f)

                val dir = nearest2d - look2d
                val len = dir.getDistance()
                if (len > 1f) {
                    val norm = Offset(dir.x / len, dir.y / len)
                    val headLength = 32f
                    val headWidth = 22f
                    val tip = nearest2d
                    val base = tip - norm * headLength
                    val perp = Offset(-norm.y, norm.x) * headWidth
                    drawLine(color = Color.Blue, start = tip, end = base + perp, strokeWidth = 8f)
                    drawLine(color = Color.Blue, start = tip, end = base - perp, strokeWidth = 8f)
                }
            }

            val targetIdx = nearest?.let { allPoints.indexOf(it) } ?: -1
            val withinHole = look2d != null && nearest2d != null &&
                (nearest2d - look2d!!).getDistance() <= (holeRadius - lookRadius)

            if (look2d != null && (!withinHole || capturing)) {
                drawCircle(color = Color.Red, radius = lookRadius, center = look2d)
            }

            if (withinHole && targetIdx >= 0 && !capturedIndices.contains(targetIdx) && !capturing) {
                showHold = true
                capturing = true
                val ic = imageCapture.value
                if (ic != null) {
                    val file = createImageFile(context.filesDir)
                    val output = ImageCapture.OutputFileOptions.Builder(file).build()
                    ic.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                showHold = false
                            }

                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                photos.add(file.absolutePath)
                                capturedIndices.add(targetIdx)
                                capturing = false
                                showHold = false
                                if (capturedIndices.size == allPoints.size) {
                                    onComplete(photos.toList())
                                }
                            }
                        }
                    )
                } else {
                    capturing = false
                    showHold = false
                }
            } else if (!withinHole) {
                showHold = false
            }
        }

        if (showHold) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    text = "HOLD",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0x99000000))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onReady: (ImageCapture) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(Unit) {
        val cameraProvider = context.getCameraProvider()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            onReady(imageCapture)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun createImageFile(filesDir: File): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
    val dir = File(filesDir, "photos").apply { mkdirs() }
    return File(dir, "IMG_$timeStamp.jpg")
}

private suspend fun android.content.Context.getCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { cont.resume(future.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }

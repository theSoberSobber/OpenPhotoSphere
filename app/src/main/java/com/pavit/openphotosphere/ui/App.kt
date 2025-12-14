package com.pavit.openphotosphere.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.exifinterface.media.ExifInterface
import com.pavit.openphotosphere.data.ProjectsStore
import com.pavit.openphotosphere.opencv.PanoramaStitcher
import com.pavit.openphotosphere.opencv.StitchException
import kotlinx.coroutines.launch
import java.util.UUID

data class Project(
    val id: String,
    val name: String,
    val photos: List<String>,
    val panoramaPath: String? = null
)

@Composable
fun OpenPhotosphereApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Projects) }
    val context = LocalContext.current
    val store = remember { ProjectsStore(context) }
    val projects = remember { mutableStateListOf<Project>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val loaded = store.load()
        projects.clear()
        projects.addAll(loaded)
    }

    Scaffold(
        floatingActionButton = {
            if (screen is Screen.Projects) {
                FloatingActionButton(onClick = { screen = Screen.Capture(UUID.randomUUID().toString()) }) {
                    Icon(Icons.Default.Add, contentDescription = "New project")
                }
            }
        }
    ) { padding ->
        when (val s = screen) {
            Screen.Projects -> ProjectsScreen(
                projects = projects,
                modifier = Modifier.padding(padding),
                onProjectClick = { screen = Screen.ProjectDetail(it.id) }
            )
            is Screen.Capture -> RingScreen(
                onComplete = { photos ->
                    val newProject = Project(s.projectId, "Project ${projects.size + 1}", photos)
                    projects.add(newProject)
                    // persist
                    scope.launch { store.save(projects.toList()) }
                    screen = Screen.Projects
                },
                onCancel = { screen = Screen.Projects }
            )
            is Screen.ProjectDetail -> ProjectDetailScreen(
                project = projects.firstOrNull { it.id == s.projectId },
                modifier = Modifier.padding(padding),
                onBack = { screen = Screen.Projects },
                onPanoramaUpdated = { id, path ->
                    val idx = projects.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        projects[idx] = projects[idx].copy(panoramaPath = path)
                        scope.launch { store.save(projects.toList()) }
                    }
                }
            )
        }
    }
}

@Composable
private fun ProjectDetailScreen(
    project: Project?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPanoramaUpdated: (projectId: String, panoramaPath: String?) -> Unit
) {
    if (project == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Project missing")
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var panorama by remember(project.panoramaPath) {
        mutableStateOf(project.panoramaPath?.let { loadOrientedBitmap(it, sampleSize = 2) })
    }
    var status by remember { mutableStateOf<String?>(null) }
    var stitching by remember { mutableStateOf(false) }
    var debugLogs by remember { mutableStateOf<List<String>>(emptyList()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    ) { padding ->
        var viewerIndex by remember { mutableStateOf<Int?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            panorama?.let { pano ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Image(
                        bitmap = pano,
                        contentDescription = "Panorama",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(pano.width.toFloat() / pano.height.toFloat())
                    )
                }
            }

            Button(
                onClick = {
                    status = null
                    stitching = true
                    debugLogs = emptyList()
                    scope.launch {
                        val result = runCatching { PanoramaStitcher.stitch(context, project.photos) }
                        stitching = false
                        result.onSuccess {
                            panorama = it.bitmap.asImageBitmap()
                            status = "Panorama ready"
                            onPanoramaUpdated(project.id, it.savedPath)
                            debugLogs = it.debugLog
                        }.onFailure { e ->
                            status = e.message ?: "Stitching failed"
                            debugLogs = if (e is StitchException) e.logs else emptyList()
                        }
                    }
                },
                enabled = project.photos.size >= 2 && !stitching,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
            ) {
                Text(if (stitching) "Stitching…" else "Stitch panorama")
            }

            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (debugLogs.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Stitch logs:", style = MaterialTheme.typography.labelLarge)
                    debugLogs.forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f, fill = true)) {
                if (project.photos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No photos captured.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(project.photos) { path ->
                            val thumb = remember(path) { loadOrientedBitmap(path, sampleSize = 8) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable { viewerIndex = project.photos.indexOf(path) }
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = path,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Preview\nunavailable", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }

                if (stitching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        viewerIndex?.let { start ->
            val pagerState = rememberPagerState(initialPage = start, pageCount = { project.photos.size })
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(state = pagerState) { page ->
                    val path = project.photos[page]
                    val full = remember(path) { loadOrientedBitmap(path) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewerIndex = null },
                        contentAlignment = Alignment.Center
                    ) {
                        if (full != null) {
                            Image(
                                bitmap = full,
                                contentDescription = path,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Unable to load image", modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsScreen(projects: List<Project>, modifier: Modifier = Modifier, onProjectClick: (Project) -> Unit) {
    if (projects.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No projects yet. Tap + to start.")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(projects) { project ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp),
                onClick = { onProjectClick(project) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Photos: ${project.photos.size}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

sealed interface Screen {
    data object Projects : Screen
    data class Capture(val projectId: String) : Screen
    data class ProjectDetail(val projectId: String) : Screen
}

private fun loadOrientedBitmap(path: String, sampleSize: Int = 1): androidx.compose.ui.graphics.ImageBitmap? {
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
    val exif = try { ExifInterface(path) } catch (e: Exception) { null }
    val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation == 0f) return bmp.asImageBitmap()
    val matrix = Matrix().apply { postRotate(rotation) }
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    if (rotated != bmp) bmp.recycle()
    return rotated.asImageBitmap()
}

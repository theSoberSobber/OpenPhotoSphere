package com.pavit.openphotosphere.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pavit.openphotosphere.data.ProjectsStore
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.exifinterface.media.ExifInterface
import android.graphics.Bitmap

data class Project(val id: String, val name: String, val photos: List<String>)

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
                onBack = { screen = Screen.Projects }
            )
        }
    }
}

@Composable
private fun ProjectDetailScreen(
    project: Project?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    if (project == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Project missing")
        }
        return
    }

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

        if (project.photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No photos captured.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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

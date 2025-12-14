package com.pavit.openphotosphere.data

import android.content.Context
import com.pavit.openphotosphere.ui.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectsStore(private val context: Context) {
    private val file: File = File(context.filesDir, "projects.json")

    suspend fun load(): List<Project> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val text = file.readText()
        if (text.isBlank()) return@withContext emptyList()
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val photosArr = obj.optJSONArray("photos") ?: JSONArray()
            val photos = (0 until photosArr.length()).map { photosArr.getString(it) }
            Project(
                id = obj.getString("id"),
                name = obj.getString("name"),
                photos = photos,
                panoramaPath = obj.optString("panoramaPath").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun save(projects: List<Project>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        projects.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            val photos = JSONArray()
            p.photos.forEach { photos.put(it) }
            obj.put("photos", photos)
            p.panoramaPath?.let { obj.put("panoramaPath", it) }
            arr.put(obj)
        }
        file.writeText(arr.toString())
    }
}

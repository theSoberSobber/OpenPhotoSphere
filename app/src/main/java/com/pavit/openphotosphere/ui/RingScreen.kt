package com.pavit.openphotosphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt
import com.pavit.openphotosphere.math.RingProjector
import com.pavit.openphotosphere.math.Vec3
import com.pavit.openphotosphere.sensor.PoseProvider

@Composable
fun RingScreen() {
    val context = LocalContext.current
    val pose = remember { PoseProvider(context) }

    DisposableEffect(Unit) {
        pose.start()
        onDispose { pose.stop() }
    }

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
        val disc = radius * radius // for center at origin and |forwardDir|=1, t=radius
        val lookPoint = forwardDir * radius
        // Draw arrow to nearest point on sphere to the look point.
        val nearest = lookPoint?.let { lp ->
            allPoints.minByOrNull { p -> (p - lp).length() }
        }
        val nearest2d = nearest?.let { project(it) }
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

        // Draw targets (rectangles with holes). Points on each ring are equally spaced by construction.
        allPoints.forEach { p ->
            drawTarget(p, Color(0xFF2E7D32))
        }

        val look2d = lookPoint?.let { project(it) }
        val lookRadius = holeRadius * 0.7f // smaller than hole, drawn on top
        look2d?.let {
            drawCircle(color = Color.Red, radius = lookRadius, center = it)
        }

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
    }
}

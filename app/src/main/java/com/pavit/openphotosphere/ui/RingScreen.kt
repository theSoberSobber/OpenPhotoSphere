package com.pavit.openphotosphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

        // Camera forward flattened onto the floor plane to keep the ring anchored on the ground.
        val forwardWorld = RingProjector.deviceToWorld(Vec3(0f, 0f, -1f), rotation)
        val forwardFlat = forwardWorld - worldUp * forwardWorld.dot(worldUp)
        val flatLen = forwardFlat.length()
        val flatDir = if (flatLen > 1e-3f) forwardFlat * (1f / flatLen) else Vec3(1f, 0f, 0f)
        val forwardDir = forwardWorld.norm()

        val sphereCenter = flatDir * 1f // place 1 unit ahead on the floor plane
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
        val dotRadius = 16f

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
                        drawLine(color = color, start = it, end = curr, strokeWidth = 3f)
                    }
                    if (first == null) first = curr
                    prev = curr
                }
            }
            if (first != null && prev != null && first != prev) {
                drawLine(color = color, start = prev!!, end = first!!, strokeWidth = 3f)
            }
        }

        // Draw rings (gray) to verify placement.
        drawRing(equatorPoints, Color.LightGray)
        drawRing(upperRingPoints, Color.LightGray)
        drawRing(lowerRingPoints, Color.LightGray)

        // Ray-sphere intersection to find where the camera looks on the sphere.
        val dDotC = forwardDir.dot(sphereCenter)
        val centerLen2 = sphereCenter.dot(sphereCenter)
        val disc = dDotC * dDotC - (centerLen2 - radius * radius)
        val lookPoint = if (disc >= 0f) {
            val sd = sqrt(disc)
            val t0 = dDotC - sd
            val t1 = dDotC + sd
            val t = listOf(t0, t1).filter { it > 0f }.minOrNull()
            t?.let { forwardDir * it }
        } else null
        val look2d = lookPoint?.let { project(it) }
        if (look2d != null) {
            drawCircle(color = Color.Red, radius = dotRadius * 0.9f, center = look2d)
        }

        // Draw arrow to nearest point on sphere to the look point.
        val nearest = lookPoint?.let { lp ->
            allPoints.minByOrNull { p -> (p - lp).length() }
        }
        val nearest2d = nearest?.let { project(it) }
        if (look2d != null && nearest2d != null) {
            drawLine(color = Color.Blue, start = look2d, end = nearest2d, strokeWidth = 4f)

            val dir = nearest2d - look2d
            val len = dir.getDistance()
            if (len > 1f) {
                val norm = Offset(dir.x / len, dir.y / len)
                val headLength = 18f
                val headWidth = 12f
                val tip = nearest2d
                val base = tip - norm * headLength
                val perp = Offset(-norm.y, norm.x) * headWidth
                drawLine(color = Color.Blue, start = tip, end = base + perp, strokeWidth = 4f)
                drawLine(color = Color.Blue, start = tip, end = base - perp, strokeWidth = 4f)
            }
        }

        // Draw points (green). Points on each ring are equally spaced by construction.
        allPoints.forEach { p ->
            project(p)?.let { c ->
                drawCircle(color = Color.Green, radius = dotRadius, center = c)
            }
        }
    }
}

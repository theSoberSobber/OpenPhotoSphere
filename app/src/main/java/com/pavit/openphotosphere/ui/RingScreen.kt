package com.pavit.openphotosphere.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

        val ringCenter = flatDir * 1f // place 1 unit ahead on the floor
        val ring = RingProjector.generateRing(worldUp, center = ringCenter)

        val cx = size.width / 2f
        val cy = size.height / 2f
        val focal = size.width * 0.8f

        var last: Offset? = null

        ring.forEach { p ->
            val cam = RingProjector.worldToCamera(p, pose.rotationMatrix)
            val depth = -cam.z // Camera looks down -Z

            if (depth > 0.1f) {
                val sx = cx + (cam.x / depth) * focal
                val sy = cy - (cam.y / depth) * focal
                val o = Offset(sx, sy)

                last?.let {
                    drawLine(
                        color = Color.Green,
                        start = it,
                        end = o,
                        strokeWidth = 3f
                    )
                }
                last = o
            } else {
                last = null
            }
        }
    }
}

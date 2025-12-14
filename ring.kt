package ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import math.RingProjector
import math.Vec3
import sensor.PoseProvider

@Composable
fun RingScreen() {
    val context = LocalContext.current
    val pose = remember { PoseProvider(context) }

    DisposableEffect(Unit) {
        pose.start()
        onDispose { pose.stop() }
    }

    Canvas(modifier = Modifier) {
        val ring = RingProjector.generateRing(
            Vec3(pose.gravity[0], pose.gravity[1], pose.gravity[2])
        )

        val cx = size.width / 2f
        val cy = size.height / 2f
        val focal = size.width * 0.8f

        var last: Offset? = null

        ring.forEach { p ->
            val cam = RingProjector.worldToCamera(p, pose.rotationMatrix)

            if (cam.z > 0.1f) {
                val sx = cx + (cam.x / cam.z) * focal
                val sy = cy - (cam.y / cam.z) * focal
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

package com.pavit.openphotosphere.math

import kotlin.math.*

data class Vec3(val x: Float, val y: Float, val z: Float) {
    fun dot(o: Vec3) = x*o.x + y*o.y + z*o.z
    fun cross(o: Vec3) =
        Vec3(
            y*o.z - z*o.y,
            z*o.x - x*o.z,
            x*o.y - y*o.x
        )

    fun norm(): Vec3 {
        val l = sqrt(x*x + y*y + z*z)
        return Vec3(x/l, y/l, z/l)
    }

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun length(): Float = sqrt(x*x + y*y + z*z)
}

object RingProjector {
    fun deviceToWorld(p: Vec3, R: FloatArray): Vec3 {
        // rotationMatrix * p (device -> world)
        return Vec3(
            R[0]*p.x + R[1]*p.y + R[2]*p.z,
            R[3]*p.x + R[4]*p.y + R[5]*p.z,
            R[6]*p.x + R[7]*p.y + R[8]*p.z
        )
    }

    fun generateRing(
        normal: Vec3,
        center: Vec3 = Vec3(0f, 0f, 0f),
        radius: Float = 1f,
        count: Int = 180
    ): List<Vec3> {
        val g = normal.norm()
        val a = if (abs(g.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)

        val u = g.cross(a).norm()
        val v = g.cross(u).norm()

        return (0 until count).map { i ->
            val t = (2 * Math.PI * i / count).toFloat()
            center + (u * cos(t) + v * sin(t)) * radius
        }
    }

    fun worldToCamera(p: Vec3, R: FloatArray): Vec3 {
        // transpose(rotationMatrix) * p
        return Vec3(
            R[0]*p.x + R[3]*p.y + R[6]*p.z,
            R[1]*p.x + R[4]*p.y + R[7]*p.z,
            R[2]*p.x + R[5]*p.y + R[8]*p.z
        )
    }
}

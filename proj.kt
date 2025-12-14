package math

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
}

object RingProjector {

    fun generateRing(
        gravity: Vec3,
        segments: Int = 180
    ): List<Vec3> {

        val g = gravity.norm()
        val a = if (abs(g.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)

        val u = g.cross(a).norm()
        val v = g.cross(u).norm()

        return (0..segments).map { i ->
            val t = (2 * Math.PI * i / segments).toFloat()
            Vec3(
                cos(t) * u.x + sin(t) * v.x,
                cos(t) * u.y + sin(t) * v.y,
                cos(t) * u.z + sin(t) * v.z
            )
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

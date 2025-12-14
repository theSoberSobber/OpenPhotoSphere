package com.pavit.openphotosphere.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.sqrt

class PoseProvider(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVector =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var rotationMatrix by mutableStateOf(
        floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
    )
        private set
    var gravity by mutableStateOf(floatArrayOf(0f, 0f, 1f))
        private set

    private val rotAlpha = 0.15f
    private val gravAlpha = 0.1f

    fun start() {
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val matrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                rotationMatrix = rotationMatrix
                    .mapIndexed { i, v -> v * (1f - rotAlpha) + matrix[i] * rotAlpha }
                    .toFloatArray()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val len = sqrt(x*x + y*y + z*z)
                if (len > 0f) {
                    val gx = x / len
                    val gy = y / len
                    val gz = z / len
                    gravity = floatArrayOf(
                        gravity[0] * (1f - gravAlpha) + gx * gravAlpha,
                        gravity[1] * (1f - gravAlpha) + gy * gravAlpha,
                        gravity[2] * (1f - gravAlpha) + gz * gravAlpha
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

package com.cocorico.ring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Détecte que le téléphone a été pris en main : mouvement franc soutenu pendant
 * [DUREE_REQUISE_MS]. Un téléphone posé sur une table produit une norme
 * d'accélération stable à ~9,81 ; un téléphone tenu oscille en permanence.
 */
class HandDetector(
    context: Context,
    private val onPrisEnMain: () -> Unit,
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelerometre: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var debutMouvement: Long = 0L
    private var declenche = false

    fun demarrer() {
        accelerometre?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun arreter() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (declenche) return

        val norme = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        val ecart = abs(norme - GRAVITE)
        val maintenant = System.currentTimeMillis()

        if (ecart > SEUIL_MOUVEMENT) {
            if (debutMouvement == 0L) debutMouvement = maintenant
            if (maintenant - debutMouvement >= DUREE_REQUISE_MS) {
                declenche = true
                onPrisEnMain()
            }
        } else {
            debutMouvement = 0L
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val GRAVITE = 9.81f
        const val SEUIL_MOUVEMENT = 1.5f
        const val DUREE_REQUISE_MS = 2_000L
    }
}

package com.cocorico.ring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * Enveloppe Android autour de [PriseEnMainDetector] : elle branche
 * l'accéléromètre, lui passe les échantillons horodatés, et prévient une seule
 * fois quand le téléphone a été pris en main. Toute la décision — donc tout ce
 * qui peut se tromper — vit dans la classe pure, testée sans appareil.
 */
class HandDetector(
    context: Context,
    private val decision: PriseEnMainDetector = PriseEnMainDetector(),
    private val onPrisEnMain: () -> Unit,
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelerometre: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var notifie = false

    /**
     * `SENSOR_DELAY_GAME` (~50 Hz) et non `SENSOR_DELAY_UI` (~16 Hz) : à 16 Hz,
     * les vibrations du haut-parleur autour de 17 et 33 Hz se replient par
     * repliement de spectre en quasi-continu, et aucun filtre logiciel ne peut
     * plus les distinguer d'un vrai mouvement — le volume baisserait tout seul
     * sur une alarme que personne n'a entendue. Échantillonner deux fois plus
     * vite les repousse hors de la bande utile ; le surcoût dure le temps d'une
     * alarme.
     */
    fun demarrer() {
        accelerometre?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun arreter() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (notifie) return
        // `event.timestamp` est en nanosecondes depuis le démarrage, monotone —
        // à l'inverse de l'horloge murale, qui peut sauter en pleine alarme.
        // Certains capteurs l'horodatent mal : on retombe alors sur l'horloge
        // monotone du système.
        val instantMs = if (event.timestamp > 0L) {
            event.timestamp / 1_000_000L
        } else {
            SystemClock.elapsedRealtime()
        }
        if (decision.onEchantillon(event.values[0], event.values[1], event.values[2], instantMs)) {
            notifie = true
            onPrisEnMain()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

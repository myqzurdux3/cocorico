package com.cocorico.ring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cocorico.challenge.pompes.EchantillonPompe
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Alimente [com.cocorico.challenge.pompes.CompteurPompes] depuis les capteurs.
 * Cette classe ne décide rien : elle convertit des mesures brutes en
 * échantillons et les transmet.
 *
 * L'inclinaison est calculée sur une estimation de la gravité par filtre
 * passe-bas, puis convertie en angle par [PriseEnMainDetector.inclinaisonDegres]
 * — le même calcul que la détection de prise en main, pour ne pas le dupliquer.
 */
class CapteurPompes(
    context: Context,
    private val onEchantillon: (EchantillonPompe) -> Unit,
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val proximite: Sensor? = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometre: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var gravite = floatArrayOf(0f, 0f, GRAVITE)
    private var proche = false

    fun capteurDisponible(): Boolean = proximite != null

    fun demarrer() {
        proximite?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometre?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun arreter() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                // Seuil relatif à la portée du capteur : certains ne rapportent
                // que 0 ou leur maximum, d'autres une distance en centimètres.
                proche = event.values[0] < (event.sensor.maximumRange / 2f)
                emettre(event.timestampMillis())
            }

            Sensor.TYPE_ACCELEROMETER -> {
                for (i in 0..2) {
                    gravite[i] = ALPHA * gravite[i] + (1 - ALPHA) * event.values[i]
                }
                emettre(event.timestampMillis())
            }
        }
    }

    private fun emettre(tMillis: Long) {
        // Vecteur trop court pour porter une direction : on attend le prochain
        // échantillon plutôt que d'émettre un angle arbitraire.
        val inclinaison = PriseEnMainDetector.inclinaisonDegres(gravite[0], gravite[1], gravite[2])
            ?: return

        val norme = sqrt(gravite[0] * gravite[0] + gravite[1] * gravite[1] + gravite[2] * gravite[2])

        onEchantillon(
            EchantillonPompe(
                procheDuCapteur = proche,
                inclinaisonDegres = inclinaison,
                ecartGravite = abs(norme - GRAVITE),
                tMillis = tMillis,
            ),
        )
    }

    private fun SensorEvent.timestampMillis(): Long = timestamp / 1_000_000L

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val GRAVITE = 9.81f
        const val ALPHA = 0.85f
    }
}

package com.cocorico.ring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * Enveloppe Android autour de [PriseEnMainDetector] et de [MouvementDetector] :
 * elle branche l'accéléromètre, lui passe les échantillons horodatés, et
 * prévient l'appelant de deux choses distinctes. Toute la décision — donc tout
 * ce qui peut se tromper — vit dans les deux classes pures, testées sans
 * appareil.
 *
 * Un seul [SensorEventListener] pour les deux détecteurs : ils consomment le
 * même flux d'échantillons dans le même [onSensorChanged], pas deux
 * enregistrements concurrents auprès du [SensorManager].
 */
class HandDetector(
    context: Context,
    private val decision: PriseEnMainDetector = PriseEnMainDetector(),
    private val mouvement: MouvementDetector = MouvementDetector(),
    private val onPrisEnMain: () -> Unit,
    /**
     * Appelé à chaque échantillon tant que [MouvementDetector.enMouvement] est
     * vrai — pas seulement au passage à vrai — pour que l'appelant puisse
     * réarmer un compte à rebours en continu pendant tout le geste, pas
     * uniquement à son début.
     */
    private val onMouvement: () -> Unit = {},
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelerometre: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var notifie = false

    /**
     * Sans accéléromètre, la baisse de volume à la prise en main et le réarmement
     * du compte à rebours sur mouvement sont l'un comme l'autre inopérants. Ce
     * n'est pas rattrapable ici, mais l'appelant doit pouvoir le dire à
     * l'utilisateur plutôt que de lui promettre un comportement qui n'arrivera
     * jamais — [CapteurPompes.capteurDisponible] existe pour la même raison.
     */
    fun capteurDisponible(): Boolean = accelerometre != null

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
        // Une seule horloge, jamais deux, exactement comme [CapteurPompes] le
        // documente. Le repli conditionnel d'avant mélangeait `event.timestamp`
        // (nanosecondes, base propre au capteur) et `SystemClock.elapsedRealtime`
        // (millisecondes depuis le démarrage) échantillon par échantillon : le
        // premier échantillon mal horodaté faisait basculer de base entre deux
        // échantillons, et les détecteurs voyaient un `dt` de plusieurs heures ou
        // négatif. Filtres téléportés, détection figée ou déclenchée à faux.
        // `elapsedRealtime` est monotone et ne saute pas comme l'horloge murale ;
        // la latence de livraison d'un événement capteur reste négligeable devant
        // les constantes de temps en jeu (100 à 350 ms).
        val instantMs = SystemClock.elapsedRealtime()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Le mouvement continue d'être suivi même après la prise en main
        // verrouillée : c'est lui qui réarme le compte à rebours ensuite, la
        // prise en main ne notifie qu'une fois.
        if (!notifie && decision.onEchantillon(x, y, z, instantMs)) {
            notifie = true
            onPrisEnMain()
        }
        if (mouvement.onEchantillon(x, y, z, instantMs)) {
            onMouvement()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

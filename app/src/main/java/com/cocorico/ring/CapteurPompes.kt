package com.cocorico.ring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.cocorico.challenge.pompes.EchantillonPompe
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Alimente [com.cocorico.challenge.pompes.CompteurPompes] depuis les capteurs.
 * Cette classe ne décide rien : elle convertit des mesures brutes en
 * échantillons et les transmet.
 *
 * L'inclinaison et l'écart de gravité sont calculés par [EstimateurGravite],
 * qui sépare les deux usages sur deux canaux — voir sa documentation pour le
 * pourquoi.
 */
class CapteurPompes(
    context: Context,
    private val onEchantillon: (EchantillonPompe) -> Unit,
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val proximite: Sensor? = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometre: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val estimateurGravite = EstimateurGravite()
    private var proche = false

    /**
     * Les deux capteurs sont exigés, pas seulement la proximité : sans
     * accéléromètre, [EstimateurGravite] ne recevrait jamais d'échantillon et
     * `inclinaisonDegres`/`ecartGravite` resteraient indéfinis. Deux des
     * quatre règles anti-triche de CompteurPompes (inclinaison, immobilité)
     * seraient alors désarmées en silence — il suffirait d'agiter la main
     * devant le capteur, téléphone tenu en main, pour valider des répétitions
     * fictives. Le matériel sans accéléromètre est rare ; la conséquence ne
     * l'est pas.
     */
    fun capteurDisponible(): Boolean = proximite != null && accelerometre != null

    fun demarrer() {
        proximite?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometre?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun arreter() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Horloge unique et monotone pour les deux capteurs, volontairement
        // pas `event.timestamp` : CompteurPompes soustrait un horodatage de
        // proximité à un horodatage d'accéléromètre pour mesurer une durée de
        // cycle. `event.timestamp` a sa propre base par capteur — nanosecondes
        // depuis le démarrage sur certains, boottime sur d'autres, parfois
        // zéro faute de support matériel. Si les deux capteurs ne partagent
        // pas la même base, la durée calculée tombe hors des bornes admises
        // et plus aucune répétition n'est comptée, silencieusement, alarme à
        // plein volume : ce n'est pas une dégradation progressive, c'est tout
        // ou rien. `HandDetector` peut se permettre l'horodatage matériel
        // avec un repli conditionnel car il ne combine jamais deux capteurs
        // entre eux dans une même soustraction ; ici les deux canaux se
        // rencontrent, donc seule une horloge commune à chaque événement,
        // sans repli capteur par capteur, garantit qu'ils parlent le même
        // temps. La latence de livraison d'un événement capteur (quelques
        // millisecondes) reste très en dessous des seuils de durée du
        // compteur (150 ms à 8 s) : rien n'est perdu à l'utiliser à la place
        // de l'horodatage matériel.
        val tMillis = SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                // Seuil relatif à la portée du capteur : certains ne rapportent
                // que 0 ou leur maximum, d'autres une distance en centimètres.
                proche = event.values[0] < (event.sensor.maximumRange / 2f)
                emettre(tMillis)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                estimateurGravite.onEchantillon(event.values[0], event.values[1], event.values[2], tMillis)
                emettre(tMillis)
            }
        }
    }

    private fun emettre(tMillis: Long) {
        // Vecteur trop court pour porter une direction, ou aucun échantillon
        // d'accéléromètre encore reçu : on attend le prochain plutôt que
        // d'émettre un angle arbitraire.
        val inclinaison = estimateurGravite.inclinaisonDegres ?: return

        onEchantillon(
            EchantillonPompe(
                procheDuCapteur = proche,
                inclinaisonDegres = inclinaison,
                ecartGravite = estimateurGravite.ecartGravite,
                tMillis = tMillis,
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Estime la gravité par deux canaux de lissage indépendants, sur le modèle de
 * [PriseEnMainDetector] qui a déjà résolu ce même arbitrage pour la détection
 * de prise en main. Classe pure, sans dépendance Android : testable sans
 * appareil.
 *
 * - Un canal **lent** ([tauOrientationMs] ≈ 350 ms, comme
 *   [PriseEnMainDetector.TAU_GRAVITE_MS]), pour [inclinaisonDegres] : la
 *   direction de la gravité doit rester stable pendant le geste, un filtre
 *   lent absorbe le bruit du capteur sans faire trembler l'angle.
 *
 * - Un canal **rapide** ([tauEcartMs] ≈ 100 ms, comme
 *   [PriseEnMainDetector.TAU_ENERGIE_MS]), pour [ecartGravite] : la garde
 *   anti-triche « posé à plat et immobile » de CompteurPompes doit réagir à
 *   un mouvement de quelques centaines de millisecondes — une main qui agite
 *   le téléphone devant le capteur pour simuler des répétitions. Mesuré
 *   avec le canal lent, ce geste disparaît quasiment : une agitation
 *   sinusoïdale à 3 Hz et 4 m/s² d'amplitude culmine à un écart de 0,91 avec
 *   un filtre à 370 ms (sous le seuil ECART_MAX = 1,5 de CompteurPompes, donc
 *   invisible), contre 2,17 avec un filtre à 100 ms — largement au-dessus.
 *   Ces deux valeurs viennent d'une simulation numérique du filtre, à la
 *   cadence SENSOR_DELAY_UI (voir le rapport de correction).
 *
 * Le lissage rapide n'est pas nul pour autant : mesurer l'écart sur le signal
 * **brut** redresserait aussi les vibrations du haut-parleur — l'alarme
 * hurle à plein volume, souvent posée sur un sol dur, à quelques centimètres
 * du capteur — en un écart permanent qui bloquerait la garde en continu.
 * Un lissage à 100 ms annule les oscillations de moyenne nulle au-delà d'une
 * dizaine de Hz tout en laissant passer le mouvement d'un corps rigide :
 * une simulation d'une vibration de haut-parleur (30 Hz, 2 m/s²) culmine à un
 * écart de 0,20 avec ce filtre, très en dessous du seuil. C'est exactement le
 * compromis que documente [PriseEnMainDetector] pour son propre canal
 * d'énergie.
 *
 * Filtrage en temps réel — constante de temps en millisecondes, `dt` borné —
 * et non par échantillon à ALPHA fixe comme l'ancienne implémentation :
 * Android ne garantit pas la cadence SENSOR_DELAY_UI, un ALPHA fixe dériverait
 * si elle varie.
 */
class EstimateurGravite(
    private val tauOrientationMs: Float = TAU_ORIENTATION_MS,
    private val tauEcartMs: Float = TAU_ECART_MS,
) {

    /** Estimation lente de la gravité : sert à l'orientation. */
    private var lenteX = 0f
    private var lenteY = 0f
    private var lenteZ = 0f

    /** Estimation rapide de la gravité : sert à l'écart, elle doit suivre le geste. */
    private var rapideX = 0f
    private var rapideY = 0f
    private var rapideZ = 0f

    private var amorce = false
    private var dernierMs = 0L

    /** Inclinaison courante, en degrés ; null tant qu'aucun échantillon n'est arrivé. */
    var inclinaisonDegres: Float? = null
        private set

    /** Écart courant à la gravité de référence, sur le canal rapide. */
    var ecartGravite: Float = 0f
        private set

    /**
     * Consomme un échantillon de l'accéléromètre (m/s², repère appareil).
     */
    fun onEchantillon(x: Float, y: Float, z: Float, nowMillis: Long) {
        // Le premier échantillon initialise les deux filtres : sans lui, ils
        // partiraient de zéro et convergeraient artificiellement lentement
        // vers la première vraie mesure.
        if (!amorce) {
            lenteX = x; lenteY = y; lenteZ = z
            rapideX = x; rapideY = y; rapideZ = z
            amorce = true
            dernierMs = nowMillis
            majSorties()
            return
        }

        // Borné : une salve d'échantillons en retard ou une pause du capteur
        // ne doivent pas téléporter la gravité estimée.
        val dtMs = (nowMillis - dernierMs).coerceIn(0L, DT_MAX_MS).toFloat()
        dernierMs = nowMillis

        val alphaLente = dtMs / (tauOrientationMs + dtMs)
        lenteX += alphaLente * (x - lenteX)
        lenteY += alphaLente * (y - lenteY)
        lenteZ += alphaLente * (z - lenteZ)

        val alphaRapide = dtMs / (tauEcartMs + dtMs)
        rapideX += alphaRapide * (x - rapideX)
        rapideY += alphaRapide * (y - rapideY)
        rapideZ += alphaRapide * (z - rapideZ)

        majSorties()
    }

    private fun majSorties() {
        inclinaisonDegres = PriseEnMainDetector.inclinaisonDegres(lenteX, lenteY, lenteZ)
        val norme = sqrt(rapideX * rapideX + rapideY * rapideY + rapideZ * rapideZ)
        ecartGravite = abs(norme - GRAVITE)
    }

    companion object {
        const val GRAVITE = 9.81f

        /** Lent : l'orientation doit rester stable pendant le geste. */
        const val TAU_ORIENTATION_MS = 350f

        /** Rapide : doit suivre une agitation de quelques centaines de ms. */
        const val TAU_ECART_MS = 100f

        private const val DT_MAX_MS = 200L
    }
}

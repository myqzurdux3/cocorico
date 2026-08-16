package com.cocorico.ring

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Décide, à partir des seuls échantillons de l'accéléromètre, si le téléphone a
 * été pris en main. Aucune dépendance à Android, aucune horloge interne :
 * l'appelant fournit l'instant, comme [InactivityTracker]. Toute la logique est
 * donc vérifiable en test unitaire, sans appareil.
 *
 * L'ancienne règle — « écart de norme > 1,5 m/s² pendant 2 s d'affilée » — ne
 * décrivait pas un téléphone pris en main mais un téléphone secoué : un seul
 * échantillon sous le seuil remettait le compteur à zéro, et un téléphone
 * simplement tenu tremble bien en dessous de 1,5. Elle ne se déclenchait donc
 * jamais dans la vraie vie.
 *
 * Deux signaux la remplacent, l'un ou l'autre suffit :
 *
 * 1. **L'inclinaison.** Posé, un téléphone met toute la gravité sur son axe Z ;
 *    tenu pour être regardé, il est franchement penché. On estime la direction
 *    de la gravité par un filtre passe-bas ([tauGraviteMs]) et on déclenche sur
 *    une inclinaison au-delà de [seuilAngleDeg] tenue [dureeInclinaisonMs].
 *    Le signal doit d'abord avoir été vu à plat : un téléphone déjà posé de
 *    biais sur un socle ne doit pas déclencher tout seul — c'est exactement le
 *    cas que le second signal rattrape.
 *
 * 2. **L'énergie de mouvement accumulée.** On intègre l'écart de norme au-dessus
 *    d'un plancher de bruit sur une fenêtre glissante d'environ
 *    [fenetreEnergieMs] (intégrateur à fuite), et on déclenche au franchissement
 *    de [budgetEnergie]. Aucun échantillon n'a besoin d'être grand : c'est la
 *    somme qui compte, ce qui attrape la prise en main d'un téléphone déjà
 *    incliné.
 *
 * Point crucial : l'énergie est mesurée sur l'accélération **lissée**
 * ([tauEnergieMs]) et non brute. Une somme d'écarts bruts est redressée : les
 * vibrations du haut-parleur — l'alarme hurle à fond, souvent sur un meuble dur —
 * s'y accumuleraient en permanence et feraient baisser le volume d'une alarme
 * que personne n'a encore entendue. Lisser avant de redresser annule les
 * oscillations de moyenne nulle (> 10 Hz) et ne laisse passer que le mouvement
 * de corps rigide, celui d'une main.
 *
 * La décision est verrouillée : une fois [estPrisEnMain] vrai, il le reste.
 */
class PriseEnMainDetector(
    private val seuilAngleDeg: Float = SEUIL_ANGLE_DEG,
    private val hysteresisAngleDeg: Float = HYSTERESIS_ANGLE_DEG,
    private val dureeInclinaisonMs: Long = DUREE_INCLINAISON_MS,
    private val plancherEnergie: Float = PLANCHER_ENERGIE,
    private val budgetEnergie: Float = BUDGET_ENERGIE,
    private val fenetreEnergieMs: Float = FENETRE_ENERGIE_MS,
    private val tauGraviteMs: Float = TAU_GRAVITE_MS,
    private val tauEnergieMs: Float = TAU_ENERGIE_MS,
) {

    /** Estimation lente de la gravité : sert à l'orientation. */
    private var graviteX = 0f
    private var graviteY = 0f
    private var graviteZ = 0f

    /** Estimation rapide : sert à l'énergie, elle doit suivre le geste. */
    private var lisseX = 0f
    private var lisseY = 0f
    private var lisseZ = 0f

    private var amorce = false
    private var dernierMs = 0L

    /**
     * Le signal d'inclinaison n'est armé qu'après avoir vu le téléphone à plat.
     * Sans ça, un téléphone posé de biais sur un socle déclencherait dès le
     * premier échantillon, alarme à peine commencée.
     */
    private var vuAPlat = false
    private var inclineDepuisMs = 0L
    private var incline = false

    /** Inclinaison courante par rapport à l'horizontale, en degrés. */
    var inclinaisonDeg: Float = 0f
        private set

    /** Énergie de mouvement accumulée sur la fenêtre glissante, en m/s. */
    var energie: Float = 0f
        private set

    var estPrisEnMain: Boolean = false
        private set

    /**
     * Consomme un échantillon de l'accéléromètre (m/s², repère appareil) et
     * renvoie l'état verrouillé : vrai dès que la prise en main est reconnue.
     */
    fun onEchantillon(x: Float, y: Float, z: Float, nowMillis: Long): Boolean {
        if (estPrisEnMain) return true

        // Le premier échantillon initialise les filtres : sans lui, ils
        // partiraient de zéro et l'écart de norme initial (9,81) déclencherait
        // l'énergie à coup sûr.
        if (!amorce) {
            graviteX = x; graviteY = y; graviteZ = z
            lisseX = x; lisseY = y; lisseZ = z
            amorce = true
            dernierMs = nowMillis
            majInclinaison(nowMillis)
            return estPrisEnMain
        }

        // Borné : une salve d'échantillons en retard ou une pause du capteur ne
        // doivent ni téléporter la gravité ni verser d'un coup une intégrale.
        val dtMs = (nowMillis - dernierMs).coerceIn(0L, DT_MAX_MS).toFloat()
        dernierMs = nowMillis

        val alphaGravite = dtMs / (tauGraviteMs + dtMs)
        graviteX += alphaGravite * (x - graviteX)
        graviteY += alphaGravite * (y - graviteY)
        graviteZ += alphaGravite * (z - graviteZ)

        val alphaLisse = dtMs / (tauEnergieMs + dtMs)
        lisseX += alphaLisse * (x - lisseX)
        lisseY += alphaLisse * (y - lisseY)
        lisseZ += alphaLisse * (z - lisseZ)

        majInclinaison(nowMillis)
        majEnergie(dtMs)
        return estPrisEnMain
    }

    private fun majInclinaison(nowMillis: Long) {
        inclinaisonDeg = inclinaisonDegres(graviteX, graviteY, graviteZ) ?: return

        if (inclinaisonDeg < seuilAngleDeg) {
            // Le réarmement demande un retour franchement à plat : sous le seul
            // seuil, le bruit ferait osciller le signal d'un échantillon à l'autre.
            if (inclinaisonDeg < seuilAngleDeg - hysteresisAngleDeg) vuAPlat = true
            incline = false
            return
        }
        if (!vuAPlat) return
        if (!incline) {
            incline = true
            inclineDepuisMs = nowMillis
        } else if (nowMillis - inclineDepuisMs >= dureeInclinaisonMs) {
            estPrisEnMain = true
        }
    }

    private fun majEnergie(dtMs: Float) {
        val norme = sqrt(lisseX * lisseX + lisseY * lisseY + lisseZ * lisseZ)
        val ecart = abs(norme - GRAVITE)
        // Fenêtre glissante d'environ [fenetreEnergieMs], sans file d'attente :
        // ce qui a plus d'une fenêtre s'efface tout seul.
        energie *= exp(-dtMs / fenetreEnergieMs)
        if (ecart > plancherEnergie) {
            energie += (ecart - plancherEnergie) * (dtMs / 1_000f)
        }
        if (energie >= budgetEnergie) estPrisEnMain = true
    }

    companion object {
        const val GRAVITE = 9.81f

        /**
         * Un téléphone regardé dans la main dépasse largement 30° ; un téléphone
         * posé sur une table à peu près plane reste sous 10°. 27° laisse de la
         * marge des deux côtés.
         */
        const val SEUIL_ANGLE_DEG = 27f

        /** Le signal ne se réarme qu'à plat franc, pour ne pas osciller. */
        const val HYSTERESIS_ANGLE_DEG = 5f

        /** Assez long pour ignorer un basculement fortuit, assez court pour ne pas faire attendre. */
        const val DUREE_INCLINAISON_MS = 400L

        /** Bruit du capteur et vibrations résiduelles : en dessous, rien ne compte. */
        const val PLANCHER_ENERGIE = 0.30f

        /**
         * 0,25 m/s. Simulé sur des trajectoires synthétiques : une prise en main
         * même molle (1,5 m/s² pendant 600 ms) culmine vers 0,26–0,31 ; un
         * téléphone posé, même avec un haut-parleur qui le fait vibrer à 2 m/s²,
         * reste sous 0,13. Le pire faux positif garde donc un facteur deux.
         */
        const val BUDGET_ENERGIE = 0.25f

        const val FENETRE_ENERGIE_MS = 1_000f

        /** Lent : l'orientation doit rester stable pendant le geste. */
        const val TAU_GRAVITE_MS = 350f

        /** Rapide : la salve d'une prise en main dure quelques centaines de ms. */
        const val TAU_ENERGIE_MS = 100f

        private const val DT_MAX_MS = 200L
        private const val NORME_MINIMALE = 0.001f
        private const val DEGRES_PAR_RADIAN = 57.295776f

        /**
         * Angle entre l'axe Z de l'appareil et la verticale, en degrés : 0° à plat.
         * La valeur absolue met « face contre table » et « face en l'air » au même
         * rang. Renvoie null si le vecteur est trop court pour porter une direction.
         */
        fun inclinaisonDegres(x: Float, y: Float, z: Float): Float? {
            val norme = sqrt(x * x + y * y + z * z)
            if (norme < NORME_MINIMALE) return null
            val cosinus = (abs(z) / norme).coerceIn(0f, 1f)
            return acos(cosinus) * DEGRES_PAR_RADIAN
        }
    }
}

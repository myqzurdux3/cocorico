package com.cocorico.ring

import kotlin.math.exp

/**
 * Décide, à chaque échantillon, si le téléphone est **actuellement** en train
 * de bouger. Contrairement à [PriseEnMainDetector], la décision n'est pas
 * verrouillée : [enMouvement] redevient faux dès que le mouvement cesse. Ce
 * n'est pas un événement à reconnaître une fois, c'est un état courant — il
 * sert à réarmer [InactivityTracker] tant que le téléphone est manipulé, même
 * si personne n'a encore touché au défi.
 *
 * Réutilise [EstimateurGravite] plutôt que de refaire son lissage : son canal
 * rapide (tau = [EstimateurGravite.TAU_ECART_MS] = 100 ms) est le même calcul
 * que documente déjà [PriseEnMainDetector] pour son propre canal d'énergie —
 * lisser avant de redresser annule les oscillations de moyenne nulle des
 * vibrations du haut-parleur (> 10 Hz) et laisse passer le mouvement de corps
 * rigide d'une main. Le projet a déjà tranché sur ce point : un seul calcul
 * d'inclinaison, deux canaux de filtrage (lent pour l'orientation, rapide pour
 * l'écart) — jamais un troisième réglage du même signal. En composant
 * [EstimateurGravite] au lieu d'écrire un troisième lissage, cette classe suit
 * cette règle plutôt que de la contourner.
 *
 * Comme [PriseEnMainDetector.energie], l'exigence de mouvement « franc et
 * soutenu » est un intégrateur à fuite sur l'excès au-dessus d'un plancher, pas
 * un simple dépassement instantané : une secousse isolée, même au-dessus du
 * plancher, n'a pas le temps d'accumuler le budget avant que la fuite ne
 * l'efface. La différence avec [PriseEnMainDetector] est que rien n'est
 * verrouillé : [enMouvement] est recalculé à chaque échantillon, donc il
 * retombe de lui-même dès que l'énergie accumulée repasse sous le budget.
 */
class MouvementDetector(
    private val estimateur: EstimateurGravite = EstimateurGravite(),
    private val plancherEcart: Float = PLANCHER_ECART,
    private val budgetEnergie: Float = BUDGET_ENERGIE,
    private val fenetreEnergieMs: Float = FENETRE_ENERGIE_MS,
) {

    private var energie = 0f
    private var dernierMs = 0L
    private var amorce = false

    /** Vrai tant que du mouvement franc et soutenu est mesuré. Pas verrouillé. */
    var enMouvement: Boolean = false
        private set

    /**
     * Consomme un échantillon de l'accéléromètre (m/s², repère appareil) et
     * renvoie [enMouvement] à jour.
     */
    fun onEchantillon(x: Float, y: Float, z: Float, nowMillis: Long): Boolean {
        estimateur.onEchantillon(x, y, z, nowMillis)

        // Le premier échantillon amorce seulement l'horloge : [EstimateurGravite]
        // gère déjà l'amorçage de ses propres filtres, rien à dupliquer ici.
        if (!amorce) {
            amorce = true
            dernierMs = nowMillis
            return enMouvement
        }

        // Borné, comme partout ailleurs dans ce module : une salve en retard ou
        // une pause du capteur ne doit pas verser d'un coup toute une fenêtre
        // d'énergie.
        val dtMs = (nowMillis - dernierMs).coerceIn(0L, DT_MAX_MS).toFloat()
        dernierMs = nowMillis

        // Fenêtre glissante sans file d'attente : ce qui a plus d'une fenêtre
        // s'efface tout seul, exactement comme l'énergie de PriseEnMainDetector.
        energie *= exp(-dtMs / fenetreEnergieMs)
        val exces = estimateur.ecartGravite - plancherEcart
        if (exces > 0f) energie += exces * (dtMs / 1_000f)

        // Recalculé, jamais figé à `true` : c'est ce qui distingue cette classe
        // de PriseEnMainDetector et lui permet de retomber.
        enMouvement = energie >= budgetEnergie
        return enMouvement
    }

    companion object {

        /**
         * 1,5 m/s², la même valeur que
         * [com.cocorico.challenge.pompes.CompteurPompes.ECART_MAX], déjà
         * retenue ailleurs dans le projet pour dire « au-delà, le téléphone
         * bouge » sur ce même canal (écart de gravité, canal rapide à 100 ms).
         * Lui redonner un réglage différent ici aurait été arbitraire.
         *
         * Elle reste très au-dessus de ce qui a été mesuré : au repos, sur un
         * Pixel 9a posé au sol sans alarme, `ecartGravite` du canal rapide
         * oscille entre 0,00 et 0,04 (traces `CocoricoPompes`) — marge de plus
         * de 35×. La vibration de haut-parleur simulée dans
         * [EstimateurGraviteTest] (30 Hz, 2 m/s²) culmine à un écart d'environ
         * 0,20 sur ce même canal — marge de 7×. **Non mesuré : l'écart réel
         * pendant que la sonnerie hurle à plein volume sur une surface dure**,
         * le cas qui compte le plus ; voir le rapport de tâche.
         */
        const val PLANCHER_ECART = 1.5f

        /**
         * 0,08 m/s. Choisi entre deux valeurs simulées avec les constantes
         * ci-dessus (fenêtre à 500 ms, plancher à 1,5), en rejouant les
         * scénarios de [MouvementDetectorTest] :
         * - une prise en main soulevée puis freinée (accélération de 3 m/s²
         *   tenue 240 ms dans un sens, puis 240 ms dans l'autre — le scénario
         *   `salve de mouvement` de PriseEnMainDetectorTest) culmine à une
         *   énergie de 0,132 ;
         * - une secousse isolée de 100 ms (4 m/s², un seul sens) culmine à
         *   0,042, presque trois fois moins.
         *
         * 0,08 se place entre les deux, plus proche de la secousse isolée :
         * marge ×1,9 au-dessus d'elle, marge ×1,65 sous la prise en main. Le
         * choix privilégie délibérément le faux négatif (mouvement réel
         * manqué, rattrapé par une interaction avec le défi ou par le prochain
         * geste) au faux positif (vibration prise pour un mouvement, qui
         * bloquerait le volume bas indéfiniment) — c'est l'échec le pire pour
         * ce produit. Le plancher [PLANCHER_ECART] fait tout le travail de
         * sécurité contre les vibrations : leur écart ne le franchit jamais,
         * donc leur énergie reste à zéro quel que soit ce budget.
         *
         * Non calibré sur un geste réel de prise en main molle : une
         * accélération de 1,5 m/s² (pile au plancher) tenue 600 ms, une fois
         * filtrée, ne dépasse jamais tout à fait le plancher et produit une
         * énergie nulle. Un mouvement plus doux que la simulation ci-dessus
         * peut donc ne pas déclencher — voir le rapport de tâche.
         */
        const val BUDGET_ENERGIE = 0.08f

        /**
         * 500 ms. Assez court pour que [enMouvement] retombe vite une fois le
         * téléphone reposé — l'écart repasse sous le plancher, l'énergie déjà
         * accumulée s'efface en moins d'une seconde. Assez long pour ne pas
         * effacer l'énergie d'un geste continu entre deux pics d'un mouvement
         * périodique (une main qui tient et bouge le téléphone n'accélère pas
         * en continu).
         */
        const val FENETRE_ENERGIE_MS = 500f

        private const val DT_MAX_MS = 200L
    }
}

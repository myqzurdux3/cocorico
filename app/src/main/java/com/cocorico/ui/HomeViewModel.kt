package com.cocorico.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cocorico.alarm.AlarmScheduler
import com.cocorico.challenge.photo.Piece
import com.cocorico.challenge.photo.SelectionObjets
import com.cocorico.data.AlarmConfig
import com.cocorico.data.AlarmConfigRepository
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty
import com.cocorico.ring.NiveauxVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AlarmConfigRepository(app)
    private val scheduler = AlarmScheduler(app)

    val config: StateFlow<AlarmConfig> = repo.config.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlarmConfig.DEFAULT,
    )

    private val _prochaine = MutableStateFlow<LocalDateTime?>(null)
    val prochaine: StateFlow<LocalDateTime?> = _prochaine.asStateFlow()

    init {
        // Sans ça, l'accueil afficherait « Aucun jour actif » au lancement, même
        // alarme armée : `prochaine` ne serait renseignée qu'au premier réglage.
        viewModelScope.launch {
            // Chiffre une clé d'API héritée d'une version qui l'écrivait en
            // clair. Ne fait rien si elle l'est déjà, ou s'il n'y en a pas.
            runCatching { repo.migrerCleApi() }
            _prochaine.value = planifier()
        }
    }

    /**
     * Recalcule l'occurrence affichée. L'accueil l'appelle quand celle qu'il
     * montre est dépassée : sans ça, le libellé restait figé sur une heure
     * révolue et le délai devenait négatif.
     */
    fun rafraichirProchaine() {
        viewModelScope.launch {
            _prochaine.value = planifier()
        }
    }

    fun majHeure(heure: Int, minute: Int) = modifier { it.copy(hour = heure, minute = minute) }

    fun basculerJour(jour: DayOfWeek) = modifier { courant ->
        val jours = courant.days.toMutableSet()
        if (!jours.remove(jour)) jours.add(jour)
        courant.copy(days = jours)
    }

    fun majSonnerie(id: String) = modifier { it.copy(ringtoneId = id) }

    fun majDifficulte(difficulte: Difficulty) = modifier { it.copy(difficulty = difficulte) }

    fun majDefi(defi: ChallengeId) = modifier { it.copy(challengeId = defi) }

    fun armer(arme: Boolean) = modifier { it.copy(armed = arme) }

    /**
     * Le plancher est réappliqué à l'écriture, en plus du curseur : la valeur
     * finit sur le disque, et rien ne doit pouvoir y déposer un plafond qui
     * rendrait l'alarme inaudible.
     */
    fun majVolumeMax(pourcent: Int) = modifier { it.copy(volumeMaxPourcent = NiveauxVolume.normaliser(pourcent)) }

    fun majCleApi(cle: String) = modifier { it.copy(cleApi = cle) }

    /** Coche ou décoche un objet du catalogue à l'écran de sélection des pièces. */
    fun basculerObjet(id: String) = modifier {
        it.copy(objetsSelectionnes = SelectionObjets.basculerObjet(it.objetsSelectionnes, id))
    }

    /** Coche ou décoche une pièce entière d'un geste. Voir [SelectionObjets.basculerPiece]. */
    fun basculerPiece(piece: Piece) = modifier {
        it.copy(objetsSelectionnes = SelectionObjets.basculerPiece(it.objetsSelectionnes, piece))
    }

    private fun modifier(transform: (AlarmConfig) -> AlarmConfig) {
        viewModelScope.launch {
            repo.update(transform)
            _prochaine.value = planifier()
        }
    }

    /**
     * Une autorisation d'alarme exacte révoquée en cours de route fait lever une
     * SecurityException : elle ferait planter l'accueil au lancement. On retombe
     * sur « aucune occurrence », et l'onboarding reprend la main puisqu'il teste
     * la même autorisation.
     */
    private suspend fun planifier(): LocalDateTime? =
        runCatching { scheduler.schedule(repo.current()).prochaine }.getOrNull()
}

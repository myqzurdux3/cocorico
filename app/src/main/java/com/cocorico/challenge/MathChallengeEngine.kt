package com.cocorico.challenge

import com.cocorico.data.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Progression et validation du défi maths, sans Compose ni Android : tout le
 * comportement du défi est vérifiable en test unitaire.
 */
class MathChallengeEngine(
    private val generator: MathProblemGenerator,
    private val difficulty: Difficulty,
    private val total: Int = 3,
) {
    init {
        // Les calculs sont le repli de tous les autres défis : ce sont eux qui
        // garantissent qu'une alarme peut toujours être arrêtée, donc ils ne
        // doivent jamais pouvoir se casser. À total = 0, le moteur rendait
        // ChallengeProgress(0, 0) et l'écran en tirait 0 / 0, c'est-à-dire un NaN
        // poussé dans la barre de progression. Mieux vaut refuser bruyamment à la
        // construction qu'afficher une barre indéfinie devant un dormeur.
        require(total >= 1) { "Le défi de maths exige au moins un calcul, reçu : $total" }
    }

    private val _current = MutableStateFlow(generator.generate(difficulty))
    val current: StateFlow<MathProblem> = _current.asStateFlow()

    private val _progress = MutableStateFlow(ChallengeProgress(done = 0, total = total))
    val progress: StateFlow<ChallengeProgress> = _progress.asStateFlow()

    private val _isSolved = MutableStateFlow(false)
    val isSolved: StateFlow<Boolean> = _isSolved.asStateFlow()

    private val _erreurs = MutableStateFlow(0)
    val erreurs: StateFlow<Int> = _erreurs.asStateFlow()

    /**
     * Renvoie true si la réponse était juste — et **false aussi** lorsque le défi
     * est déjà résolu, cas où la soumission est simplement ignorée. Ces deux false
     * ne veulent pas dire la même chose : voir [estUneFaute], que l'affichage doit
     * utiliser plutôt que de nier ce booléen.
     *
     * Une erreur ne pénalise pas le volume : seule l'inactivité le fait remonter,
     * sinon l'application punirait l'effort.
     */
    fun submit(answer: Int): Boolean {
        if (_isSolved.value) return false

        val juste = answer == _current.value.answer
        if (juste) {
            val done = _progress.value.done + 1
            _progress.value = ChallengeProgress(done = done, total = total)
            if (done >= total) {
                _isSolved.value = true
                return true
            }
        } else {
            _erreurs.value += 1
        }
        _current.value = generator.generate(difficulty)
        return juste
    }

    companion object {
        /**
         * Faut-il reprocher une faute à l'utilisateur ? Non si le défi était déjà
         * résolu : le pavé reste à l'écran le temps de la bascule vers l'écran de
         * victoire, et un dernier appui sur ✓ y est ignoré par [submit], qui rend
         * alors false. L'écran niait ce false et affichait « Non. Et le coq a
         * entendu. » sur une réponse pourtant juste.
         *
         * Extrait ici, hors du composable, pour être testable : c'est la seule
         * décision de l'affichage qui puisse être fausse.
         */
        fun estUneFaute(dejaResolu: Boolean, reponseJuste: Boolean): Boolean = !dejaResolu && !reponseJuste
    }
}

package com.cocorico.challenge.combine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cocorico.challenge.Challenge
import com.cocorico.data.ChallengeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enchaîne plusieurs épreuves : le défi sur mesure.
 *
 * Il implémente la même interface [Challenge] que les épreuves qu'il contient,
 * si bien que ni `alarm/` ni `ring/` ne savent qu'il existe — le service ne
 * connaît toujours que [isSolved]. C'est ce qui permet d'ajouter ce mode sans
 * toucher à la chaîne qui fait sonner.
 *
 * Les épreuves sont construites **une à une, quand leur tour vient**, jamais
 * d'avance. Ce n'est pas une optimisation : `PhotoChallenge` tire son objet à
 * la construction et l'exclut du tirage du lendemain, donc tout construire au
 * départ exclurait des objets que l'utilisateur n'a jamais vus.
 *
 * @param fabriquer construit l'épreuve demandée, ou rend `null` si elle est
 *   impossible sur cet appareil ce matin — pas de caméra, pas de clé, pas de
 *   capteur. Le rappel qu'elle reçoit est le renoncement de cette épreuve-là.
 */
class DefiCombine(
    etapes: List<EtapeCombine>,
    private val fabriquer: (EtapeCombine, onRenoncer: () -> Unit) -> Challenge?,
) : Challenge {

    override val id = ChallengeId.COMBINE

    private val _etat = MutableStateFlow(EtatSequence(EtapesCombine.assainir(etapes)))

    private val _isSolved = MutableStateFlow(false)
    override val isSolved: StateFlow<Boolean> = _isSolved.asStateFlow()

    /** Rang de l'épreuve en cours et nombre total, pour l'en-tête. */
    val avancement: StateFlow<EtatSequence> = _etat.asStateFlow()

    private fun avancer() {
        val suivant = _etat.value.suivante()
        _etat.value = suivant
        // L'ordre compte : le drapeau ne passe à vrai qu'une fois l'état
        // avancé, sinon l'écran afficherait encore la dernière épreuve alors
        // que le service a déjà commencé à s'arrêter.
        if (suivant.estTerminee) _isSolved.value = true
    }

    private fun remplacerParCalculs() {
        _etat.value = _etat.value.remplacerParCalculs()
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val etat by _etat.collectAsState()
        if (etat.estTerminee) return

        val courante = etat.courante
        // Clé sur le rang **et** sur l'épreuve : un remplacement par les calculs
        // ne change pas le rang, et sans la seconde clé l'ancienne épreuve
        // resterait affichée.
        val epreuve = remember(etat.index, courante) {
            fabriquer(courante) { remplacerParCalculs() }
        }

        if (epreuve == null) {
            // Impossible ce matin : même réponse que le renoncement, décidée
            // dans `EtatSequence`. Passée par un effet et non appelée pendant la
            // composition, qui n'a pas le droit d'avoir des effets de bord.
            LaunchedEffect(etat.index) { remplacerParCalculs() }
            return
        }

        val resolue by epreuve.isSolved.collectAsState()
        LaunchedEffect(resolue, etat.index) { if (resolue) avancer() }

        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Épreuve ${etat.numero} sur ${etat.total} — ${libelle(courante)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // `key` sur l'épreuve : sa sortie de composition libère ses capteurs
            // ou sa caméra. Sans ça, les pompes continueraient d'écouter
            // l'accéléromètre pendant la photo qui suit.
            key(epreuve) {
                epreuve.Content(Modifier.fillMaxWidth())
            }
        }
    }

    private fun libelle(etape: EtapeCombine): String = when (etape.type) {
        ChallengeId.MATHS -> "${etape.nombre} calcul${pluriel(etape.nombre)}"
        ChallengeId.POMPES -> "${etape.nombre} pompe${pluriel(etape.nombre)}"
        ChallengeId.PHOTO -> "${etape.nombre} photo${pluriel(etape.nombre)}"
        // Une épreuve « sur mesure » à l'intérieur d'une épreuve sur mesure
        // n'existe pas : `EtapesCombine.TYPES` ne la propose pas, et l'écran de
        // réglage ne peut pas la produire.
        ChallengeId.COMBINE -> "épreuve"
    }

    private fun pluriel(nombre: Int): String = if (nombre > 1) "s" else ""
}
